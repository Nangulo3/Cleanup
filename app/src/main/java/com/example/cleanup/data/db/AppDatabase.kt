package com.example.cleanup.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.cleanup.data.db.dao.MediaDao
import com.example.cleanup.data.db.dao.ScanDao
import com.example.cleanup.data.db.entity.MediaItemEntity
import com.example.cleanup.data.db.entity.ScanStateEntity
import com.example.cleanup.data.db.entity.VolumeStateEntity

/**
 * Base de datos local de resultados del análisis. El esquema se exporta a app/schemas/
 * para escribir migraciones cuando se añadan tablas en fases siguientes.
 */
@Database(
    entities = [
        MediaItemEntity::class,
        VolumeStateEntity::class,
        ScanStateEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun scanDao(): ScanDao

    companion object {
        private const val NAME = "cleanup.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME).build()
    }
}
