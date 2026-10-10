package com.example.cleanup.worker

import androidx.room.withTransaction
import com.example.cleanup.data.db.AppDatabase
import com.example.cleanup.data.db.entity.ScanStateEntity
import com.example.cleanup.data.db.entity.VolumeStateEntity
import com.example.cleanup.data.media.MediaRowMapper
import com.example.cleanup.data.media.MediaStoreSource
import com.example.cleanup.data.media.RawMediaRow
import com.example.cleanup.domain.model.MediaAccess
import com.example.cleanup.domain.model.MediaClassifier
import com.example.cleanup.domain.model.ScreenInfo
import com.example.cleanup.domain.scan.ScanPlanner
import com.example.cleanup.domain.scan.StoredVolume
import java.time.ZoneId

/** Resultado de un escaneo de catálogo. */
data class CatalogResult(val rowsRead: Int, val removed: Int, val totalItems: Int, val wasFull: Boolean)

/**
 * Sincroniza la tabla media_items con MediaStore:
 *  1. Por volumen, lee todo o solo lo cambiado desde la última generación ([ScanPlanner]).
 *  2. Concilia por IDs: trae los visibles que faltan (p. ej. al ampliar el acceso parcial) y borra
 *     de la base de datos los que ya no son visibles (eliminados en otra app o sin permiso).
 *  3. Guarda la generación leída AL INICIO, para que lo que cambie durante el escaneo
 *     se recoja en el siguiente.
 *
 * Nunca borra archivos: solo actualiza la base de datos local.
 */
class CatalogScanner(
    private val source: MediaStoreSource,
    private val db: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val screen: () -> ScreenInfo? = { null },
) {

    suspend fun run(
        forceFull: Boolean,
        access: MediaAccess,
        onProgress: suspend (ScanProgressInfo) -> Unit,
    ): CatalogResult {
        val mediaDao = db.mediaDao()
        val scanDao = db.scanDao()
        val startedAt = clock()
        val previous = scanDao.scanState()
        scanDao.upsertScanState((previous ?: ScanStateEntity()).copy(lastStartedAt = startedAt))

        // Si cambió el nivel de acceso, se relee todo: obtener permiso sobre un archivo
        // no cambia su "generación", así que el modo incremental no lo detectaría.
        val accessChanged = previous?.lastAccess != access
        // Si cambiaron las reglas de clasificación (capturas, chat) hay que reclasificar todo.
        val classifierChanged = previous?.classifierVersion != MediaClassifier.VERSION
        val snapshots = source.volumeSnapshots()
        val stored = scanDao.volumes().associate {
            it.volumeName to StoredVolume(it.volumeName, it.mediaStoreVersion, it.lastGeneration)
        }
        val plans = ScanPlanner.plan(forceFull || accessChanged || classifierChanged, snapshots, stored)

        var rowsRead = 0
        val screenInfo = screen()
        suspend fun save(batch: List<RawMediaRow>) {
            val zoneId = zone()
            mediaDao.upsertAll(batch.map { MediaRowMapper.toEntity(it, startedAt, zoneId, screenInfo) })
            rowsRead += batch.size
            onProgress(ScanProgressInfo(ScanPhase.CATALOG, rowsRead, null))
        }

        onProgress(ScanProgressInfo(ScanPhase.CATALOG, 0, null))
        for (plan in plans) {
            for (collection in MediaStoreSource.Collection.entries) {
                source.readRows(plan.volume.name, collection, plan.sinceGeneration) { save(it) }
            }
        }

        // Conciliación por IDs.
        onProgress(ScanProgressInfo(ScanPhase.RECONCILE, rowsRead, null))
        val known = mediaDao.allIds().toHashSet()
        val visible = HashSet<Long>()
        for (snapshot in snapshots) {
            for (collection in MediaStoreSource.Collection.entries) {
                val ids = source.readIds(snapshot.name, collection)
                visible += ids
                val missing = ids.filterNot { it in known }
                if (missing.isNotEmpty()) {
                    source.readRowsByIds(snapshot.name, collection, missing) { save(it) }
                }
            }
        }
        val removed = known.filterNot { it in visible }
        removed.chunked(500).forEach { mediaDao.deleteByIds(it) }

        val total = mediaDao.count()
        val wasFull = plans.all { it.isFull }
        db.withTransaction {
            scanDao.replaceVolumes(
                snapshots.map { VolumeStateEntity(it.name, it.version, it.generation) }
            )
            scanDao.upsertScanState(
                ScanStateEntity(
                    lastStartedAt = startedAt,
                    lastCompletedAt = clock(),
                    lastWasFull = wasFull,
                    lastAccess = access,
                    lastError = null,
                    lastItemCount = total,
                    classifierVersion = MediaClassifier.VERSION,
                )
            )
        }
        return CatalogResult(rowsRead = rowsRead, removed = removed.size, totalItems = total, wasFull = wasFull)
    }
}
