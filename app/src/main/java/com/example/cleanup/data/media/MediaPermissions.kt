package com.example.cleanup.data.media

import android.Manifest
import com.example.cleanup.domain.model.MediaAccess

/**
 * Reglas de permisos por versión de Android, como funciones puras para poder probarlas
 * en la JVM pasando el nivel de API y una función que dice si un permiso está concedido.
 *
 * - API 30–32: READ_EXTERNAL_STORAGE.
 * - API 33:    READ_MEDIA_IMAGES + READ_MEDIA_VIDEO.
 * - API 34+:   lo anterior + READ_MEDIA_VISUAL_USER_SELECTED, que Android concede sola cuando
 *              el usuario elige "Seleccionar fotos y videos" (acceso parcial).
 */
object MediaPermissions {

    const val API_TIRAMISU = 33
    const val API_UPSIDE_DOWN_CAKE = 34

    fun permissionsToRequest(sdkInt: Int): Array<String> = when {
        sdkInt >= API_UPSIDE_DOWN_CAKE -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )

        sdkInt >= API_TIRAMISU -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
        )

        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun resolveAccess(sdkInt: Int, isGranted: (String) -> Boolean): MediaAccess {
        if (sdkInt < API_TIRAMISU) {
            return if (isGranted(Manifest.permission.READ_EXTERNAL_STORAGE)) MediaAccess.FULL
            else MediaAccess.DENIED
        }
        val images = isGranted(Manifest.permission.READ_MEDIA_IMAGES)
        val videos = isGranted(Manifest.permission.READ_MEDIA_VIDEO)
        if (images && videos) return MediaAccess.FULL

        val userSelected = sdkInt >= API_UPSIDE_DOWN_CAKE &&
            isGranted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        // Solo imágenes o solo videos también es un acceso incompleto: se trata como parcial
        // para que la interfaz explique que el análisis no cubre toda la biblioteca.
        return if (userSelected || images || videos) MediaAccess.PARTIAL else MediaAccess.DENIED
    }
}
