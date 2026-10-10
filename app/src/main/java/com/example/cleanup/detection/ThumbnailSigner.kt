package com.example.cleanup.detection

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Size

/**
 * Obtiene la miniatura del sistema (unos 256 px, ya orientada) y calcula sus firmas visuales.
 * Nunca decodifica la foto original completa.
 */
class ThumbnailSigner(context: Context) {

    private val resolver = context.applicationContext.contentResolver

    fun sign(uri: Uri): ImageSignature {
        val thumbnail = resolver.loadThumbnail(uri, Size(THUMB_SIZE, THUMB_SIZE), null)
        // Los bitmaps de hardware no permiten leer píxeles: se copian a memoria normal.
        val readable = if (thumbnail.config == Bitmap.Config.HARDWARE) {
            thumbnail.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            thumbnail
        }
        try {
            val w = readable.width
            val h = readable.height
            val pixels = IntArray(w * h)
            readable.getPixels(pixels, 0, w, 0, 0, w, h)
            return ImageSignatures.compute(pixels, w, h)
        } finally {
            if (readable !== thumbnail) readable.recycle()
            thumbnail.recycle()
        }
    }

    private companion object {
        const val THUMB_SIZE = 256
    }
}
