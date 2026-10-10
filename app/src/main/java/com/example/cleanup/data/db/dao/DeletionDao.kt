package com.example.cleanup.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.cleanup.data.db.entity.DeletionBatchEntity
import com.example.cleanup.data.db.entity.DeletionBatchItemEntity

@Dao
interface DeletionDao {

    @Insert
    suspend fun insertBatch(batch: DeletionBatchEntity): Long

    @Insert
    suspend fun insertBatchItems(items: List<DeletionBatchItemEntity>)

    /** Solicitudes enviadas a Android cuyo resultado aún no se verificó (más reciente primero). */
    @Query("SELECT * FROM deletion_batches WHERE completedAt IS NULL ORDER BY id DESC")
    suspend fun awaitingBatches(): List<DeletionBatchEntity>

    @Query("SELECT * FROM deletion_batch_items WHERE batchId = :batchId")
    suspend fun batchItems(batchId: Long): List<DeletionBatchItemEntity>

    @Update
    suspend fun updateBatch(batch: DeletionBatchEntity)

    /** Conserva solo las últimas [keep] solicitudes (sus elementos se borran en cascada). */
    @Query("DELETE FROM deletion_batches WHERE id NOT IN (SELECT id FROM deletion_batches ORDER BY id DESC LIMIT :keep)")
    suspend fun pruneBatches(keep: Int)

    /** Limpieza defensiva por si las claves foráneas no estuvieran activas. */
    @Query("DELETE FROM deletion_batch_items WHERE batchId NOT IN (SELECT id FROM deletion_batches)")
    suspend fun deleteOrphanBatchItems()
}
