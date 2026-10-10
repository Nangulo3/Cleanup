package com.example.cleanup.deletion

import android.app.Activity
import android.content.IntentSender
import androidx.core.net.toUri
import androidx.room.withTransaction
import com.example.cleanup.data.db.AppDatabase
import com.example.cleanup.data.db.entity.DeletionBatchEntity
import com.example.cleanup.data.db.entity.DeletionBatchItemEntity
import com.example.cleanup.data.db.entity.PendingDeletionEntity
import com.example.cleanup.data.media.MediaAccessRepository
import com.example.cleanup.data.media.MediaStoreSource
import com.example.cleanup.domain.model.MediaAccess
import com.example.cleanup.domain.model.MediaType
import com.example.cleanup.settings.DeletionMode
import com.example.cleanup.settings.SettingsRepository
import kotlinx.coroutines.flow.first

/**
 * Orquesta la eliminación final:
 *  1. [prepare]: comprueba en MediaStore que los elementos siguen existiendo, guarda la solicitud
 *     en la base de datos y crea el diálogo de confirmación de Android.
 *  2. La interfaz muestra ese diálogo.
 *  3. [onSystemResult]: vuelve a consultar MediaStore y retira de la papelera solo lo que de
 *     verdad desapareció.
 *
 * La app nunca borra archivos directamente: lo hace Android tras la confirmación del usuario.
 */
class DeletionCoordinator(
    private val db: AppDatabase,
    private val source: MediaStoreSource,
    private val trash: TrashRepository,
    private val settings: SettingsRepository,
    private val access: MediaAccessRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    sealed interface PrepareResult {
        /** Listo para mostrar el diálogo de Android. */
        data class Ready(val intentSender: IntentSender, val count: Int, val mode: DeletionMode) : PrepareResult

        /** No quedó nada que pedir a Android (p. ej. todo había desaparecido ya). */
        data class NothingToDelete(val report: DeletionReport) : PrepareResult
    }

    /**
     * Prepara la eliminación de [selectedIds] (o de toda la papelera si es null).
     * Se envían como máximo [MAX_PER_REQUEST] elementos por solicitud.
     */
    suspend fun prepare(selectedIds: Set<Long>?): PrepareResult {
        // Una solicitud anterior sin respuesta (p. ej. la app se cerró) se verifica y se cierra.
        finishAwaiting(resultCode = null)

        val mode = settings.settings.first().deletionMode
        val all = db.trashDao().allPendingOldestFirst()
        val targets = if (selectedIds == null) all else all.filter { it.mediaId in selectedIds }

        val presentIds = presentIdsOf(targets.map { it.mediaId to it.mediaType })
        val (present, missing) = targets.partition { it.mediaId in presentIds }

        // Con acceso completo, "no visible" significa que ya no existe: se quita de la cola.
        // Con acceso parcial puede ser solo que no está autorizado: se conserva.
        val fullAccess = access.currentAccess() == MediaAccess.FULL
        val skippedMissing = if (fullAccess) missing.size else 0
        val skippedInaccessible = if (fullAccess) 0 else missing.size
        if (fullAccess) trash.forgetDeleted(missing.map { it.mediaId })

        if (present.isEmpty()) {
            return PrepareResult.NothingToDelete(
                DeletionReport(
                    mode = mode, deleted = 0, deletedKnownBytes = 0, deletedUnknownSizeCount = 0,
                    stillPresent = 0, systemAccepted = null, skippedMissing = skippedMissing,
                    skippedInaccessible = skippedInaccessible, remainingAfterLimit = 0,
                )
            )
        }

        val (request, sent) = createRequest(present, mode)
        val remaining = present.size - sent.size
        db.withTransaction {
            val batchId = db.deletionDao().insertBatch(
                DeletionBatchEntity(
                    createdAt = clock(),
                    mode = mode,
                    requestedCount = sent.size,
                    requestedKnownBytes = sent.sumOf { it.sizeBytes ?: 0L },
                    skippedMissing = skippedMissing,
                    skippedInaccessible = skippedInaccessible,
                    remainingAfterLimit = remaining,
                )
            )
            db.deletionDao().insertBatchItems(
                sent.map { DeletionBatchItemEntity(batchId, it.mediaId, it.mediaType, it.sizeBytes) }
            )
        }
        return PrepareResult.Ready(request, sent.size, mode)
    }

    /** Llamar con el código que devolvió el diálogo de Android. */
    suspend fun onSystemResult(resultCode: Int): DeletionReport? {
        val batch = db.deletionDao().awaitingBatches().firstOrNull() ?: return null
        return finish(batch, resultCode)
    }

    /** Verifica y cierra las solicitudes que quedaron sin respuesta. */
    suspend fun finishAwaiting(resultCode: Int?) {
        db.deletionDao().awaitingBatches().forEach { finish(it, resultCode) }
    }

    /**
     * Quita de la papelera los elementos que ya no existen (eliminados desde otra app).
     * Solo con acceso completo, para no confundir "sin permiso" con "eliminado".
     * Devuelve cuántos se quitaron.
     */
    suspend fun pruneMissingPending(): Int {
        if (access.currentAccess() != MediaAccess.FULL) return 0
        val all = db.trashDao().allPendingOldestFirst()
        if (all.isEmpty()) return 0
        val presentIds = presentIdsOf(all.map { it.mediaId to it.mediaType })
        val missing = all.filterNot { it.mediaId in presentIds }.map { it.mediaId }
        trash.forgetDeleted(missing)
        return missing.size
    }

    private suspend fun finish(batch: DeletionBatchEntity, resultCode: Int?): DeletionReport {
        val dao = db.deletionDao()
        val items = dao.batchItems(batch.id)
        val presentIds = presentIdsOf(items.map { it.mediaId to it.mediaType })
        val outcome = DeletionReconciler.reconcile(items.map { BatchItem(it.mediaId, it.sizeBytes) }, presentIds)
        trash.forgetDeleted(outcome.confirmedDeleted)

        val completed = batch.copy(
            resultCode = resultCode,
            deletedCount = outcome.confirmedDeleted.size,
            deletedKnownBytes = outcome.deletedKnownBytes,
            deletedUnknownSizeCount = outcome.deletedUnknownSizeCount,
            stillPresentCount = outcome.stillPresent.size,
            completedAt = clock(),
        )
        dao.updateBatch(completed)
        dao.pruneBatches(KEEP_BATCHES)
        dao.deleteOrphanBatchItems()

        return DeletionReport(
            mode = batch.mode,
            deleted = completed.deletedCount,
            deletedKnownBytes = completed.deletedKnownBytes,
            deletedUnknownSizeCount = completed.deletedUnknownSizeCount,
            stillPresent = completed.stillPresentCount,
            systemAccepted = resultCode?.let { it == Activity.RESULT_OK },
            skippedMissing = batch.skippedMissing,
            skippedInaccessible = batch.skippedInaccessible,
            remainingAfterLimit = batch.remainingAfterLimit,
        )
    }

    /**
     * Crea la solicitud con hasta [MAX_PER_REQUEST] elementos. Si Android la rechaza por tamaño
     * (demasiadas URIs para una sola transacción), reintenta con la mitad.
     */
    private fun createRequest(
        items: List<PendingDeletionEntity>,
        mode: DeletionMode,
    ): Pair<IntentSender, List<PendingDeletionEntity>> {
        var limit = minOf(items.size, MAX_PER_REQUEST)
        while (true) {
            val chunk = items.take(limit)
            try {
                val pendingIntent = source.createDeletionRequest(
                    uris = chunk.map { it.contentUri.toUri() },
                    moveToTrash = mode == DeletionMode.SYSTEM_TRASH,
                )
                return pendingIntent.intentSender to chunk
            } catch (e: RuntimeException) {
                if (limit <= MIN_PER_REQUEST) throw e
                limit /= 2
            }
        }
    }

    private suspend fun presentIdsOf(items: List<Pair<Long, MediaType>>): Set<Long> {
        val byType = items.groupBy({ it.second }, { it.first })
        val result = HashSet<Long>()
        for ((type, ids) in byType) result += source.existingIds(type, ids)
        return result
    }

    companion object {
        const val MAX_PER_REQUEST = 1000
        private const val MIN_PER_REQUEST = 50
        private const val KEEP_BATCHES = 20
    }
}
