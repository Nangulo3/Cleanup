package com.example.cleanup.ui.screens.duplicates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cleanup.data.repo.DuplicatesRepository
import com.example.cleanup.data.repo.MarkSurplusResult
import com.example.cleanup.deletion.TrashRepository
import com.example.cleanup.detection.DuplicateGroup
import com.example.cleanup.worker.ScanPhase
import com.example.cleanup.worker.ScanRunState
import com.example.cleanup.worker.ScanScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DuplicatesUiState(
    val isLoading: Boolean = true,
    val groups: List<DuplicateGroup> = emptyList(),
    val pendingIds: Set<Long> = emptySet(),
    val run: ScanRunState = ScanRunState.Idle,
    val message: MarkSurplusResult? = null,
) {
    val surplusBytes: Long get() = groups.sumOf { it.surplusBytes }

    /** Copias que "Marcar todos los sobrantes" añadiría ahora mismo. */
    val markableCount: Int
        get() = groups.filter { it.keeperId !in pendingIds }
            .sumOf { group -> group.surplus.count { it.id !in pendingIds } }

    /** El escaneo está calculando huellas: los resultados pueden crecer. */
    val isHashing: Boolean
        get() = (run as? ScanRunState.Running)?.progress?.phase.let {
            it == ScanPhase.HASH_QUICK || it == ScanPhase.HASH_FULL
        } || run is ScanRunState.Queued
}

class DuplicatesViewModel(
    private val duplicates: DuplicatesRepository,
    private val trash: TrashRepository,
    scheduler: ScanScheduler,
) : ViewModel() {

    private val message = MutableStateFlow<MarkSurplusResult?>(null)

    val uiState: StateFlow<DuplicatesUiState> = combine(
        duplicates.groups,
        trash.pendingIds,
        scheduler.state,
        message,
    ) { groups, pending, run, msg ->
        DuplicatesUiState(isLoading = false, groups = groups, pendingIds = pending, run = run, message = msg)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DuplicatesUiState())

    fun setKeeper(groupKey: String, mediaId: Long) {
        viewModelScope.launch { duplicates.setKeeper(groupKey, mediaId) }
    }

    fun markGroupSurplus(groupKey: String) {
        viewModelScope.launch { message.value = duplicates.markSurplus(setOf(groupKey)) }
    }

    fun markAllSurplus() {
        viewModelScope.launch { message.value = duplicates.markSurplus(null) }
    }

    fun undo() {
        viewModelScope.launch { trash.undoLast() }
    }

    fun consumeMessage() = message.update { null }
}
