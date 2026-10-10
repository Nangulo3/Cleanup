package com.example.cleanup

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import com.example.cleanup.data.media.MediaThumbnailFetcher
import com.example.cleanup.data.media.MediaThumbnailKeyer

/**
 * Punto de entrada del proceso. Crea el [AppContainer] (dependencias compartidas) una sola vez
 * y configura el cargador de imágenes de Coil que usan todas las miniaturas.
 */
class CleanupApp : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                // Miniaturas del sistema para cuadrículas (ver MediaThumbnail).
                add(MediaThumbnailFetcher.Factory(contentResolver))
                add(MediaThumbnailKeyer())
            }
            .crossfade(true)
            .build()
}
