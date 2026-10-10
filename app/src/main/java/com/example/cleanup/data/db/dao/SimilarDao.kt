package com.example.cleanup.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.example.cleanup.data.db.entity.PerceptualHashEntity
import com.example.cleanup.data.db.entity.SimilarGroupEntity
import com.example.cleanup.data.db.entity.SimilarKind
import com.example.cleanup.data.db.entity.SimilarMemberEntity
import com.example.cleanup.data.db.entity.VideoSignatureEntity
import com.example.cleanup.domain.model.MediaType
import kotlinx.coroutines.flow.Flow

/** Foto sin firma visual vigente. */
data class PerceptualCandidate(
    val id: Long,
    val contentUri: String,
    val sizeBytes: Long?,
    val dateModifiedMs: Long?,
    val generationModified: Long?,
)

/** Datos para agrupar similares. [sha256] permite tratar los duplicados exactos como uno solo. */
data class SimilarityInput(
    val id: Long,
    val sortDateMs: Long,
    val isScreenshot: Boolean,
    val width: Int?,
    val height: Int?,
    val phash: Long,
    val dhash: Long,
    val d256a: Long?,
    val d256b: Long?,
    val d256c: Long?,
    val d256d: Long?,
    val lowInformation: Boolean,
    val sharpness: Float?,
    val sha256: String?,
)

/** Video sin firma vigente. */
data class VideoCandidate(
    val id: Long,
    val contentUri: String,
    val sizeBytes: Long?,
    val dateModifiedMs: Long?,
    val generationModified: Long?,
    val durationMs: Long?,
)

/** Datos para agrupar videos similares. */
data class VideoInput(
    val id: Long,
    val sortDateMs: Long,
    val sizeBytes: Long?,
    val width: Int?,
    val height: Int?,
    val orientation: Int?,
    val durationMs: Long,
    val framePhashes: ByteArray,
    val frameDhashes: ByteArray,
    val lowInfoMask: Int,
    val sha256: String?,
)

/** Fila de un miembro de un grupo de similares para la interfaz. */
data class SimilarRow(
    val groupId: Long,
    val suggestedId: Long?,
    val maxDistance: Int,
    val id: Long,
    val mediaType: MediaType,
    val contentUri: String,
    val displayName: String?,
    val sizeBytes: Long?,
    val width: Int?,
    val height: Int?,
    val durationMs: Long?,
    val sortDateMs: Long,
    val dateModifiedMs: Long?,
)

/** Grupo listo para guardar. */
data class NewSimilarGroup(
    val group: SimilarGroupEntity,
    val memberIds: List<Long>,
)

@Dao
interface SimilarDao {

    @Query(
        """
        SELECT m.id, m.contentUri, m.sizeBytes, m.dateModifiedMs, m.generationModified
        FROM media_items m
        LEFT JOIN perceptual_hashes p ON p.mediaId = m.id
        WHERE m.mediaType = 'PHOTO'
          AND (p.mediaId IS NULL
               OR p.algorithmVersion != :version
               OR NOT (p.sizeBytes IS m.sizeBytes
                       AND p.dateModifiedMs IS m.dateModifiedMs
                       AND p.generationModified IS m.generationModified)
               OR (p.error IS NOT NULL AND :retryErrors))
        ORDER BY m.sortDateMs DESC, m.id DESC
        """
    )
    suspend fun perceptualCandidates(version: Int, retryErrors: Boolean): List<PerceptualCandidate>

    @Upsert
    suspend fun upsertPerceptual(items: List<PerceptualHashEntity>)

    @Query("DELETE FROM perceptual_hashes WHERE mediaId NOT IN (SELECT id FROM media_items)")
    suspend fun deletePerceptualOrphans()

    @Query("DELETE FROM perceptual_hashes")
    suspend fun deleteAllPerceptual()

    @Query(
        """
        SELECT m.id, m.sortDateMs, m.isScreenshot, m.width, m.height,
               p.phash, p.dhash, p.d256a, p.d256b, p.d256c, p.d256d, p.lowInformation, p.sharpness,
               (SELECT f.sha256 FROM media_fingerprints f
                 WHERE f.mediaId = m.id AND f.sha256 IS NOT NULL
                   AND f.sizeBytes IS m.sizeBytes AND f.dateModifiedMs IS m.dateModifiedMs
                   AND f.generationModified IS m.generationModified) AS sha256
        FROM media_items m
        JOIN perceptual_hashes p ON p.mediaId = m.id
        WHERE m.mediaType = 'PHOTO'
          AND p.error IS NULL AND p.phash IS NOT NULL AND p.dhash IS NOT NULL
          AND p.algorithmVersion = :version
          AND p.sizeBytes IS m.sizeBytes
          AND p.dateModifiedMs IS m.dateModifiedMs
          AND p.generationModified IS m.generationModified
        """
    )
    suspend fun similarityInputs(version: Int): List<SimilarityInput>

    // --- Videos ---

    @Query(
        """
        SELECT m.id, m.contentUri, m.sizeBytes, m.dateModifiedMs, m.generationModified, m.durationMs
        FROM media_items m
        LEFT JOIN video_signatures v ON v.mediaId = m.id
        WHERE m.mediaType = 'VIDEO'
          AND (v.mediaId IS NULL
               OR v.algorithmVersion != :version
               OR NOT (v.sizeBytes IS m.sizeBytes
                       AND v.dateModifiedMs IS m.dateModifiedMs
                       AND v.generationModified IS m.generationModified)
               OR (v.error IS NOT NULL AND :retryErrors))
        ORDER BY m.sortDateMs DESC, m.id DESC
        """
    )
    suspend fun videoCandidates(version: Int, retryErrors: Boolean): List<VideoCandidate>

    @Upsert
    suspend fun upsertVideoSignatures(items: List<VideoSignatureEntity>)

    @Query("DELETE FROM video_signatures WHERE mediaId NOT IN (SELECT id FROM media_items)")
    suspend fun deleteVideoOrphans()

    @Query("DELETE FROM video_signatures")
    suspend fun deleteAllVideoSignatures()

    @Query(
        """
        SELECT m.id, m.sortDateMs, m.sizeBytes, m.width, m.height, m.orientation,
               v.durationMs, v.framePhashes, v.frameDhashes, v.lowInfoMask,
               (SELECT f.sha256 FROM media_fingerprints f
                 WHERE f.mediaId = m.id AND f.sha256 IS NOT NULL
                   AND f.sizeBytes IS m.sizeBytes AND f.dateModifiedMs IS m.dateModifiedMs
                   AND f.generationModified IS m.generationModified) AS sha256
        FROM media_items m
        JOIN video_signatures v ON v.mediaId = m.id
        WHERE m.mediaType = 'VIDEO'
          AND v.error IS NULL AND v.framePhashes IS NOT NULL AND v.frameDhashes IS NOT NULL
          AND v.durationMs IS NOT NULL
          AND v.algorithmVersion = :version
          AND v.sizeBytes IS m.sizeBytes
          AND v.dateModifiedMs IS m.dateModifiedMs
          AND v.generationModified IS m.generationModified
        """
    )
    suspend fun videoInputs(version: Int): List<VideoInput>

    /** Videos vigentes que no se pudieron analizar (formato no compatible, archivo dañado...). */
    @Query(
        """
        SELECT COUNT(*) FROM video_signatures v JOIN media_items m ON m.id = v.mediaId
        WHERE v.error IS NOT NULL
          AND v.sizeBytes IS m.sizeBytes AND v.dateModifiedMs IS m.dateModifiedMs
          AND v.generationModified IS m.generationModified
        """
    )
    fun observeVideoErrorCount(): Flow<Int>

    @Insert
    suspend fun insertGroup(group: SimilarGroupEntity): Long

    @Insert
    suspend fun insertMembers(members: List<SimilarMemberEntity>)

    @Query("DELETE FROM similar_groups WHERE kind IN (:kinds)")
    suspend fun deleteGroupsOfKinds(kinds: List<SimilarKind>)

    @Query("DELETE FROM similar_members WHERE groupId NOT IN (SELECT id FROM similar_groups)")
    suspend fun deleteOrphanMembers()

    /** Reemplaza todos los grupos de esos tipos en una sola transacción (la interfaz nunca ve un estado a medias). */
    @Transaction
    suspend fun replaceGroups(kinds: List<SimilarKind>, groups: List<NewSimilarGroup>) {
        deleteGroupsOfKinds(kinds)
        deleteOrphanMembers()
        for (g in groups) {
            val id = insertGroup(g.group)
            insertMembers(g.memberIds.map { SimilarMemberEntity(id, it) })
        }
    }

    @Query(
        """
        SELECT g.id AS groupId, g.suggestedId, g.maxDistance,
               m.id, m.mediaType, m.contentUri, m.displayName, m.sizeBytes, m.width, m.height,
               m.durationMs, m.sortDateMs, m.dateModifiedMs
        FROM similar_groups g
        JOIN similar_members sm ON sm.groupId = g.id
        JOIN media_items m ON m.id = sm.mediaId
        WHERE g.kind = :kind
        ORDER BY g.id ASC, m.sortDateMs ASC, m.id ASC
        """
    )
    fun observeGroupRows(kind: SimilarKind): Flow<List<SimilarRow>>
}
