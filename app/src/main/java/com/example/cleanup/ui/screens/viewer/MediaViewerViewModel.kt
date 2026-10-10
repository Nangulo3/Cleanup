package com.example.cleanup.ui.screens.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cleanup.data.db.entity.MediaItemEntity
import com.example.cleanup.data.repo.MediaRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface MediaViewerUiState {
    data object Loading : MediaViewerUiState
    /** El elemento ya no está en el catálogo (borrado fuera de la app o sin acceso). */
    data object Missing : MediaViewerUiState
    data class Loaded(val item: MediaItemEntity) : MediaViewerUiState
}

class MediaViewerViewModel(mediaId: Long, repository: MediaRepository) : ViewModel() {
    val uiState: StateFlow<MediaViewerUiState> = repository.observeItem(mediaId)
        .map { item -> if (item == null) MediaViewerUiState.Missing else MediaViewerUiState.Loaded(item) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MediaViewerUiState.Loading)
}
