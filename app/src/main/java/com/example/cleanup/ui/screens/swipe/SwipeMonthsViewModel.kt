package com.example.cleanup.ui.screens.swipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cleanup.data.db.dao.MonthProgress
import com.example.cleanup.data.repo.MediaRepository
import com.example.cleanup.data.repo.ReviewRepository
import com.example.cleanup.domain.review.MonthPending
import com.example.cleanup.domain.review.SwipeResume
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SwipeMonthsUiState(
    val isLoading: Boolean = true,
    val hasScanned: Boolean = false,
    val months: List<MonthProgress> = emptyList(),
    val resumeMonth: Int? = null,
) {
    val totalItems: Int get() = months.sumOf { it.total }
    val totalReviewed: Int get() = months.sumOf { it.reviewed }
}

class SwipeMonthsViewModel(
    review: ReviewRepository,
    media: MediaRepository,
) : ViewModel() {

    val uiState: StateFlow<SwipeMonthsUiState> = combine(
        review.months,
        review.lastDecisionMonth,
        media.scanState,
    ) { months, lastMonth, scanState ->
        SwipeMonthsUiState(
            isLoading = false,
            hasScanned = scanState?.lastCompletedAt != null,
            months = months,
            resumeMonth = SwipeResume.resumeMonth(months.map { MonthPending(it.monthKey, it.pending) }, lastMonth),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SwipeMonthsUiState())
}
