package com.example.cleanup.data.repo

import com.example.cleanup.data.db.AppDatabase
import com.example.cleanup.worker.ScanMode
import com.example.cleanup.worker.ScanScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * "Borrar datos de la app" en Ajustes: deja la base de datos local como recién instalada y
 * vuelve a analizar desde cero.
 *
 * Borra: catálogo, huellas y firmas, grupos, copias elegidas, papelera de revisión (los archivos
 * vuelven a quedar sin marcar), progreso de Deslizar e historial para deshacer.
 * No toca: fotos y videos del teléfono (nunca se llama a MediaStore aquí) ni los ajustes.
 */
class AppDataCleaner(
    private val db: AppDatabase,
    private val scheduler: ScanScheduler,
) {
    suspend fun clearAll() {
        // Primero se detiene el escaneo para que no siga escribiendo mientras se vacía la base.
        scheduler.cancelAndWait()
        // clearAllTables() es bloqueante: nunca en el hilo principal.
        withContext(Dispatchers.IO) { db.clearAllTables() }
        scheduler.enqueue(ScanMode.FULL_RESET)
    }
}
