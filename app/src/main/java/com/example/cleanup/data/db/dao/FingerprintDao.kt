package com.example.cleanup.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.cleanup.data.db.entity.DuplicateKeeperEntity
import com.example.cleanup.data.db.entity.FingerprintEntity
import com.example.cleanup.domain.model.MediaType
import kotlinx.coroutines.flow.Flow

/** Elemento que comparte tamaño con otro (candidato a duplicado) y el estado de su huella. */
data class HashCandidate(
    val id: Long,
    val mediaType: MediaType,
    val contentUri: String,
    val sizeBytes: Long,
    val dateModifiedMs: Long?,
    val generationModified: Long?,
    val quickHash: String?,
    val sha256: String?,
    val hashError: String?,
    /** La huella existe y su firma coincide con el archivo actual. */
    val fingerprintValid: Boolean,
)

/** Fila de un miembro de un grupo de duplicados exactos. */
data class DuplicateRow(
    val groupKey: String,
    val id: Long,
    val mediaType: MediaType,
    val contentUri: String,
    val displayName: String?,
    val mimeType: String?,
    val sizeBytes: Long,
    val width: Int?,
    val height: Int?,
    val durationMs: Long?,
    val sortDateMs: Long,
    val dateTakenMs: Long?,
    val dateAddedMs: Long?,
    val dateModifiedMs: Long?,
    val relativePath: String?,
)

@Dao
interface FingerprintDao {

    /** Solo elementos cuyo tipo y tamaño coinciden con al menos otro: los demás no pueden ser duplicados. */
    @Query(
        """
        SELECT m.id, m.mediaType, m.contentUri, m.sizeBytes, m.dateModifiedMs, m.generationModified,
               f.quickHash, f.sha256, f.hashError,
               CASE WHEN f.mediaId IS NOT NULL
                     AND f.sizeBytes IS m.sizeBytes
                     AND f.dateModifiedMs IS m.dateModifiedMs
                     AND f.generationModified IS m.generationModified
                    THEN 1 ELSE 0 END AS fingerprintValid
        FROM media_items m
        JOIN (SELECT mediaType AS t, sizeBytes AS s FROM media_items
              WHERE sizeBytes IS NOT NULL
              GROUP BY mediaType, sizeBytes HAVING COUNT(*) > 1) d
          ON d.t = m.mediaType AND d.s = m.sizeBytes
        LEFT JOIN media_fingerprints f ON f.mediaId = m.id
        ORDER BY m.sizeBytes DESC, m.id ASC
        """
    )
    suspend fun hashCandidates(): List<HashCandidate>

    @Upsert
    suspend fun upsertAll(fingerprints: List<FingerprintEntity>)

    @Query("DELETE FROM media_fingerprints WHERE mediaId NOT IN (SELECT id FROM media_items)")
    suspend fun deleteOrphans()

    @Query("DELETE FROM media_fingerprints")
    suspend fun deleteAll()

    /**
     * Miembros de grupos de duplicados exactos: SHA-256 completo igual y huella vigente.
     * Si la verificación no terminó (sin SHA-256) el archivo no aparece.
     */
    @Query(
        """
        SELECT f.sha256 AS groupKey, m.id, m.mediaType, m.contentUri, m.displayName, m.mimeType,
               m.sizeBytes, m.width, m.height, m.durationMs, m.sortDateMs, m.dateTakenMs,
               m.dateAddedMs, m.dateModifiedMs, m.relativePath
        FROM media_items m
        JOIN media_fingerprints f ON f.mediaId = m.id
        WHERE f.sha256 IS NOT NULL
          AND m.sizeBytes IS NOT NULL
          AND f.sizeBytes IS m.sizeBytes
          AND f.dateModifiedMs IS m.dateModifiedMs
          AND f.generationModified IS m.generationModified
          AND f.sha256 IN (
              SELECT f2.sha256 FROM media_fingerprints f2
              JOIN media_items m2 ON m2.id = f2.mediaId
              WHERE f2.sha256 IS NOT NULL
                AND f2.sizeBytes IS m2.sizeBytes
                AND f2.dateModifiedMs IS m2.dateModifiedMs
                AND f2.generationModified IS m2.generationModified
              GROUP BY f2.sha256
              HAVING COUNT(*) > 1
          )
        ORDER BY m.sizeBytes DESC, f.sha256 ASC, m.id ASC
        """
    )
    fun observeDuplicateRows(): Flow<List<DuplicateRow>>

    @Query("SELECT * FROM duplicate_keepers")
    fun observeKeepers(): Flow<List<DuplicateKeeperEntity>>

    @Upsert
    suspend fun upsertKeeper(keeper: DuplicateKeeperEntity)
}
