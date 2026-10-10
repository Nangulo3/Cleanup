package com.example.cleanup.deletion

import androidx.room.withTransaction
import com.example.cleanup.data.db.AppDatabase
import com.example.cleanup.data.db.dao.TrashSummary
import com.example.cleanup.data.db.entity.ActionItemEntity
import com.example.cleanup.data.db.entity.ActionLogEntity
import com.example.cleanup.data.db.entity.ActionType
import com.example.cleanup.data.db.entity.PendingDeletionEntity
import com.example.cleanup.data.db.entity.TrashChange
import com.example.cleanup.domain.model.DeletionSource
import com.example.cleanup.domain.model.MediaType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Lo necesario para poner un elemento en la papelera (viene del catálogo o de una cuadrícula). */
data class MarkCandidate(
    val mediaId: Long,
    val contentUri: String,
    val mediaType: MediaType,
    val displayName: String?,
    val sizeBytes: Long?,
    val sortDateMs: Long,
)

/** Resultado de deshacer: qué tipo de acción se revirtió y cuántos elementos afectó. */
data class UndoResult(val type: ActionType, val itemCount: Int)

/**
 * La Papelera de revisión: una lista de marcas, no copias de archivos. Todas las pantallas
 * marcan y desmarcan a través de esta clase, así que la selección es única por elemento.
 */
class TrashRepository(
    private val db: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.trashDao()

    val pending: Flow<List<PendingDeletionEntity>> = dao.observePending()
    val pendingIds: Flow<Set<Long>> = dao.observePendingIds().map { it.toHashSet() }
    val summary: Flow<TrashSummary> = dao.observeSummary()
    val canUndo: Flow<Boolean> = dao.observeActionCount().map { it > 0 }

    fun observeIsPending(mediaId: Long): Flow<Boolean> = dao.observeIsPending(mediaId)

    /** Añade a la papelera. Los que ya estaban se ignoran. Devuelve cuántos se añadieron. */
    suspend fun mark(candidates: List<MarkCandidate>, source: DeletionSource): Int =
        db.withTransaction {
            val unique = candidates.distinctBy { it.mediaId }
            val already = unique.map { it.mediaId }.chunked(CHUNK)
                .flatMap { dao.existingPendingIds(it) }
                .toHashSet()
            val now = clock()
            val toInsert = unique
                .filterNot { it.mediaId in already }
                .map { it.toPending(now, source) }
            if (toInsert.isNotEmpty()) {
                dao.insertPending(toInsert)
                recordAction(ActionType.MARK, toInsert, now)
            }
            toInsert.size
        }

    /** Quita de la papelera (el archivo no se toca). Devuelve cuántos se quitaron. */
    suspend fun unmark(ids: Collection<Long>): Int =
        db.withTransaction {
            val rows = ids.distinct().chunked(CHUNK).flatMap { dao.pendingByIds(it) }
            if (rows.isNotEmpty()) {
                rows.map { it.mediaId }.chunked(CHUNK).forEach { dao.deletePending(it) }
                recordAction(ActionType.UNMARK, rows, clock())
            }
            rows.size
        }

    /** Revierte la última acción de marcar o desmarcar. null si no hay nada que deshacer. */
    suspend fun undoLast(): UndoResult? =
        db.withTransaction {
            val action = dao.latestAction() ?: return@withTransaction null
            val items = dao.actionItems(action.id)
            when (action.type) {
                ActionType.MARK ->
                    items.map { it.mediaId }.chunked(CHUNK).forEach { dao.deletePending(it) }
                ActionType.UNMARK ->
                    dao.insertPending(items.map { it.toPending() })
                ActionType.REVIEW -> {
                    // Borrar la decisión devuelve el elemento a la cola de Deslizar en su posición.
                    items.map { it.mediaId }.chunked(CHUNK).forEach { db.reviewDao().deleteDecisions(it) }
                    val added = items.filter { it.trashChange == TrashChange.ADDED }.map { it.mediaId }
                    added.chunked(CHUNK).forEach { dao.deletePending(it) }
                    val removed = items.filter { it.trashChange == TrashChange.REMOVED }
                    if (removed.isNotEmpty()) dao.insertPending(removed.map { it.toPending() })
                }
            }
            dao.deleteActionItems(action.id)
            dao.deleteAction(action.id)
            UndoResult(action.type, items.size)
        }

    /**
     * Olvida elementos que Android confirmó como eliminados (o que ya no existen): los quita de
     * la papelera, del historial de deshacer (para no "resucitarlos") y del catálogo.
     */
    suspend fun forgetDeleted(ids: List<Long>) {
        if (ids.isEmpty()) return
        db.withTransaction {
            ids.chunked(CHUNK).forEach { chunk ->
                dao.deletePending(chunk)
                dao.deleteActionItemsFor(chunk)
                db.reviewDao().deleteDecisions(chunk)
                db.mediaDao().deleteByIds(chunk)
            }
            dao.deleteEmptyActions()
        }
    }

    private suspend fun recordAction(type: ActionType, rows: List<PendingDeletionEntity>, now: Long) {
        val actionId = dao.insertAction(ActionLogEntity(type = type, createdAt = now, itemCount = rows.size))
        dao.insertActionItems(rows.map { it.toActionItem(actionId) })
        dao.pruneActions(MAX_UNDO_ACTIONS)
        dao.deleteOrphanActionItems()
    }

    private fun MarkCandidate.toPending(now: Long, source: DeletionSource) = PendingDeletionEntity(
        mediaId = mediaId,
        contentUri = contentUri,
        mediaType = mediaType,
        displayName = displayName,
        sizeBytes = sizeBytes,
        sortDateMs = sortDateMs,
        markedAt = now,
        source = source,
    )

    private fun PendingDeletionEntity.toActionItem(actionId: Long) = ActionItemEntity(
        actionId = actionId,
        mediaId = mediaId,
        contentUri = contentUri,
        mediaType = mediaType,
        displayName = displayName,
        sizeBytes = sizeBytes,
        sortDateMs = sortDateMs,
        markedAt = markedAt,
        source = source,
    )

    private fun ActionItemEntity.toPending() = PendingDeletionEntity(
        mediaId = mediaId,
        contentUri = contentUri,
        mediaType = mediaType,
        displayName = displayName,
        sizeBytes = sizeBytes,
        sortDateMs = sortDateMs,
        markedAt = markedAt,
        source = source,
    )

    private companion object {
        const val CHUNK = 500
        const val MAX_UNDO_ACTIONS = 50
    }
}
