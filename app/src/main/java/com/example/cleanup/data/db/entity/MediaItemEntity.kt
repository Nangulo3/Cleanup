package com.example.cleanup.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.cleanup.domain.model.MediaType

/**
 * Una foto o video visible en MediaStore. No guarda el archivo, solo cómo encontrarlo
 * ([id] de MediaStore + [contentUri]) y sus metadatos. Los valores desconocidos son null:
 * la interfaz muestra "Desconocido" en vez de inventar un número.
 */
@Entity(
    tableName = "media_items",
    indices = [
        Index(value = ["mediaType", "sortDateMs"]),
        Index(value = ["monthKey"]),
        Index(value = ["sizeBytes"]),
    ],
)
data class MediaItemEntity(
    /** _ID de MediaStore (único entre todos los volúmenes externos). */
    @PrimaryKey val id: Long,
    val mediaType: MediaType,
    val contentUri: String,
    val volumeName: String,
    val displayName: String?,
    val mimeType: String?,
    val relativePath: String?,
    val bucketName: String?,
    val sizeBytes: Long?,
    val width: Int?,
    val height: Int?,
    val orientation: Int?,
    val durationMs: Long?,
    val dateTakenMs: Long?,
    val dateAddedMs: Long?,
    val dateModifiedMs: Long?,
    /** GENERATION_MODIFIED de MediaStore: sirve para saber si el archivo cambió. */
    val generationModified: Long?,
    /** Mejor fecha disponible (tomada > modificada > añadida), en milisegundos. 0 si no hay ninguna. */
    val sortDateMs: Long,
    /** Año y mes de [sortDateMs] en la zona horaria local, como yyyyMM (p. ej. 202610). 0 = sin fecha. */
    val monthKey: Int,
    /** Momento en que la app leyó esta fila por última vez. */
    val catalogedAt: Long,
)
