package com.example.cleanup.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cleanup.data.db.dao.ExtraTotals
import com.example.cleanup.data.db.dao.TrashSummary
import com.example.cleanup.data.db.entity.SimilarKind
import com.example.cleanup.data.db.dao.TypeTotals
import com.example.cleanup.data.repo.DuplicateSummary
import com.example.cleanup.data.repo.DuplicatesRepository
import com.example.cleanup.data.repo.MediaRepository
import com.example.cleanup.data.repo.SimilarRepository
import com.example.cleanup.data.repo.SimilarSummary
import com.example.cleanup.deletion.TrashRepository
import com.example.cleanup.domain.model.MediaType
import com.example.cleanup.worker.ScanRunState
import com.example.cleanup.worker.ScanMode
import com.example.cleanup.worker.ScanScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Cantidad y tamaño de un tipo de medio. */
data class TypeSummary(
    val count: Int = 0,
    val knownBytes: Long = 0,
    val unknownSizeCount: Int = 0,
)

data class HomeUiState(
    val isLoading: Boolean = true,
    /** true cuando ya terminó al menos un análisis; antes se muestran "—". */
    val hasScanned: Boolean = false,
    val photos: TypeSummary = TypeSummary(),
    val videos: TypeSummary = TypeSummary(),
    val lastScanAt: Long? = null,
    val run: ScanRunState = ScanRunState.Idle,
    val trash: TrashSummary = TrashSummary(0, 0, 0),
    val duplicates: DuplicateSummary = DuplicateSummary(),
    val similarPhotos: SimilarSummary = SimilarSummary(),
    val similarScreenshots: SimilarSummary = SimilarSummary(),
    val similarVideos: SimilarSummary = SimilarSummary(),
    val extra: ExtraTotals = ExtraTotals(),
) {
    val totalKnownBytes: Long get() = photos.knownBytes + videos.knownBytes
    val totalUnknownSizeCount: Int get() = photos.unknownSizeCount + videos.unknownSizeCount
    val isScanning: Boolean get() = run is ScanRunState.Running || run is ScanRunState.Queued
}

/** Agrupa los datos de categorías derivadas antes de mezclarlos con el estado principal. */
private data class HomeExtras(
    val similarPhotos: SimilarSummary,
    val similarScreenshots: SimilarSummary,
    val similarVideos: SimilarSummary,
    val extra: ExtraTotals,
)

class HomeViewModel(
    repository: MediaRepository,
    private val scanScheduler: ScanScheduler,
    trashRepository: TrashRepository,
    duplicatesRepository: DuplicatesRepository,
    similarRepository: SimilarRepository,
) : ViewModel() {

    // Datos de las categorías derivadas (similares, otras capturas, chat), combinados aparte
    // porque combine() admite hasta 5 flujos con tipos.
    private val extras = combine(
        similarRepository.summary(SimilarKind.PHOTO),
        similarRepository.summary(SimilarKind.SCREENSHOT),
        similarRepository.summary(SimilarKind.VIDEO),
        repository.extraTotals,
    ) { photos, screenshots, videos, extra -> HomeExtras(photos, screenshots, videos, extra) }

    val uiState: StateFlow<HomeUiState> = combine(
        repository.typeTotals,
        repository.scanState,
        scanScheduler.state,
        trashRepository.summary,
        duplicatesRepository.summary,
    ) { totals, scanState, run, trash, duplicates ->
        HomeUiState(
            isLoading = false,
            hasScanned = scanState?.lastCompletedAt != null,
            photos = totals.summaryFor(MediaType.PHOTO),
            videos = totals.summaryFor(MediaType.VIDEO),
            lastScanAt = scanState?.lastCompletedAt,
            run = run,
            trash = trash,
            duplicates = duplicates,
        )
    }.combine(extras) { state, extras ->
        state.copy(
            similarPhotos = extras.similarPhotos,
            similarScreenshots = extras.similarScreenshots,
            similarVideos = extras.similarVideos,
            extra = extras.extra,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun analyze() {
        scanScheduler.enqueue(ScanMode.MANUAL)
    }

    private fun List<TypeTotals>.summaryFor(type: MediaType): TypeSummary =
        firstOrNull { it.mediaType == type }
            ?.let { TypeSummary(it.itemCount, it.knownBytes, it.unknownSizeCount) }
            ?: TypeSummary()
}
