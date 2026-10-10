package com.example.cleanup.ui.screens.similar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cleanup.data.db.entity.SimilarKind
import com.example.cleanup.data.repo.SimilarGroup
import com.example.cleanup.data.repo.SimilarRepository
import com.example.cleanup.deletion.TrashRepository
import com.example.cleanup.settings.SettingsRepository
import com.example.cleanup.settings.SimilarityLevel
import com.example.cleanup.worker.ScanPhase
import com.example.cleanup.worker.ScanRunState
import com.example.cleanup.worker.ScanScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SimilarGroupsUiState(
    val isLoading: Boolean = true,
    val groups: List<SimilarGroup> = emptyList(),
    val pendingIds: Set<Long> = emptySet(),
    /** Selección del usuario. Empieza vacía: nada se selecciona automáticamente. */
    val selected: Set<Long> = emptySet(),
    val level: SimilarityLevel = SimilarityLevel.NORMAL,
    val run: ScanRunState = ScanRunState.Idle,
    val markedMessage: Int? = null,
    /** Solo videos: cuántos no se pudieron analizar. */
    val unreadableCount: Int = 0,
) {
    val itemCount: Int get() = groups.sumOf { it.members.size }
    val isAnalyzing: Boolean
        get() = (run as? ScanRunState.Running)?.progress?.phase.let {
            it == ScanPhase.PERCEPTUAL || it == ScanPhase.VIDEO || it == ScanPhase.GROUPING
        }
}

private data class SimilarLocal(val selected: Set<Long> = emptySet(), val markedMessage: Int? = null)

class SimilarGroupsViewModel(
    kind: SimilarKind,
    private val similar: SimilarRepository,
    private val trash: TrashRepository,
    settings: SettingsRepository,
    scheduler: ScanScheduler,
) : ViewModel() {

    private val local = MutableStateFlow(SimilarLocal())

    private val base = combine(
        similar.groups(kind),
        trash.pendingIds,
        settings.settings,
        scheduler.state,
        if (kind == SimilarKind.VIDEO) similar.videoErrorCount else flowOf(0),
    ) { groups, pending, s, run, unreadable ->
        SimilarGroupsUiState(
            isLoading = false,
            groups = groups,
            pendingIds = pending,
            level = s.similarityLevel,
            run = run,
            unreadableCount = unreadable,
        )
    }

    val uiState: StateFlow<SimilarGroupsUiState> = combine(base, local) { b, l ->
        val visible = b.groups.flatMap { g -> g.members.map { it.id } }.toHashSet()
        b.copy(selected = l.selected.filterTo(HashSet()) { it in visible && it !in b.pendingIds }, markedMessage = l.markedMessage)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SimilarGroupsUiState())

    fun toggle(id: Long) = local.update {
        it.copy(selected = if (id in it.selected) it.selected - id else it.selected + id)
    }

    /** Selecciona (sin enviar a la papelera) todas las del grupo menos la sugerida. El usuario revisa y confirma. */
    fun selectAllButSuggested(group: SimilarGroup) = local.update { state ->
        val pending = uiState.value.pendingIds
        val ids = group.members.map { it.id }.filter { it != group.suggestedId && it !in pending }
        state.copy(selected = state.selected + ids)
    }

    fun clearGroupSelection(group: SimilarGroup) = local.update { state ->
        state.copy(selected = state.selected - group.members.map { it.id }.toSet())
    }

    /** Envía a la papelera la selección actual (de todos los grupos). */
    fun markSelected() {
        val state = uiState.value
        val rows = state.groups.flatMap { it.members }.filter { it.id in state.selected }.distinctBy { it.id }
        if (rows.isEmpty()) return
        viewModelScope.launch {
            val added = similar.mark(rows)
            local.update { it.copy(selected = emptySet(), markedMessage = added) }
        }
    }

    fun undo() {
        viewModelScope.launch { trash.undoLast() }
    }

    fun consumeMessage() = local.update { it.copy(markedMessage = null) }
}
