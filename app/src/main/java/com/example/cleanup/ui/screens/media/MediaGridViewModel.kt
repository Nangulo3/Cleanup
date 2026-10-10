package com.example.cleanup.ui.screens.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cleanup.data.db.dao.MediaGridItem
import com.example.cleanup.data.repo.MediaRepository
import com.example.cleanup.deletion.MarkCandidate
import com.example.cleanup.deletion.TrashRepository
import com.example.cleanup.domain.model.Category
import com.example.cleanup.domain.model.DeletionSource
import com.example.cleanup.domain.model.MediaSort
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Mensajes de la cuadrícula que se muestran como Snackbar. */
sealed interface GridMessage {
    data class Marked(val count: Int) : GridMessage
    data class Unmarked(val count: Int) : GridMessage
}

data class MediaGridUiState(
    val isLoading: Boolean = true,
    val sort: MediaSort = MediaSort.DATE_DESC,
    val items: List<MediaGridItem> = emptyList(),
    /** IDs que ya están en la Papelera de revisión (se marcan visualmente). */
    val pendingIds: Set<Long> = emptySet(),
    val selected: Set<Long> = emptySet(),
    val message: GridMessage? = null,
) {
    val knownBytes: Long get() = items.sumOf { it.sizeBytes ?: 0L }
    val selectionMode: Boolean get() = selected.isNotEmpty()
    val selectedPendingCount: Int get() = selected.count { it in pendingIds }
    val selectedNotPendingCount: Int get() = selected.size - selectedPendingCount
}

private data class GridLocalState(
    val sort: MediaSort = MediaSort.DATE_DESC,
    val selected: Set<Long> = emptySet(),
    val message: GridMessage? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MediaGridViewModel(
    category: Category,
    repository: MediaRepository,
    private val trash: TrashRepository,
) : ViewModel() {

    private val local = MutableStateFlow(GridLocalState())

    // Cuando cambia el orden se cambia de consulta (flatMapLatest); la selección no la reinicia.
    private val items = local
        .map { it.sort }
        .distinctUntilChanged()
        .flatMapLatest { sort -> repository.observeCategoryItems(category, sort) }

    val uiState: StateFlow<MediaGridUiState> = combine(items, trash.pendingIds, local) { list, pending, l ->
        val visibleIds = list.mapTo(HashSet()) { it.id }
        MediaGridUiState(
            isLoading = false,
            sort = l.sort,
            items = list,
            pendingIds = pending,
            selected = l.selected.filterTo(HashSet()) { it in visibleIds },
            message = l.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MediaGridUiState())

    fun setSort(newSort: MediaSort) = local.update { it.copy(sort = newSort) }

    fun toggleSelection(id: Long) = local.update {
        it.copy(selected = if (id in it.selected) it.selected - id else it.selected + id)
    }

    fun selectAll() = local.update { state ->
        state.copy(selected = uiState.value.items.mapTo(HashSet()) { it.id })
    }

    fun clearSelection() = local.update { it.copy(selected = emptySet()) }

    /** Envía la selección a la Papelera de revisión (no borra nada). */
    fun markSelected() {
        val state = uiState.value
        val candidates = state.items
            .filter { it.id in state.selected }
            .map { MarkCandidate(it.id, it.contentUri, it.mediaType, it.displayName, it.sizeBytes, it.sortDateMs) }
        if (candidates.isEmpty()) return
        viewModelScope.launch {
            val added = trash.mark(candidates, DeletionSource.CATEGORY)
            local.update { it.copy(selected = emptySet(), message = GridMessage.Marked(added)) }
        }
    }

    fun unmarkSelected() {
        val state = uiState.value
        val ids = state.selected.filter { it in state.pendingIds }
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val removed = trash.unmark(ids)
            local.update { it.copy(selected = emptySet(), message = GridMessage.Unmarked(removed)) }
        }
    }

    fun undo() {
        viewModelScope.launch { trash.undoLast() }
    }

    fun consumeMessage() = local.update { it.copy(message = null) }
}
