package com.example.cleanup.detection

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri

/** Resultado de muestrear un video. */
class SampledVideo(
    val durationMs: Long,
    val phashes: LongArray,
    val dhashes: LongArray,
    val lowInfoMask: Int,
)

/**
 * Extrae fotogramas reducidos (≈160 px) en posiciones fijas con MediaMetadataRetriever, que usa los
 * decodificadores del propio teléfono. Pide el fotograma clave más cercano (rápido) y libera el
 * recurso siempre. No usa Media3 ni comprime nada.
 */
class VideoFrameSampler(context: Context) {

    private val appContext = context.applicationContext

    fun sample(uri: Uri, durationHintMs: Long?): SampledVideo {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(appContext, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.takeIf { it > 0 }
                ?: durationHintMs?.takeIf { it > 0 }
                ?: throw IllegalStateException("Duración desconocida")

            val phashes = LongArray(VideoSignatures.FRAME_COUNT)
            val dhashes = LongArray(VideoSignatures.FRAME_COUNT)
            var lowInfoMask = 0
            var extracted = 0
            VideoSignatures.FRAME_POSITIONS.forEachIndexed { i, position ->
                val timeUs = (duration * position * 1000).toLong()
                val frame = retriever.getScaledFrameAtTime(
                    timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, FRAME_SIZE, FRAME_SIZE,
                )
                if (frame == null) {
                    lowInfoMask = lowInfoMask or (1 shl i) // sin fotograma: no se usa para comparar
                } else {
                    val signature = signFrame(frame)
                    phashes[i] = signature.phash
                    dhashes[i] = signature.dhash
                    if (signature.lowInformation) lowInfoMask = lowInfoMask or (1 shl i)
                    extracted++
                }
            }
            if (extracted < VideoSignatures.MIN_USEFUL_FRAMES) throw IllegalStateException("Sin fotogramas")
            return SampledVideo(duration, phashes, dhashes, lowInfoMask)
        } finally {
            // release() puede lanzar IOException en algunas versiones; no debe tapar el resultado.
            runCatching { retriever.release() }
        }
    }

    private fun signFrame(frame: Bitmap): ImageSignature {
        val readable = if (frame.config == Bitmap.Config.HARDWARE) frame.copy(Bitmap.Config.ARGB_8888, false) else frame
        try {
            val pixels = IntArray(readable.width * readable.height)
            readable.getPixels(pixels, 0, readable.width, 0, 0, readable.width, readable.height)
            return ImageSignatures.compute(pixels, readable.width, readable.height)
        } finally {
            if (readable !== frame) readable.recycle()
            frame.recycle()
        }
    }

    private companion object {
        const val FRAME_SIZE = 160
    }
}
