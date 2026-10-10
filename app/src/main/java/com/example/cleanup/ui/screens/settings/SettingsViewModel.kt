package com.example.cleanup.ui.screens.settings

import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import com.example.cleanup.data.repo.AppDataCleaner
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

/** Estado de "Borrar datos de la app". */
enum class ClearDataState { IDLE, RUNNING, DONE, FAILED }

/** Expone las preferencias como estado observable y guarda los cambios en segundo plano. */
class SettingsViewModel(
    private val repository: SettingsRepository,
    private val scanScheduler: ScanScheduler,
    private val cleaner: AppDataCleaner,
    /** Ámbito de la app: el borrado termina aunque el usuario salga de Ajustes. */
    private val appScope: CoroutineScope,
) : ViewModel() {

    private val _clearState = MutableStateFlow(ClearDataState.IDLE)
    val clearState: StateFlow<ClearDataState> = _clearState.asStateFlow()

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

    fun setAutoScan(enabled: Boolean) {
        viewModelScope.launch { repository.setAutoScan(enabled) }
    }

    /** Recalcula todo desde cero (catálogo y huellas). No borra fotos ni cambia la papelera. */
    fun reanalyzeAll() {
        scanScheduler.enqueue(ScanMode.FULL_RESET)
    }

    /** Borra todos los datos locales (no los archivos) y vuelve a analizar. */
    fun clearAllData() {
        if (_clearState.value == ClearDataState.RUNNING) return
        _clearState.value = ClearDataState.RUNNING
        appScope.launch {
            _clearState.value = try {
                cleaner.clearAll()
                ClearDataState.DONE
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ClearDataState.FAILED
            }
        }
    }
}
