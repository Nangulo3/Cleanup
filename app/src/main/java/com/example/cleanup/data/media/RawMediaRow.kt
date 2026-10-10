package com.example.cleanup.data.media

import com.example.cleanup.data.db.entity.MediaItemEntity
import com.example.cleanup.domain.model.MediaType
import java.time.Instant
import java.time.ZoneId

/**
 * Una fila tal como la entrega MediaStore, sin interpretar (fechas en sus unidades originales,
 * ceros incluidos). Separarla de la entidad permite probar la conversión sin un teléfono.
 */
data class RawMediaRow(
    val id: Long,
    val volumeName: String,
    val isVideo: Boolean,
    val contentUri: String,
    val displayName: String?,
    val mimeType: String?,
    val size: Long?,
    val width: Int?,
    val height: Int?,
    val orientation: Int?,
    val duration: Long?,
    /** DATE_TAKEN: milisegundos. */
    val dateTakenMs: Long?,
    /** DATE_ADDED: segundos. */
    val dateAddedSec: Long?,
    /** DATE_MODIFIED: segundos. */
    val dateModifiedSec: Long?,
    val relativePath: String?,
    val bucketName: String?,
    val generationModified: Long?,
)

object MediaRowMapper {

    /**
     * Convierte una fila de MediaStore en entidad:
     * - tamaños, dimensiones, duraciones y fechas ≤ 0 se tratan como desconocidos (null);
     * - todas las fechas pasan a milisegundos;
     * - la fecha para ordenar y agrupar por mes prefiere la de captura.
     */
    fun toEntity(row: RawMediaRow, catalogedAt: Long, zone: ZoneId): MediaItemEntity {
        val dateTaken = row.dateTakenMs.positiveOrNull()
        val dateAdded = row.dateAddedSec.positiveOrNull()?.times(1000)
        val dateModified = row.dateModifiedSec.positiveOrNull()?.times(1000)
        val sortDate = dateTaken ?: dateModified ?: dateAdded ?: 0L
        return MediaItemEntity(
            id = row.id,
            mediaType = if (row.isVideo) MediaType.VIDEO else MediaType.PHOTO,
            contentUri = row.contentUri,
            volumeName = row.volumeName,
            displayName = row.displayName?.takeIf { it.isNotBlank() },
            mimeType = row.mimeType?.takeIf { it.isNotBlank() },
            relativePath = row.relativePath?.takeIf { it.isNotBlank() },
            bucketName = row.bucketName?.takeIf { it.isNotBlank() },
            sizeBytes = row.size.positiveOrNull(),
            width = row.width?.takeIf { it > 0 },
            height = row.height?.takeIf { it > 0 },
            orientation = row.orientation,
            durationMs = if (row.isVideo) row.duration.positiveOrNull() else null,
            dateTakenMs = dateTaken,
            dateAddedMs = dateAdded,
            dateModifiedMs = dateModified,
            generationModified = row.generationModified,
            sortDateMs = sortDate,
            monthKey = monthKey(sortDate, zone),
            catalogedAt = catalogedAt,
        )
    }

    /** yyyyMM en la zona dada; 0 si no hay fecha. */
    fun monthKey(epochMs: Long, zone: ZoneId): Int {
        if (epochMs <= 0) return 0
        val date = Instant.ofEpochMilli(epochMs).atZone(zone)
        return date.year * 100 + date.monthValue
    }

    private fun Long?.positiveOrNull(): Long? = this?.takeIf { it > 0 }
}
