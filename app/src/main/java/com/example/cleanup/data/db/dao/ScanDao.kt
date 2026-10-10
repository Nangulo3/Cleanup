package com.example.cleanup.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.example.cleanup.data.db.entity.ScanStateEntity
import com.example.cleanup.data.db.entity.VolumeStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {

    @Query("SELECT * FROM volume_state")
    suspend fun volumes(): List<VolumeStateEntity>

    @Query("DELETE FROM volume_state")
    suspend fun clearVolumes()

    @Upsert
    suspend fun upsertVolumes(volumes: List<VolumeStateEntity>)

    /** Reemplaza todos los volúmenes: los que ya no existen (SD retirada) se olvidan. */
    @Transaction
    suspend fun replaceVolumes(volumes: List<VolumeStateEntity>) {
        clearVolumes()
        upsertVolumes(volumes)
    }

    @Query("SELECT * FROM scan_state WHERE id = 0")
    suspend fun scanState(): ScanStateEntity?

    @Query("SELECT * FROM scan_state WHERE id = 0")
    fun observeScanState(): Flow<ScanStateEntity?>

    @Upsert
    suspend fun upsertScanState(state: ScanStateEntity)
}
