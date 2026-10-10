package com.example.cleanup.detection

import com.example.cleanup.domain.model.MediaType

/** Estado de la huella de un candidato, ya normalizado (huellas vencidas = null). */
data class HashState(
    val id: Long,
    val mediaType: MediaType,
    val sizeBytes: Long,
    val quickHash: String?,
    val sha256: String?,
    val hasError: Boolean,
)

/**
 * Estrategia escalonada para no leer más de lo necesario:
 *  1. Solo archivos del mismo tipo y tamaño pueden ser idénticos.
 *  2. Entre ellos, la huella rápida descarta los que difieren al principio o al final.
 *  3. Solo los que coinciden en la huella rápida se leen completos (SHA-256).
 * Un archivo con error de lectura no se reintenta salvo que se pida ([retryErrors]).
 */
object DuplicatePlanner {

    fun needQuickHash(items: List<HashState>, retryErrors: Boolean): List<HashState> =
        items.groupBy { it.mediaType to it.sizeBytes }
            .values
            .filter { it.size > 1 }
            .flatten()
            .filter { it.quickHash == null && (retryErrors || !it.hasError) }

    fun needFullHash(items: List<HashState>, retryErrors: Boolean): List<HashState> =
        items.filter { it.quickHash != null }
            .groupBy { Triple(it.mediaType, it.sizeBytes, it.quickHash) }
            .values
            .filter { it.size > 1 }
            .flatten()
            .filter { it.sha256 == null && (retryErrors || !it.hasError) }
}
