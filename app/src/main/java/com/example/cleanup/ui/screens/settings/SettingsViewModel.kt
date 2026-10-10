package com.example.cleanup.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cleanup.settings.AppSettings
import com.example.cleanup.settings.DeletionMode
import com.example.cleanup.settings.SettingsRepository
import com.example.cleanup.settings.SimilarityLevel
import com.example.cleanup.settings.ThemeMode
import com.example.cleanup.worker.ScanMode
import com.example.cleanup.worker.ScanScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Expone las preferencias como estado observable y guarda los cambios en segundo plano. */
class SettingsViewModel(
    private val repository: SettingsRepository,
    private val scanScheduler: ScanScheduler,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings(),
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setDeletionMode(mode: DeletionMode) {
        viewModelScope.launch { repository.setDeletionMode(mode) }
    }

    /** Cambia el nivel de similitud y vuelve a agrupar (las firmas ya calculadas se reutilizan). */
    fun setSimilarityLevel(level: SimilarityLevel) {
        viewModelScope.launch {
            repository.setSimilarityLevel(level)
            scanScheduler.enqueue(ScanMode.MANUAL)
        }
    }

    /** Recalcula todo desde cero (catálogo y huellas). No borra fotos ni cambia la papelera. */
    fun reanalyzeAll() {
        scanScheduler.enqueue(ScanMode.FULL_RESET)
    }
}
