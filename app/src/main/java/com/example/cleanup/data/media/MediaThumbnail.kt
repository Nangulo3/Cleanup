package com.example.cleanup.data.media

import android.content.ContentResolver
import android.net.Uri
import android.util.Size
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import coil3.size.pxOrElse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

/**
 * Pide a Coil una miniatura generada por Android (ContentResolver.loadThumbnail) en lugar de
 * decodificar el archivo original. Es más rápido, usa la caché del sistema y funciona igual
 * para fotos y videos (en videos devuelve un fotograma).
 *
 * [version] (fecha de modificación) invalida la caché si el archivo cambia.
 */
data class MediaThumbnail(val uri: Uri, val version: Long)

class MediaThumbnailFetcher(
    private val data: MediaThumbnail,
    private val options: Options,
    private val resolver: ContentResolver,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val width = options.size.width.pxOrElse { DEFAULT_SIZE_PX }
        val height = options.size.height.pxOrElse { DEFAULT_SIZE_PX }
        // runInterruptible: si la miniatura sale de pantalla, Coil cancela y se interrumpe la carga.
        val bitmap = runInterruptible(Dispatchers.IO) {
            resolver.loadThumbnail(data.uri, Size(width, height), null)
        }
        return ImageFetchResult(
            image = bitmap.asImage(),
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    }

    class Factory(private val resolver: ContentResolver) : Fetcher.Factory<MediaThumbnail> {
        override fun create(data: MediaThumbnail, options: Options, imageLoader: ImageLoader): Fetcher =
            MediaThumbnailFetcher(data, options, resolver)
    }

    private companion object {
        const val DEFAULT_SIZE_PX = 256
    }
}

/** Clave de la caché en memoria de Coil (Coil añade el tamaño solicitado por su cuenta). */
class MediaThumbnailKeyer : Keyer<MediaThumbnail> {
    override fun key(data: MediaThumbnail, options: Options): String =
        "thumb:${data.uri}:${data.version}"
}
