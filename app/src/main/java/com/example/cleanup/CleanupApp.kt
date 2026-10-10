package com.example.cleanup

import android.app.Application
import android.os.StrictMode
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
        if (BuildConfig.DEBUG) enableStrictMode()
        super.onCreate()
        container = AppContainer(this)
    }

    /**
     * Solo en debug: avisa en Logcat (etiqueta "StrictMode") cuando se lee o escribe disco en el
     * hilo principal (causa tirones en la interfaz) o cuando se olvida cerrar un cursor, un
     * archivo o la base de datos. No cierra la app: solo registra para poder corregirlo.
     * En release no hace nada.
     */
    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork() // la app no usa red; si algo la usa, se verá aquí
                .detectCustomSlowCalls()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()   // cursores, streams de archivos
                .detectLeakedSqlLiteObjects()
                .detectActivityLeaks()
                .detectLeakedRegistrationObjects() // ContentObserver sin desregistrar
                .detectContentUriWithoutPermission() // URIs enviadas a otra app sin permiso temporal
                .penaltyLog()
                .build()
        )
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
