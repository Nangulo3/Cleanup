package com.example.cleanup.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.cleanup.settings.SimilarityLevel

/**
 * Firmas visuales de una foto, calculadas a partir de su miniatura del sistema.
 * Igual que las huellas exactas, guardan la firma del archivo para invalidarse si cambia.
 */
@Entity(tableName = "perceptual_hashes")
data class PerceptualHashEntity(
    @PrimaryKey val mediaId: Long,
    val sizeBytes: Long?,
    val dateModifiedMs: Long?,
    val generationModified: Long?,
    /** Versión del algoritmo: si cambia, se recalcula. */
    val algorithmVersion: Int,
    /** pHash (DCT) de 64 bits: estructura general de la imagen. */
    val phash: Long?,
    /** dHash de 64 bits: dirección de los cambios de brillo. */
    val dhash: Long?,
    /** dHash de 256 bits (cuatro partes), más detallado; se usa para capturas de pantalla. */
    val d256a: Long?,
    val d256b: Long?,
    val d256c: Long?,
    val d256d: Long?,
    /** Imagen casi uniforme (negra, blanca...): sus hashes no son fiables y no se agrupa. */
    val lowInformation: Boolean,
    /** Nitidez aproximada (varianza del laplaciano); sirve para sugerir cuál conservar. */
    val sharpness: Float?,
    val error: String?,
    val computedAt: Long,
)

/** Tipo de grupo de similares. */
enum class SimilarKind { PHOTO, SCREENSHOT, VIDEO }

/** Grupo de elementos visualmente parecidos (no idénticos). Se recalcula al final de cada escaneo. */
@Entity(tableName = "similar_groups", indices = [Index(value = ["kind"])])
data class SimilarGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: SimilarKind,
    val level: SimilarityLevel,
    val algorithmVersion: Int,
    /** Sugerencia opcional de cuál conservar (la más nítida). Nunca se preselecciona nada. */
    val suggestedId: Long?,
    /** Mayor distancia entre dos miembros (más baja = más parecidos). */
    val maxDistance: Int,
    val createdAt: Long,
)

@Entity(
    tableName = "similar_members",
    primaryKeys = ["groupId", "mediaId"],
    foreignKeys = [
        ForeignKey(
            entity = SimilarGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["mediaId"])],
)
data class SimilarMemberEntity(
    val groupId: Long,
    val mediaId: Long,
)
