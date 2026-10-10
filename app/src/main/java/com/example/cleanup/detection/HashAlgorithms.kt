package com.example.cleanup.detection

import java.io.InputStream
import java.security.MessageDigest

/**
 * Cálculo de huellas de contenido, sin dependencias de Android para poder probarlo en la JVM.
 * Todo se lee por bloques: nunca se carga un archivo completo en memoria.
 */
object HashAlgorithms {

    const val BUFFER_SIZE = 64 * 1024
    const val QUICK_CHUNK = 64 * 1024

    /**
     * SHA-256 del flujo completo, leído en bloques de [bufferSize].
     * [onChunk] se llama tras cada bloque; puede lanzar una excepción para cancelar.
     */
    fun sha256(input: InputStream, bufferSize: Int = BUFFER_SIZE, onChunk: () -> Unit = {}): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(bufferSize)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read > 0) digest.update(buffer, 0, read)
            onChunk()
        }
        return digest.digest().toHex()
    }

    /**
     * Huella rápida: SHA-256 de (tamaño + primeros [QUICK_CHUNK] bytes + últimos [QUICK_CHUNK] bytes).
     * Dos archivos con distinta huella rápida seguro son distintos; con la misma, puede que no:
     * por eso los que coinciden se confirman con [sha256] completo.
     *
     * [readAt] lee hasta `length` bytes desde `position` (puede devolver menos si el archivo es más corto).
     */
    fun quickHash(size: Long, readAt: (position: Long, length: Int) -> ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(size.toString().toByteArray(Charsets.US_ASCII))
        val headLength = minOf(size, QUICK_CHUNK.toLong()).toInt()
        digest.update(readAt(0L, headLength))
        if (size > QUICK_CHUNK) {
            // Si el archivo es menor que dos bloques, el final se solapa con el inicio: no importa.
            val tailStart = maxOf(QUICK_CHUNK.toLong(), size - QUICK_CHUNK)
            digest.update(readAt(tailStart, (size - tailStart).toInt()))
        }
        return digest.digest().toHex()
    }

    private val HEX = "0123456789abcdef".toCharArray()

    fun ByteArray.toHex(): String {
        val chars = CharArray(size * 2)
        forEachIndexed { i, byte ->
            val v = byte.toInt() and 0xFF
            chars[i * 2] = HEX[v ushr 4]
            chars[i * 2 + 1] = HEX[v and 0x0F]
        }
        return String(chars)
    }
}
