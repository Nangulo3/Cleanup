package com.example.cleanup.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.cleanup.data.db.entity.MediaItemEntity
import com.example.cleanup.domain.model.MediaType
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {

    /** Inserta o actualiza un lote dentro de una sola transacción. */
    @Upsert
    suspend fun upsertAll(items: List<MediaItemEntity>)

    @Query("SELECT id FROM media_items")
    suspend fun allIds(): List<Long>

    /** Llamar con lotes de ≤ 500 IDs (límite de variables de SQLite en Android 11). */
    @Query("DELETE FROM media_items WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>): Int

    @Query("SELECT COUNT(*) FROM media_items")
    suspend fun count(): Int

    @Query(
        """
        SELECT mediaType,
               COUNT(*) AS itemCount,
               COALESCE(SUM(sizeBytes), 0) AS knownBytes,
               SUM(CASE WHEN sizeBytes IS NULL THEN 1 ELSE 0 END) AS unknownSizeCount
        FROM media_items
        GROUP BY mediaType
        """
    )
    fun observeTypeTotals(): Flow<List<TypeTotals>>

    /** [sort] es [com.example.cleanup.domain.model.MediaSort.sqlCode]. */
    @Query(
        """
        SELECT id, mediaType, contentUri, displayName, sizeBytes, durationMs, sortDateMs, dateModifiedMs
        FROM media_items
        WHERE mediaType = :type
        ORDER BY
            CASE WHEN :sort = 0 THEN sortDateMs END DESC,
            CASE WHEN :sort = 1 THEN sortDateMs END ASC,
            CASE WHEN :sort = 2 THEN sizeBytes END DESC,
            CASE WHEN :sort = 3 THEN displayName END COLLATE NOCASE ASC,
            id DESC
        """
    )
    fun observeGridItems(type: MediaType, sort: Int): Flow<List<MediaGridItem>>

    @Query("SELECT * FROM media_items WHERE id = :id")
    fun observeItem(id: Long): Flow<MediaItemEntity?>
}
