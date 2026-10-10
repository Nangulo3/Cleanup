package com.example.cleanup.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.cleanup.data.db.entity.ReviewDecisionEntity
import com.example.cleanup.domain.model.MediaType
import kotlinx.coroutines.flow.Flow

/** Progreso de revisión de un mes (monthKey = yyyyMM; 0 = sin fecha). */
data class MonthProgress(
    val monthKey: Int,
    val total: Int,
    val reviewed: Int,
    val kept: Int,
    val marked: Int,
    val coverUri: String?,
    val coverModified: Long?,
) {
    val pending: Int get() = total - reviewed
    val isComplete: Boolean get() = total > 0 && reviewed >= total
}

/** Lo necesario para mostrar un elemento en la tarjeta de Deslizar. */
data class SwipeItem(
    val id: Long,
    val mediaType: MediaType,
    val contentUri: String,
    val displayName: String?,
    val mimeType: String?,
    val sizeBytes: Long?,
    val durationMs: Long?,
    val sortDateMs: Long,
    val dateModifiedMs: Long?,
    val width: Int?,
    val height: Int?,
    val orientation: Int?,
)

@Dao
interface ReviewDao {

    @Upsert
    suspend fun upsertDecision(decision: ReviewDecisionEntity)

    @Query("DELETE FROM review_decisions WHERE mediaId IN (:ids)")
    suspend fun deleteDecisions(ids: List<Long>)

    @Query(
        """
        SELECT m.monthKey AS monthKey,
               COUNT(*) AS total,
               COUNT(r.mediaId) AS reviewed,
               COALESCE(SUM(CASE WHEN r.decision = 'KEPT' THEN 1 ELSE 0 END), 0) AS kept,
               COALESCE(SUM(CASE WHEN r.decision = 'MARKED' THEN 1 ELSE 0 END), 0) AS marked,
               (SELECT c.contentUri FROM media_items c WHERE c.monthKey = m.monthKey
                ORDER BY c.sortDateMs DESC, c.id DESC LIMIT 1) AS coverUri,
               (SELECT c.dateModifiedMs FROM media_items c WHERE c.monthKey = m.monthKey
                ORDER BY c.sortDateMs DESC, c.id DESC LIMIT 1) AS coverModified
        FROM media_items m
        LEFT JOIN review_decisions r ON r.mediaId = m.id
        GROUP BY m.monthKey
        ORDER BY (m.monthKey = 0) ASC, m.monthKey DESC
        """
    )
    fun observeMonths(): Flow<List<MonthProgress>>

    @Query(
        """
        SELECT m.monthKey AS monthKey,
               COUNT(*) AS total,
               COUNT(r.mediaId) AS reviewed,
               COALESCE(SUM(CASE WHEN r.decision = 'KEPT' THEN 1 ELSE 0 END), 0) AS kept,
               COALESCE(SUM(CASE WHEN r.decision = 'MARKED' THEN 1 ELSE 0 END), 0) AS marked,
               NULL AS coverUri,
               NULL AS coverModified
        FROM media_items m
        LEFT JOIN review_decisions r ON r.mediaId = m.id
        WHERE m.monthKey = :monthKey
        GROUP BY m.monthKey
        """
    )
    fun observeMonth(monthKey: Int): Flow<MonthProgress?>

    /** Próximos elementos sin revisar del mes, en orden cronológico. */
    @Query(
        """
        SELECT m.id, m.mediaType, m.contentUri, m.displayName, m.mimeType, m.sizeBytes, m.durationMs,
               m.sortDateMs, m.dateModifiedMs, m.width, m.height, m.orientation
        FROM media_items m
        WHERE m.monthKey = :monthKey
          AND NOT EXISTS (SELECT 1 FROM review_decisions r WHERE r.mediaId = m.id)
        ORDER BY m.sortDateMs ASC, m.id ASC
        LIMIT :limit
        """
    )
    fun observeQueue(monthKey: Int, limit: Int): Flow<List<SwipeItem>>

    /** Mes de la última decisión tomada (para "Reanudar"). */
    @Query(
        """
        SELECT m.monthKey FROM review_decisions r
        JOIN media_items m ON m.id = r.mediaId
        ORDER BY r.decidedAt DESC
        LIMIT 1
        """
    )
    fun observeLastDecisionMonth(): Flow<Int?>

    @Query("DELETE FROM review_decisions WHERE mediaId IN (SELECT id FROM media_items WHERE monthKey = :monthKey)")
    suspend fun deleteDecisionsForMonth(monthKey: Int)

    @Query(
        """
        DELETE FROM action_items
        WHERE reviewDecision IS NOT NULL
          AND mediaId IN (SELECT id FROM media_items WHERE monthKey = :monthKey)
        """
    )
    suspend fun deleteReviewActionItemsForMonth(monthKey: Int)
}
