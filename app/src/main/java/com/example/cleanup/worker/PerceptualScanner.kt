package com.example.cleanup.worker

import androidx.core.net.toUri
import com.example.cleanup.data.db.AppDatabase
import com.example.cleanup.data.db.dao.PerceptualCandidate
import com.example.cleanup.data.db.entity.PerceptualHashEntity
import com.example.cleanup.detection.ImageSignatures
import com.example.cleanup.detection.ThumbnailSigner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Fase del escaneo que calcula las firmas visuales de las fotos nuevas o modificadas.
 * Usa miniaturas del sistema, dos a la vez, y guarda cada lote al terminarlo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PerceptualScanner(
    private val db: AppDatabase,
    private val signer: ThumbnailSigner,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dispatcher = Dispatchers.IO.limitedParallelism(2)

    /** Devuelve cuántas fotos se procesaron. */
    suspend fun run(retryErrors: Boolean, onProgress: suspend (ScanProgressInfo) -> Unit): Int {
        val dao = db.similarDao()
        val candidates = dao.perceptualCandidates(ImageSignatures.VERSION, retryErrors)
        if (candidates.isNotEmpty()) {
            var done = 0
            onProgress(ScanProgressInfo(ScanPhase.PERCEPTUAL, 0, candidates.size))
            for (batch in candidates.chunked(BATCH_SIZE)) {
                currentCoroutineContext().ensureActive()
                val results = coroutineScope {
                    batch.map { candidate -> async(dispatcher) { compute(candidate) } }.awaitAll()
                }
                dao.upsertPerceptual(results)
                done += batch.size
                onProgress(ScanProgressInfo(ScanPhase.PERCEPTUAL, done, candidates.size))
            }
        }
        dao.deletePerceptualOrphans()
        return candidates.size
    }

    private fun compute(c: PerceptualCandidate): PerceptualHashEntity = try {
        val s = signer.sign(c.contentUri.toUri())
        PerceptualHashEntity(
            mediaId = c.id,
            sizeBytes = c.sizeBytes,
            dateModifiedMs = c.dateModifiedMs,
            generationModified = c.generationModified,
            algorithmVersion = ImageSignatures.VERSION,
            phash = s.phash,
            dhash = s.dhash,
            d256a = s.d256[0],
            d256b = s.d256[1],
            d256c = s.d256[2],
            d256d = s.d256[3],
            lowInformation = s.lowInformation,
            sharpness = s.sharpness,
            error = null,
            computedAt = clock(),
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Formato no compatible o archivo inaccesible: no se agrupa, el resto sigue.
        PerceptualHashEntity(
            mediaId = c.id, sizeBytes = c.sizeBytes, dateModifiedMs = c.dateModifiedMs,
            generationModified = c.generationModified, algorithmVersion = ImageSignatures.VERSION,
            phash = null, dhash = null, d256a = null, d256b = null, d256c = null, d256d = null,
            lowInformation = false, sharpness = null, error = ERROR_THUMBNAIL, computedAt = clock(),
        )
    }

    private companion object {
        const val BATCH_SIZE = 16
        const val ERROR_THUMBNAIL = "THUMBNAIL_FAILED"
    }
}
