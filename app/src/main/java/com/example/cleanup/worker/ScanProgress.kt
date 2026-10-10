package com.example.cleanup.worker

import androidx.work.Data
import androidx.work.workDataOf

/** Fase actual del escaneo. Las fases de análisis (hash, similitud) se añaden en fases siguientes. */
enum class ScanPhase {
    /** Leyendo filas de MediaStore. */
    CATALOG,

    /** Comparando IDs para detectar elementos nuevos o eliminados fuera de la app. */
    RECONCILE,
}

/**
 * Progreso real del escaneo. [total] es null cuando no se conoce de antemano: la interfaz
 * muestra entonces un contador sin porcentaje.
 */
data class ScanProgressInfo(
    val phase: ScanPhase,
    val processed: Int,
    val total: Int?,
) {
    fun toData(): Data = workDataOf(
        KEY_PHASE to phase.name,
        KEY_PROCESSED to processed,
        KEY_TOTAL to (total ?: -1),
    )

    companion object {
        private const val KEY_PHASE = "phase"
        private const val KEY_PROCESSED = "processed"
        private const val KEY_TOTAL = "total"

        fun fromData(data: Data): ScanProgressInfo = ScanProgressInfo(
            phase = data.getString(KEY_PHASE)
                ?.let { name -> ScanPhase.entries.firstOrNull { it.name == name } }
                ?: ScanPhase.CATALOG,
            processed = data.getInt(KEY_PROCESSED, 0),
            total = data.getInt(KEY_TOTAL, -1).takeIf { it >= 0 },
        )
    }
}

/** Estado del trabajo de escaneo tal como lo ve la interfaz. */
sealed interface ScanRunState {
    data object Idle : ScanRunState
    data object Queued : ScanRunState
    data class Running(val progress: ScanProgressInfo) : ScanRunState
    data class Failed(val reason: ScanFailure) : ScanRunState
}

enum class ScanFailure { PERMISSION, UNEXPECTED }
