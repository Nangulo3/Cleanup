package com.example.cleanup.ui.screens.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cleanup.data.db.dao.MediaGridItem
import com.example.cleanup.data.repo.MediaRepository
import com.example.cleanup.domain.model.Category
import com.example.cleanup.domain.model.MediaSort
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class MediaGridUiState(
    val isLoading: Boolean = true,
    val sort: MediaSort = MediaSort.DATE_DESC,
    val items: List<MediaGridItem> = emptyList(),
) {
    val knownBytes: Long get() = items.sumOf { it.sizeBytes ?: 0L }
    val unknownSizeCount: Int get() = items.count { it.sizeBytes == null }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MediaGridViewModel(
    category: Category,
    repository: MediaRepository,
) : ViewModel() {

    private val sort = MutableStateFlow(MediaSort.DATE_DESC)

    // Cada vez que cambia el orden, se cambia a la consulta correspondiente (flatMapLatest).
    val uiState: StateFlow<MediaGridUiState> = sort
        .flatMapLatest { currentSort ->
            repository.observeCategoryItems(category, currentSort).map { items ->
                MediaGridUiState(isLoading = false, sort = currentSort, items = items)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MediaGridUiState())

    fun setSort(newSort: MediaSort) {
        sort.value = newSort
    }
}
