package com.example.cleanup.data.db.dao

import com.example.cleanup.domain.model.MediaType

/** Totales por tipo de medio. Los archivos sin tamaño conocido se cuentan aparte. */
data class TypeTotals(
    val mediaType: MediaType,
    val itemCount: Int,
    val knownBytes: Long,
    val unknownSizeCount: Int,
)

/** Datos mínimos para dibujar una miniatura en la cuadrícula (menos memoria que la fila completa). */
data class MediaGridItem(
    val id: Long,
    val mediaType: MediaType,
    val contentUri: String,
    val displayName: String?,
    val sizeBytes: Long?,
    val durationMs: Long?,
    val sortDateMs: Long,
    val dateModifiedMs: Long?,
)
