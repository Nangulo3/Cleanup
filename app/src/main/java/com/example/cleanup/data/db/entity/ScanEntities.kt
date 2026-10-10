package com.example.cleanup.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.cleanup.domain.model.MediaAccess

/** Versión y generación de cada volumen en el último escaneo exitoso (para escaneo incremental). */
@Entity(tableName = "volume_state")
data class VolumeStateEntity(
    @PrimaryKey val volumeName: String,
    val mediaStoreVersion: String,
    val lastGeneration: Long,
)

/** Fila única (id = 0) con el resumen del último escaneo. */
@Entity(tableName = "scan_state")
data class ScanStateEntity(
    @PrimaryKey val id: Int = 0,
    val lastStartedAt: Long? = null,
    val lastCompletedAt: Long? = null,
    val lastWasFull: Boolean = false,
    /** Nivel de acceso con el que se hizo el último escaneo; si cambia, se relee todo. */
    val lastAccess: MediaAccess? = null,
    val lastError: String? = null,
    val lastItemCount: Int = 0,
)
