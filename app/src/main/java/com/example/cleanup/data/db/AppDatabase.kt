package com.example.cleanup.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.cleanup.data.db.dao.DeletionDao
import com.example.cleanup.data.db.dao.FingerprintDao
import com.example.cleanup.data.db.dao.MediaDao
import com.example.cleanup.data.db.dao.ReviewDao
import com.example.cleanup.data.db.dao.ScanDao
import com.example.cleanup.data.db.dao.TrashDao
import com.example.cleanup.data.db.entity.ActionItemEntity
import com.example.cleanup.data.db.entity.ActionLogEntity
import com.example.cleanup.data.db.entity.DeletionBatchEntity
import com.example.cleanup.data.db.entity.DeletionBatchItemEntity
import com.example.cleanup.data.db.entity.DuplicateKeeperEntity
import com.example.cleanup.data.db.entity.FingerprintEntity
import com.example.cleanup.data.db.entity.MediaItemEntity
import com.example.cleanup.data.db.entity.PendingDeletionEntity
import com.example.cleanup.data.db.entity.ReviewDecisionEntity
import com.example.cleanup.data.db.entity.ScanStateEntity
import com.example.cleanup.data.db.entity.VolumeStateEntity

/**
 * Base de datos local de resultados del análisis. El esquema de cada versión se exporta a
 * app/schemas/ y Room genera las migraciones automáticas a partir de esos archivos.
 *
 * Historial:
 *  1 → catálogo (media_items, volume_state, scan_state)
 *  2 → Papelera de revisión, deshacer y solicitudes de eliminación (solo tablas nuevas)
 *  3 → Deslizar: review_decisions y columnas de revisión en action_items
 *  4 → Duplicados exactos: media_fingerprints y duplicate_keepers
 */
@Database(
    entities = [
        MediaItemEntity::class,
        VolumeStateEntity::class,
        ScanStateEntity::class,
        PendingDeletionEntity::class,
        ActionLogEntity::class,
        ActionItemEntity::class,
        DeletionBatchEntity::class,
        DeletionBatchItemEntity::class,
        ReviewDecisionEntity::class,
        FingerprintEntity::class,
        DuplicateKeeperEntity::class,
    ],
    version = 4,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
    ],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun scanDao(): ScanDao
    abstract fun trashDao(): TrashDao
    abstract fun deletionDao(): DeletionDao
    abstract fun reviewDao(): ReviewDao
    abstract fun fingerprintDao(): FingerprintDao

    companion object {
        const val NAME = "cleanup.db"

        fun build(context: Context, name: String = NAME): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, name).build()
    }
}
