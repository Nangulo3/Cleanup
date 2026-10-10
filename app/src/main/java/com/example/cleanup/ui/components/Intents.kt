package com.example.cleanup.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Abre un elemento con la app que el usuario elija (galería, reproductor...).
 * Devuelve false si no hay ninguna app capaz de abrirlo.
 */
fun Context.openMediaExternally(uri: Uri, mimeType: String?): Boolean {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType ?: "*/*")
        // Permiso temporal de lectura para la otra app (no se le da acceso a nada más).
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return try {
        startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
