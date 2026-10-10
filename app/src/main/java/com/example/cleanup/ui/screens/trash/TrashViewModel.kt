package com.example.cleanup.ui.screens.trash

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cleanup.data.db.entity.ActionType
import com.example.cleanup.data.db.entity.PendingDeletionEntity
import com.example.cleanup.data.repo.DuplicatesRepository
import com.example.cleanup.deletion.DeletionCoordinator
import com.example.cleanup.deletion.DeletionReport
import com.example.cleanup.deletion.TrashRepository
import com.example.cleanup.settings.AppSettings
import com.example.cleanup.settings.DeletionMode
import com.example.cleanup.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Datos del diálogo de confirmación propio de la app (antes del de Android). */
data class ConfirmDeletion(
    val count: Int,
    val knownBytes: Long,
    val unknownSizeCount: Int,
    val mode: DeletionMode,
    val exceedsLimit: Boolean,
    /** Grupos de duplicados de los que no quedaría ninguna copia. */
    val groupsLosingAllCopies: Int = 0,
)

/** Mensajes breves que se muestran como Snackbar. */
sealed interface TrashMessage {
    data class Unmarked(val count: Int) : TrashMessage
    data class Undone(val type: ActionType, val count: Int) : TrashMessage
    data object NothingToUndo : TrashMessage
    data class MissingRemoved(val count: Int) : TrashMessage
    data object RequestFailed : TrashMessage
}

data class TrashUiState(
    val isLoading: Boolean = true,
    val items: List<PendingDeletionEntity> = emptyList(),
    val selected: Set<Long> = emptySet(),
    val canUndo: Boolean = false,
    val mode: DeletionMode = DeletionMode.PERMANENT,
    val busy: Boolean = false,
    val confirm: ConfirmDeletion? = null,
    val launchRequest: IntentSender? = null,
    val report: DeletionReport? = null,
    val message: TrashMessage? = null,
) {
    private val target: List<PendingDeletionEntity>
        get() = if (selected.isEmpty()) items else items.filter { it.mediaId in selected }

    val targetCount: Int get() = target.size
    val targetKnownBytes: Long get() = target.sumOf { it.sizeBytes ?: 0L }
    val targetUnknownSizeCount: Int get() = target.count { it.sizeBytes == null }
}

/** Estado propio de la pantalla (lo demás viene de la base de datos). */
private data class LocalState(
    val selected: Set<Long> = emptySet(),
    val busy: Boolean = false,
    val confirm: ConfirmDeletion? = null,
    val launchRequest: IntentSender? = null,
    val report: DeletionReport? = null,
    val message: TrashMessage? = null,
)

class TrashViewModel(
    private val trash: TrashRepository,
    private val coordinator: DeletionCoordinator,
    settings: SettingsRepository,
    private val duplicates: DuplicatesRepository,
) : ViewModel() {

    private val local = MutableStateFlow(LocalState())

    val uiState: StateFlow<TrashUiState> = combine(
        trash.pending,
        trash.canUndo,
        settings.settings,
        local,
    ) { items, canUndo, appSettings: AppSettings, l ->
        val visibleIds = items.mapTo(HashSet()) { it.mediaId }
        TrashUiState(
            isLoading = false,
            items = items,
            selected = l.selected.filterTo(HashSet()) { it in visibleIds },
            canUndo = canUndo,
            mode = appSettings.deletionMode,
            busy = l.busy,
            confirm = l.confirm,
            launchRequest = l.launchRequest,
            report = l.report,
            message = l.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrashUiState())

    init {
        // Al abrir la papelera, se quitan los elementos que ya no existen en el teléfono.
        viewModelScope.launch {
            val removed = runCatching { coordinator.pruneMissingPending() }.getOrDefault(0)
            if (removed > 0) local.update { it.copy(message = TrashMessage.MissingRemoved(removed)) }
        }
    }

    fun toggle(mediaId: Long) = local.update {
        it.copy(selected = if (mediaId in it.selected) it.selected - mediaId else it.selected + mediaId)
    }

    fun selectAll() = local.update { state ->
        state.copy(selected = uiState.value.items.mapTo(HashSet()) { it.mediaId })
    }

    fun clearSelection() = local.update { it.copy(selected = emptySet()) }

    /** Primer paso: muestra la confirmación propia con número de archivos y tamaño estimado. */
    fun requestDelete() {
        val state = uiState.value
        if (state.targetCount == 0 || state.busy) return
        val targetIds = if (state.selected.isEmpty()) state.items.mapTo(HashSet()) { it.mediaId } else state.selected
        viewModelScope.launch {
            // Aviso si se van a eliminar TODAS las copias de algún grupo de duplicados.
            val losingAll = runCatching { duplicates.groupsLosingAllCopies(targetIds) }.getOrDefault(0)
            local.update {
                it.copy(
                    confirm = ConfirmDeletion(
                        count = state.targetCount,
                        knownBytes = state.targetKnownBytes,
                        unknownSizeCount = state.targetUnknownSizeCount,
                        mode = state.mode,
                        exceedsLimit = state.targetCount > DeletionCoordinator.MAX_PER_REQUEST,
                        groupsLosingAllCopies = losingAll,
                    )
                )
            }
        }
    }

    fun dismissConfirm() = local.update { it.copy(confirm = null) }

    /** Segundo paso: verifica, guarda la solicitud y pide a la pantalla que abra el diálogo de Android. */
    fun confirmDelete() {
        val selection = local.value.selected.takeIf { it.isNotEmpty() }
        local.update { it.copy(confirm = null, busy = true) }
        viewModelScope.launch {
            try {
                when (val result = coordinator.prepare(selection)) {
                    is DeletionCoordinator.PrepareResult.Ready ->
                        local.update { it.copy(launchRequest = result.intentSender) }
                    is DeletionCoordinator.PrepareResult.NothingToDelete ->
                        local.update { it.copy(busy = false, report = result.report) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(busy = false, message = TrashMessage.RequestFailed) }
            }
        }
    }

    /** La pantalla ya lanzó el diálogo de Android. */
    fun onLaunchHandled() = local.update { it.copy(launchRequest = null) }

    /** Respuesta del diálogo de Android: se verifica en MediaStore qué se eliminó realmente. */
    fun onSystemResult(resultCode: Int) {
        viewModelScope.launch {
            val report = runCatching { coordinator.onSystemResult(resultCode) }.getOrNull()
            local.update { it.copy(busy = false, report = report, selected = emptySet()) }
        }
    }

    fun dismissReport() = local.update { it.copy(report = null) }

    fun unmarkSelected() {
        val ids = local.value.selected
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val count = trash.unmark(ids)
            local.update { it.copy(selected = emptySet(), message = TrashMessage.Unmarked(count)) }
        }
    }

    fun undo() {
        viewModelScope.launch {
            val result = trash.undoLast()
            local.update {
                it.copy(
                    message = if (result == null) TrashMessage.NothingToUndo
                    else TrashMessage.Undone(result.type, result.itemCount)
                )
            }
        }
    }

    fun consumeMessage() = local.update { it.copy(message = null) }
}
