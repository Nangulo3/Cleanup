package com.example.cleanup.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Huellas del contenido de un archivo para detectar duplicados exactos.
 *
 * La "firma" ([sizeBytes], [dateModifiedMs], [generationModified]) es la del archivo cuando se
 * calculó la huella: si el archivo cambia, la firma deja de coincidir con media_items y la huella
 * se ignora y se recalcula. Así nunca se compara un hash viejo con un archivo nuevo.
 */
@Entity(tableName = "media_fingerprints")
data class FingerprintEntity(
    @PrimaryKey val mediaId: Long,
    val sizeBytes: Long?,
    val dateModifiedMs: Long?,
    val generationModified: Long?,
    /** SHA-256 de (tamaño + primeros 64 KB + últimos 64 KB): filtro barato. */
    val quickHash: String?,
    /** SHA-256 del contenido completo: confirma que dos archivos son idénticos. */
    val sha256: String?,
    /** Código de error si no se pudo leer el archivo (no se clasifica como duplicado). */
    val hashError: String?,
    val hashedAt: Long,
)

/** Copia que el usuario eligió conservar en un grupo de duplicados (clave = SHA-256 del grupo). */
@Entity(tableName = "duplicate_keepers")
data class DuplicateKeeperEntity(
    @PrimaryKey val groupKey: String,
    val mediaId: Long,
    val chosenAt: Long,
)
