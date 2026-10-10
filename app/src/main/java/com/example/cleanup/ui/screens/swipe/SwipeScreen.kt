package com.example.cleanup.ui.screens.swipe

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.example.cleanup.LocalAppContainer
import com.example.cleanup.R
import com.example.cleanup.data.db.dao.MonthProgress
import com.example.cleanup.data.media.MediaThumbnail
import com.example.cleanup.ui.components.AppIcons
import com.example.cleanup.ui.components.EmptyState
import com.example.cleanup.ui.components.LoadingState
import com.example.cleanup.util.Formatters

/** Deslizar: lista de meses con su progreso de revisión. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeScreen(onOpenMonth: (Int) -> Unit, modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val viewModel: SwipeMonthsViewModel = viewModel {
        SwipeMonthsViewModel(container.reviewRepository, container.mediaRepository)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.swipe_title)) }) },
    ) { padding ->
        when {
            state.isLoading -> LoadingState(stringResource(R.string.loading), Modifier.padding(padding))
            !state.hasScanned || state.months.isEmpty() -> EmptyState(
                icon = AppIcons.Swipe,
                title = stringResource(R.string.swipe_empty_title),
                message = stringResource(R.string.swipe_empty_body),
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "summary") {
                    OverallProgressCard(state = state, onResume = { state.resumeMonth?.let(onOpenMonth) })
                }
                items(state.months, key = { it.monthKey }) { month ->
                    MonthRow(month = month, onClick = { onOpenMonth(month.monthKey) })
                }
            }
        }
    }
}

@Composable
private fun OverallProgressCard(state: SwipeMonthsUiState, onResume: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                stringResource(
                    R.string.swipe_total_progress,
                    Formatters.count(state.totalReviewed),
                    Formatters.count(state.totalItems),
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = {
                    if (state.totalItems == 0) 0f else state.totalReviewed.toFloat() / state.totalItems
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            val resume = state.resumeMonth
            if (resume != null) {
                Button(onClick = onResume, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Icon(AppIcons.Swipe, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.swipe_resume, monthLabel(resume)))
                }
            } else {
                Text(
                    stringResource(R.string.swipe_all_done),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MonthRow(month: MonthProgress, onClick: () -> Unit) {
    val label = monthLabel(month.monthKey)
    val status = if (month.isComplete) {
        stringResource(R.string.swipe_month_done, Formatters.count(month.total))
    } else {
        stringResource(R.string.swipe_month_pending, Formatters.count(month.pending), Formatters.count(month.total))
    }
    val cover = remember(month.coverUri, month.coverModified) {
        month.coverUri?.let { MediaThumbnail(it.toUri(), month.coverModified ?: 0L) }
    }
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "$label. $status" },
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                if (cover != null) {
                    AsyncImage(
                        model = cover,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(64.dp),
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text(
                    status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { if (month.total == 0) 0f else month.reviewed.toFloat() / month.total },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(12.dp))
            Icon(
                if (month.isComplete) AppIcons.CheckCircle else AppIcons.ChevronRight,
                contentDescription = null,
                tint = if (month.isComplete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "Octubre 2026" o "Sin fecha". */
@Composable
fun monthLabel(monthKey: Int): String =
    Formatters.month(monthKey) ?: stringResource(R.string.swipe_no_date)
