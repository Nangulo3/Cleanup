package com.example.cleanup.worker

import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Tipos de escaneo:
 * - [AUTO]: al abrir la app o al detectar cambios. Incremental: solo lo nuevo o modificado.
 * - [MANUAL]: botón "Analizar". Relee todo el catálogo (unos segundos) y reintenta los archivos que
 *   no se pudieron leer; las huellas válidas se reutilizan. Corrige archivos que se leyeron mientras
 *   otra app todavía los estaba guardando.
 * - [FULL_RESET]: "Volver a analizar todo". Además borra las huellas y las recalcula desde cero.
 */
enum class ScanMode { AUTO, MANUAL, FULL_RESET }

/** Encola escaneos y expone su estado para la interfaz. */
class ScanScheduler(context: Context) {

    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun enqueue(mode: ScanMode) {
        val request = OneTimeWorkRequestBuilder<ScanWorker>()
            .setInputData(workDataOf(ScanWorker.KEY_MODE to mode.name))
            .build()
        // Automático: si ya hay uno en curso, basta con ese (KEEP).
        // Manual: se encadena detrás del que esté en curso para que la petición del usuario no se pierda.
        val policy = if (mode == ScanMode.AUTO) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.APPEND_OR_REPLACE
        workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, policy, request)
    }

    /**
     * Cancela el escaneo en curso o en espera y aguarda (máx. [timeoutMs]) a que WorkManager lo dé
     * por terminado. El trabajo se detiene en el siguiente punto de control; lo que alcance a
     * escribir después no hace daño porque el siguiente escaneo completo lo vuelve a conciliar.
     */
    suspend fun cancelAndWait(timeoutMs: Long = 10_000) {
        workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
        withTimeoutOrNull(timeoutMs) {
            workManager.getWorkInfosForUniqueWorkFlow(UNIQUE_WORK_NAME)
                .first { infos -> infos.all { it.state.isFinished } }
        }
    }

    val state: Flow<ScanRunState> =
        workManager.getWorkInfosForUniqueWorkFlow(UNIQUE_WORK_NAME).map { infos ->
            // Con trabajos encadenados puede haber varios: manda el que corre, luego el que espera.
            val info = infos.firstOrNull { it.state == WorkInfo.State.RUNNING }
                ?: infos.firstOrNull { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }
                ?: infos.lastOrNull()
                ?: return@map ScanRunState.Idle
            when (info.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> ScanRunState.Queued
                WorkInfo.State.RUNNING -> ScanRunState.Running(ScanProgressInfo.fromData(info.progress))
                WorkInfo.State.FAILED -> ScanRunState.Failed(
                    info.outputData.getString(ScanWorker.KEY_FAILURE)
                        ?.let { name -> ScanFailure.entries.firstOrNull { it.name == name } }
                        ?: ScanFailure.UNEXPECTED
                )
                WorkInfo.State.SUCCEEDED, WorkInfo.State.CANCELLED -> ScanRunState.Idle
            }
        }

    private companion object {
        const val UNIQUE_WORK_NAME = "media_scan"
    }
}
