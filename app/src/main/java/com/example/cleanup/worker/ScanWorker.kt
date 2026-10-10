package com.example.cleanup.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.cleanup.BuildConfig
import com.example.cleanup.CleanupApp
import com.example.cleanup.domain.model.MediaAccess
import kotlinx.coroutines.CancellationException

/**
 * Trabajo de WorkManager que ejecuta el escaneo. WorkManager lo mantiene vivo aunque el usuario
 * salga de la pantalla y lo reintenta si el sistema lo detiene; como cada lote se guarda al
 * momento, al reanudar no se pierde lo ya leído.
 */
class ScanWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as CleanupApp).container
        val access = container.mediaAccessRepository.currentAccess()
        if (access == MediaAccess.DENIED) {
            // Sin permiso MediaStore devolvería una lista vacía y la conciliación borraría
            // todo el catálogo local; por eso no se escanea.
            return Result.failure(workDataOf(KEY_FAILURE to ScanFailure.PERMISSION.name))
        }
        val mode = inputData.getString(KEY_MODE)
            ?.let { name -> ScanMode.entries.firstOrNull { it.name == name } }
            ?: ScanMode.AUTO
        val fullCatalog = mode != ScanMode.AUTO
        return try {
            // 1. Catálogo (metadatos de MediaStore).
            container.catalogScanner.run(forceFull = fullCatalog, access = access) { progress ->
                setProgress(progress.toData())
            }
            // 2. Huellas de contenido para duplicados exactos (solo lo nuevo o cambiado).
            if (mode == ScanMode.FULL_RESET) container.database.fingerprintDao().deleteAll()
            container.hashScanner.run(retryErrors = mode != ScanMode.AUTO) { progress ->
                setProgress(progress.toData())
            }
            // 3. Firmas visuales de las fotos (para similares).
            if (mode == ScanMode.FULL_RESET) container.database.similarDao().deleteAllPerceptual()
            container.perceptualScanner.run(retryErrors = mode != ScanMode.AUTO) { progress ->
                setProgress(progress.toData())
            }
            // 4. Fotogramas de los videos (para videos similares), un video a la vez.
            if (mode == ScanMode.FULL_RESET) container.database.similarDao().deleteAllVideoSignatures()
            container.videoScanner.run(retryErrors = mode != ScanMode.AUTO) { progress ->
                setProgress(progress.toData())
            }
            // 5. Agrupar similares con el nivel elegido en Ajustes.
            container.similarGrouper.run { progress -> setProgress(progress.toData()) }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            container.mediaRepository.recordScanError(ScanFailure.PERMISSION.name)
            Result.failure(workDataOf(KEY_FAILURE to ScanFailure.PERMISSION.name))
        } catch (e: Exception) {
            // En producción no se registran nombres de archivo ni URIs; solo el tipo de error.
            if (BuildConfig.DEBUG) Log.e(TAG, "Fallo en el escaneo", e)
            container.mediaRepository.recordScanError(ScanFailure.UNEXPECTED.name)
            Result.failure(workDataOf(KEY_FAILURE to ScanFailure.UNEXPECTED.name))
        }
    }

    companion object {
        const val KEY_MODE = "mode"
        const val KEY_FAILURE = "failure"
        private const val TAG = "ScanWorker"
    }
}
