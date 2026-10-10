package com.example.cleanup.worker

import kotlinx.coroutines.flow.first
import com.example.cleanup.settings.SettingsRepository
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.example.cleanup.data.db.dao.ScanDao
import com.example.cleanup.data.media.MediaAccessRepository
import com.example.cleanup.domain.model.MediaAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * Mantiene el catálogo al día mientras la app está en primer plano:
 * - al volver a la app lanza un escaneo incremental;
 * - si cambia la biblioteca (foto nueva, borrado desde la galería) lo detecta con un
 *   ContentObserver y, tras 2 s sin más cambios, lanza otro.
 *
 * Solo actúa si ya se hizo al menos un análisis (el primero lo inicia el usuario) y si el
 * análisis automático está activado en Ajustes.
 */
@OptIn(FlowPreview::class)
class LibraryAutoUpdater(
    context: Context,
    private val scope: CoroutineScope,
    private val accessRepository: MediaAccessRepository,
    private val scanDao: ScanDao,
    private val scheduler: ScanScheduler,
    private val settings: SettingsRepository,
) {
    private val resolver = context.applicationContext.contentResolver
    private val changes = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private var registered = false

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            changes.tryEmit(Unit)
        }
    }

    init {
        scope.launch {
            changes.debounce(CHANGE_DEBOUNCE_MS).collect { updateIfScannedBefore() }
        }
    }

    /** Llamar desde onStart de la actividad. */
    fun onForeground() {
        if (!registered) {
            val external = MediaStore.VOLUME_EXTERNAL
            resolver.registerContentObserver(MediaStore.Images.Media.getContentUri(external), true, observer)
            resolver.registerContentObserver(MediaStore.Video.Media.getContentUri(external), true, observer)
            registered = true
        }
        scope.launch { updateIfScannedBefore() }
    }

    /** Llamar desde onStop de la actividad. */
    fun onBackground() {
        if (registered) {
            resolver.unregisterContentObserver(observer)
            registered = false
        }
    }

    private suspend fun updateIfScannedBefore() {
        accessRepository.refresh()
        if (accessRepository.access.value == MediaAccess.DENIED) return
        if (scanDao.scanState()?.lastCompletedAt == null) return
        if (!settings.settings.first().autoScan) return
        scheduler.enqueue(ScanMode.AUTO)
    }

    private companion object {
        const val CHANGE_DEBOUNCE_MS = 2_000L
    }
}
