package com.example.cleanup.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.cleanup.data.db.entity.ActionItemEntity
import com.example.cleanup.data.db.entity.ActionLogEntity
import com.example.cleanup.data.db.entity.PendingDeletionEntity
import kotlinx.coroutines.flow.Flow

/** Totales de la Papelera de revisión. */
data class TrashSummary(
    val itemCount: Int,
    val knownBytes: Long,
    val unknownSizeCount: Int,
)

/** Las consultas con IN (:ids) deben recibir lotes de ≤ 500 IDs. */
@Dao
interface TrashDao {

    @Query("SELECT * FROM pending_deletions ORDER BY markedAt DESC, mediaId DESC")
    fun observePending(): Flow<List<PendingDeletionEntity>>

    @Query("SELECT mediaId FROM pending_deletions")
    fun observePendingIds(): Flow<List<Long>>

    @Query(
        """
        SELECT COUNT(*) AS itemCount,
               COALESCE(SUM(sizeBytes), 0) AS knownBytes,
               COALESCE(SUM(CASE WHEN sizeBytes IS NULL THEN 1 ELSE 0 END), 0) AS unknownSizeCount
        FROM pending_deletions
        """
    )
    fun observeSummary(): Flow<TrashSummary>

    @Query("SELECT EXISTS(SELECT 1 FROM pending_deletions WHERE mediaId = :mediaId)")
    fun observeIsPending(mediaId: Long): Flow<Boolean>

    @Query("SELECT * FROM pending_deletions ORDER BY markedAt ASC, mediaId ASC")
    suspend fun allPendingOldestFirst(): List<PendingDeletionEntity>

    @Query("SELECT * FROM pending_deletions WHERE mediaId IN (:ids)")
    suspend fun pendingByIds(ids: List<Long>): List<PendingDeletionEntity>

    @Query("SELECT mediaId FROM pending_deletions WHERE mediaId IN (:ids)")
    suspend fun existingPendingIds(ids: List<Long>): List<Long>

    /** IGNORE: si ya estaba en la cola, se conserva la marca original. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPending(items: List<PendingDeletionEntity>)

    @Query("DELETE FROM pending_deletions WHERE mediaId IN (:ids)")
    suspend fun deletePending(ids: List<Long>)

    // --- Historial para deshacer ---

    @Insert
    suspend fun insertAction(action: ActionLogEntity): Long

    @Insert
    suspend fun insertActionItems(items: List<ActionItemEntity>)

    @Query("SELECT * FROM action_log ORDER BY id DESC LIMIT 1")
    suspend fun latestAction(): ActionLogEntity?

    @Query("SELECT * FROM action_items WHERE actionId = :actionId")
    suspend fun actionItems(actionId: Long): List<ActionItemEntity>

    /** Borra la acción (sus elementos también se borran en cascada). */
    @Query("DELETE FROM action_log WHERE id = :actionId")
    suspend fun deleteAction(actionId: Long)

    @Query("DELETE FROM action_items WHERE actionId = :actionId")
    suspend fun deleteActionItems(actionId: Long)

    /** Limpieza defensiva por si las claves foráneas no estuvieran activas. */
    @Query("DELETE FROM action_items WHERE actionId NOT IN (SELECT id FROM action_log)")
    suspend fun deleteOrphanActionItems()

    @Query("SELECT COUNT(*) FROM action_log")
    fun observeActionCount(): Flow<Int>

    @Query("DELETE FROM action_log WHERE id NOT IN (SELECT id FROM action_log ORDER BY id DESC LIMIT :keep)")
    suspend fun pruneActions(keep: Int)

    @Query("DELETE FROM action_items WHERE mediaId IN (:ids)")
    suspend fun deleteActionItemsFor(ids: List<Long>)

    @Query("DELETE FROM action_log WHERE id NOT IN (SELECT DISTINCT actionId FROM action_items)")
    suspend fun deleteEmptyActions()
}
