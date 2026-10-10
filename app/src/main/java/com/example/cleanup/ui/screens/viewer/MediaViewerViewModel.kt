package com.example.cleanup.ui.screens.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cleanup.data.db.entity.MediaItemEntity
import com.example.cleanup.data.repo.DuplicatesRepository
import com.example.cleanup.data.repo.MediaRepository
import com.example.cleanup.deletion.MarkCandidate
import com.example.cleanup.deletion.TrashRepository
import com.example.cleanup.detection.DuplicateStatus
import com.example.cleanup.detection.DuplicateStatusClassifier
import com.example.cleanup.domain.model.DeletionSource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface MediaViewerUiState {
    data object Loading : MediaViewerUiState
    /** El elemento ya no está en el catálogo (borrado fuera de la app o sin acceso). */
    data object Missing : MediaViewerUiState
    data class Loaded(
        val item: MediaItemEntity,
        val isPending: Boolean,
        val duplicateStatus: DuplicateStatus?,
        val identicalCount: Int,
    ) : MediaViewerUiState
}

class MediaViewerViewModel(
    private val mediaId: Long,
    repository: MediaRepository,
    private val trash: TrashRepository,
    duplicates: DuplicatesRepository,
) : ViewModel() {

    val uiState: StateFlow<MediaViewerUiState> = combine(
        repository.observeItem(mediaId),
        trash.observeIsPending(mediaId),
        duplicates.observeStatus(mediaId),
    ) { item, pending, status ->
        if (item == null) {
            MediaViewerUiState.Missing
        } else {
            MediaViewerUiState.Loaded(
                item = item,
                isPending = pending,
                duplicateStatus = status?.let { DuplicateStatusClassifier.classify(it) },
                identicalCount = status?.identicalCount ?: 0,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MediaViewerUiState.Loading)

    /** Marca o desmarca para eliminar. Nunca borra el archivo. */
    fun toggleMark() {
        val state = uiState.value as? MediaViewerUiState.Loaded ?: return
        viewModelScope.launch {
            if (state.isPending) {
                trash.unmark(listOf(mediaId))
            } else {
                val item = state.item
                trash.mark(
                    listOf(
                        MarkCandidate(
                            mediaId = item.id,
                            contentUri = item.contentUri,
                            mediaType = item.mediaType,
                            displayName = item.displayName,
                            sizeBytes = item.sizeBytes,
                            sortDateMs = item.sortDateMs,
                        )
                    ),
                    DeletionSource.VIEWER,
                )
            }
        }
    }
}
