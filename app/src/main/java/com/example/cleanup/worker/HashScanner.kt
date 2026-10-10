package com.example.cleanup.worker

import androidx.core.net.toUri
import com.example.cleanup.data.db.AppDatabase
import com.example.cleanup.data.db.dao.HashCandidate
import com.example.cleanup.data.db.entity.FingerprintEntity
import com.example.cleanup.detection.ContentHasher
import com.example.cleanup.detection.DuplicatePlanner
import com.example.cleanup.detection.HashState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Resultado de la fase de hash. */
data class HashResult(val quickHashed: Int, val fullHashed: Int, val errors: Int)

/**
 * Fase del escaneo que calcula huellas de contenido para encontrar duplicados exactos
 * (ver [DuplicatePlanner]). Guarda cada lote al terminarlo, así que si el sistema detiene el
 * trabajo, al reanudar no se repite lo ya calculado.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HashScanner(
    private val db: AppDatabase,
    private val hasher: ContentHasher,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    // Como mucho dos archivos leyéndose a la vez: suficiente para el almacenamiento flash
    // sin saturar memoria ni batería.
    private val hashDispatcher = Dispatchers.IO.limitedParallelism(2)

    suspend fun run(retryErrors: Boolean, onProgress: suspend (ScanProgressInfo) -> Unit): HashResult {
        val dao = db.fingerprintDao()
        var errors = 0

        // 1. Huella rápida.
        val first = dao.hashCandidates()
        val needQuick = DuplicatePlanner.needQuickHash(first.map { it.toState() }, retryErrors)
        val byId = first.associateBy { it.id }
        errors += process(needQuick.mapNotNull { byId[it.id] }, ScanPhase.HASH_QUICK, onProgress) { c ->
            val quick = hasher.quickHash(c.contentUri.toUri(), c.sizeBytes)
            c.toEntity(quickHash = quick, sha256 = null, error = null)
        }

        // 2. SHA-256 completo solo de los que coinciden en la huella rápida.
        val second = dao.hashCandidates()
        val needFull = DuplicatePlanner.needFullHash(second.map { it.toState() }, retryErrors)
        val byId2 = second.associateBy { it.id }
        val job = currentCoroutineContext()[Job]
        errors += process(needFull.mapNotNull { byId2[it.id] }, ScanPhase.HASH_FULL, onProgress) { c ->
            val full = hasher.fullHash(c.contentUri.toUri()) { job?.ensureActive() }
            c.toEntity(quickHash = c.quickHash, sha256 = full, error = null)
        }

        dao.deleteOrphans()
        return HashResult(quickHashed = needQuick.size, fullHashed = needFull.size, errors = errors)
    }

    /**
     * Calcula en lotes pequeños, guarda cada lote y reporta progreso real (procesados / total).
     * Un error en un archivo se registra y no detiene el resto. Devuelve cuántos fallaron.
     */
    private suspend fun process(
        items: List<HashCandidate>,
        phase: ScanPhase,
        onProgress: suspend (ScanProgressInfo) -> Unit,
        compute: (HashCandidate) -> FingerprintEntity,
    ): Int {
        if (items.isEmpty()) return 0
        var done = 0
        var errors = 0
        onProgress(ScanProgressInfo(phase, 0, items.size))
        for (batch in items.chunked(BATCH_SIZE)) {
            currentCoroutineContext().ensureActive()
            val results = coroutineScope {
                batch.map { candidate ->
                    async(hashDispatcher) {
                        try {
                            compute(candidate)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // Archivo ilegible, borrado a mitad o sin permiso: no se clasifica.
                            candidate.toEntity(quickHash = null, sha256 = null, error = ERROR_READ_FAILED)
                        }
                    }
                }.awaitAll()
            }
            errors += results.count { it.hashError != null }
            db.fingerprintDao().upsertAll(results)
            done += batch.size
            onProgress(ScanProgressInfo(phase, done, items.size))
        }
        return errors
    }

    private fun HashCandidate.toState(): HashState = HashState(
        id = id,
        mediaType = mediaType,
        sizeBytes = sizeBytes,
        quickHash = if (fingerprintValid) quickHash else null,
        sha256 = if (fingerprintValid) sha256 else null,
        hasError = fingerprintValid && hashError != null,
    )

    private fun HashCandidate.toEntity(quickHash: String?, sha256: String?, error: String?) = FingerprintEntity(
        mediaId = id,
        sizeBytes = sizeBytes,
        dateModifiedMs = dateModifiedMs,
        generationModified = generationModified,
        quickHash = quickHash,
        sha256 = sha256,
        hashError = error,
        hashedAt = clock(),
    )

    private companion object {
        const val BATCH_SIZE = 8
        const val ERROR_READ_FAILED = "READ_FAILED"
    }
}
