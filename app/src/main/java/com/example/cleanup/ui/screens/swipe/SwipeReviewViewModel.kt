package com.example.cleanup.ui.screens.swipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cleanup.data.db.dao.MonthProgress
import com.example.cleanup.data.db.dao.SwipeItem
import com.example.cleanup.data.media.MediaStoreSource
import com.example.cleanup.data.repo.ReviewRepository
import com.example.cleanup.deletion.TrashRepository
import com.example.cleanup.domain.model.ReviewDecision
import com.example.cleanup.domain.review.MonthPending
import com.example.cleanup.domain.review.SwipeResume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SwipeMessage {
    data object NothingToUndo : SwipeMessage
    data object ItemMissing : SwipeMessage
}

data class SwipeReviewUiState(
    val isLoading: Boolean = true,
    val monthKey: Int = 0,
    val progress: MonthProgress? = null,
    /** Elemento actual (el primero sin revisar) y los siguientes, para precargar. */
    val queue: List<SwipeItem> = emptyList(),
    val pendingIds: Set<Long> = emptySet(),
    val canUndo: Boolean = false,
    val trashCount: Int = 0,
    val nextMonthWithPending: Int? = null,
    /** El elemento actual existe pero su imagen no se pudo mostrar (formato no compatible, etc.). */
    val unavailableId: Long? = null,
    val message: SwipeMessage? = null,
) {
    val current: SwipeItem? get() = queue.firstOrNull()
    val total: Int get() = progress?.total ?: 0
    val reviewed: Int get() = progress?.reviewed ?: 0
    /** Posición mostrada como "12/170". */
    val position: Int get() = (reviewed + 1).coerceAtMost(total)
    val isMonthComplete: Boolean get() = !isLoading && current == null && total > 0
    val currentIsPending: Boolean get() = current?.id?.let { it in pendingIds } == true
}

private data class SwipeLocalState(
    val unavailableId: Long? = null,
    val message: SwipeMessage? = null,
)

class SwipeReviewViewModel(
    private val monthKey: Int,
    private val review: ReviewRepository,
    private val trash: TrashRepository,
    private val source: MediaStoreSource,
) : ViewModel() {

    private val local = MutableStateFlow(SwipeLocalState())

    /** Evita registrar dos decisiones para el mismo elemento si se toca dos veces muy rápido. */
    private var decisionInFlight: Long? = null

    private val dbState = combine(
        review.observeQueue(monthKey),
        review.observeMonth(monthKey),
        review.months,
        trash.pendingIds,
    ) { queue, progress, months, pending ->
        DbSnapshot(queue, progress, months, pending)
    }

    val uiState: StateFlow<SwipeReviewUiState> = combine(
        dbState,
        trash.canUndo,
        trash.summary,
        local,
    ) { db, canUndo, trashSummary, l ->
        SwipeReviewUiState(
            isLoading = false,
            monthKey = monthKey,
            progress = db.progress,
            queue = db.queue,
            pendingIds = db.pending,
            canUndo = canUndo,
            trashCount = trashSummary.itemCount,
            nextMonthWithPending = SwipeResume.nextMonthWithPending(
                db.months.map { MonthPending(it.monthKey, it.pending) }, monthKey
            ),
            unavailableId = l.unavailableId,
            message = l.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SwipeReviewUiState(monthKey = monthKey))

    fun keep() = decide(ReviewDecision.KEPT)

    fun markForDeletion() = decide(ReviewDecision.MARKED)

    private fun decide(decision: ReviewDecision) {
        val item = uiState.value.current ?: return
        if (decisionInFlight == item.id) return
        decisionInFlight = item.id
        viewModelScope.launch {
            try {
                review.decide(item, decision)
            } finally {
                decisionInFlight = null
            }
        }
    }

    /** Deshace la última acción (normalmente la última decisión): el elemento vuelve a la tarjeta. */
    fun undo() {
        viewModelScope.launch {
            if (trash.undoLast() == null) local.update { it.copy(message = SwipeMessage.NothingToUndo) }
        }
    }

    /**
     * La imagen del elemento no cargó. Si ya no existe en el teléfono se quita del catálogo
     * (y por tanto de la cola); si existe, se marca como "no se pudo mostrar" pero se puede decidir igual.
     */
    fun onLoadError(item: SwipeItem) {
        viewModelScope.launch {
            val exists = runCatching { item.id in source.existingIds(item.mediaType, listOf(item.id)) }
                .getOrDefault(true)
            if (exists) {
                local.update { it.copy(unavailableId = item.id) }
            } else {
                trash.forgetDeleted(listOf(item.id))
                local.update { it.copy(message = SwipeMessage.ItemMissing) }
            }
        }
    }

    fun resetMonth() {
        viewModelScope.launch { review.resetMonth(monthKey) }
    }

    fun consumeMessage() = local.update { it.copy(message = null) }

    private data class DbSnapshot(
        val queue: List<SwipeItem>,
        val progress: MonthProgress?,
        val months: List<MonthProgress>,
        val pending: Set<Long>,
    )
}
