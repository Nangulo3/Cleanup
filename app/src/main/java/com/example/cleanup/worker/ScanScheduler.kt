package com.example.cleanup.worker

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Encola escaneos y expone su estado para la interfaz. */
class ScanScheduler(context: Context) {

    private val workManager = WorkManager.getInstance(context.applicationContext)

    /**
     * Pide un escaneo. Si ya hay uno en curso o en cola se conserva ese (KEEP): lo que cambie
     * mientras tanto se recoge en el siguiente escaneo incremental.
     */
    fun enqueue(forceFull: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<ScanWorker>()
            .setInputData(workDataOf(ScanWorker.KEY_FORCE_FULL to forceFull))
            .build()
        workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    val state: Flow<ScanRunState> =
        workManager.getWorkInfosForUniqueWorkFlow(UNIQUE_WORK_NAME).map { infos ->
            val info = infos.firstOrNull() ?: return@map ScanRunState.Idle
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
