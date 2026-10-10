package com.example.cleanup.worker

import androidx.core.net.toUri
import com.example.cleanup.data.db.AppDatabase
import com.example.cleanup.data.db.dao.VideoCandidate
import com.example.cleanup.data.db.entity.VideoSignatureEntity
import com.example.cleanup.detection.VideoFrameSampler
import com.example.cleanup.detection.VideoSignatures
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Fase del escaneo que muestrea fotogramas de los videos nuevos o modificados.
 * Decodificar video es costoso: se procesa **un video a la vez** y se guarda cada pocos videos.
 * Un video que no se puede leer se registra como error y no detiene el resto.
 */
class VideoScanner(
    private val db: AppDatabase,
    private val sampler: VideoFrameSampler,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun run(retryErrors: Boolean, onProgress: suspend (ScanProgressInfo) -> Unit): Int {
        val dao = db.similarDao()
        val candidates = dao.videoCandidates(VideoSignatures.VERSION, retryErrors)
        if (candidates.isNotEmpty()) {
            onProgress(ScanProgressInfo(ScanPhase.VIDEO, 0, candidates.size))
            val pending = ArrayList<VideoSignatureEntity>(SAVE_EVERY)
            candidates.forEachIndexed { index, candidate ->
                currentCoroutineContext().ensureActive()
                pending += withContext(Dispatchers.IO) { compute(candidate) }
                if (pending.size >= SAVE_EVERY || index == candidates.lastIndex) {
                    dao.upsertVideoSignatures(ArrayList(pending))
                    pending.clear()
                    onProgress(ScanProgressInfo(ScanPhase.VIDEO, index + 1, candidates.size))
                }
            }
        }
        dao.deleteVideoOrphans()
        return candidates.size
    }

    private fun compute(c: VideoCandidate): VideoSignatureEntity = try {
        val sample = sampler.sample(c.contentUri.toUri(), c.durationMs)
        VideoSignatureEntity(
            mediaId = c.id,
            sizeBytes = c.sizeBytes,
            dateModifiedMs = c.dateModifiedMs,
            generationModified = c.generationModified,
            algorithmVersion = VideoSignatures.VERSION,
            durationMs = sample.durationMs,
            frameCount = VideoSignatures.FRAME_COUNT,
            framePhashes = VideoSignatures.toBytes(sample.phashes),
            frameDhashes = VideoSignatures.toBytes(sample.dhashes),
            lowInfoMask = sample.lowInfoMask,
            error = null,
            computedAt = clock(),
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        VideoSignatureEntity(
            mediaId = c.id, sizeBytes = c.sizeBytes, dateModifiedMs = c.dateModifiedMs,
            generationModified = c.generationModified, algorithmVersion = VideoSignatures.VERSION,
            durationMs = c.durationMs, frameCount = 0, framePhashes = null, frameDhashes = null,
            lowInfoMask = 0, error = ERROR_UNREADABLE, computedAt = clock(),
        )
    }

    private companion object {
        const val SAVE_EVERY = 4
        const val ERROR_UNREADABLE = "VIDEO_UNREADABLE"
    }
}
