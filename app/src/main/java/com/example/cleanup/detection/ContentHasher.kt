package com.example.cleanup.detection

import android.content.Context
import android.net.Uri
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.nio.ByteBuffer

/**
 * Lee el contenido de un archivo de MediaStore (por URI, nunca por ruta) para calcular sus huellas.
 *
 * Nota: sin el permiso de ubicación en medios, Android oculta los datos de ubicación (GPS) de las
 * fotos al leerlas. Por eso dos fotos que solo difieran en esos datos se consideran idénticas.
 * La app no pide ese permiso a propósito.
 */
class ContentHasher(context: Context) {

    private val resolver = context.applicationContext.contentResolver

    fun quickHash(uri: Uri, size: Long): String {
        val pfd = resolver.openFileDescriptor(uri, "r") ?: throw FileNotFoundException("Sin descriptor")
        return pfd.use {
            FileInputStream(it.fileDescriptor).use { input ->
                val channel = input.channel
                HashAlgorithms.quickHash(size) { position, length ->
                    val buffer = ByteBuffer.allocate(length)
                    var offset = position
                    while (buffer.hasRemaining()) {
                        val read = channel.read(buffer, offset)
                        if (read <= 0) break // el archivo es más corto de lo esperado
                        offset += read
                    }
                    buffer.array().copyOf(buffer.position())
                }
            }
        }
    }

    /** SHA-256 completo leído en bloques de 64 KB. [onChunk] permite cancelar a mitad de un archivo grande. */
    fun fullHash(uri: Uri, onChunk: () -> Unit): String {
        val input = resolver.openInputStream(uri) ?: throw FileNotFoundException("Sin flujo de lectura")
        return input.use { HashAlgorithms.sha256(it, onChunk = onChunk) }
    }
}
