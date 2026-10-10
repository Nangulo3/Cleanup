package com.example.cleanup.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Firma visual de un video: hashes de [frameCount] fotogramas tomados en posiciones relativas
 * fijas (10 % … 90 % de la duración). Como las posiciones son relativas, dos videos casi iguales
 * quedan alineados aunque su duración difiera un poco.
 */
@Entity(tableName = "video_signatures")
data class VideoSignatureEntity(
    @PrimaryKey val mediaId: Long,
    val sizeBytes: Long?,
    val dateModifiedMs: Long?,
    val generationModified: Long?,
    val algorithmVersion: Int,
    val durationMs: Long?,
    val frameCount: Int,
    /** pHash de cada fotograma, 8 bytes por fotograma. */
    val framePhashes: ByteArray?,
    /** dHash de cada fotograma, 8 bytes por fotograma. */
    val frameDhashes: ByteArray?,
    /** Bit i = 1 si el fotograma i es casi uniforme (negro, blanco): no se usa para comparar. */
    val lowInfoMask: Int,
    val error: String?,
    val computedAt: Long,
) {
    // ByteArray no compara por contenido en data classes; Room no lo necesita, pero se deja explícito.
    override fun equals(other: Any?): Boolean = other is VideoSignatureEntity && other.mediaId == mediaId &&
        other.computedAt == computedAt
    override fun hashCode(): Int = mediaId.hashCode() * 31 + computedAt.hashCode()
}
