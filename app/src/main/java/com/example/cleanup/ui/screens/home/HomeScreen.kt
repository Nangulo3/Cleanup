package com.example.cleanup.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cleanup.LocalAppContainer
import com.example.cleanup.R
import com.example.cleanup.domain.model.Category
import com.example.cleanup.domain.model.MediaAccess
import com.example.cleanup.ui.components.AppIcons
import com.example.cleanup.ui.components.CategoryCard
import com.example.cleanup.ui.components.NavigationRowCard
import com.example.cleanup.ui.components.PartialAccessBanner
import com.example.cleanup.ui.components.SectionHeader
import com.example.cleanup.ui.components.icon
import com.example.cleanup.ui.components.titleRes
import com.example.cleanup.util.Formatters
import com.example.cleanup.worker.ScanFailure
import com.example.cleanup.worker.ScanPhase
import com.example.cleanup.worker.ScanRunState

/** Inicio: "Tu almacenamiento". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    access: MediaAccess,
    onExpandAccess: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenCategory: (Category) -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val viewModel: HomeViewModel = viewModel {
        HomeViewModel(container.mediaRepository, container.scanScheduler)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.home_title)) }) },
    ) { padding ->
        LazyVerticalGrid(
            // Adaptativo: 2 columnas en teléfonos, más en tabletas o en horizontal.
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                StorageSummaryCard(state = state, onAnalyze = viewModel::analyze)
            }
            if (access == MediaAccess.PARTIAL) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    PartialAccessBanner(onExpandAccess = onExpandAccess)
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                NavigationRowCard(
                    title = stringResource(R.string.home_trash_card_title),
                    subtitle = stringResource(R.string.home_trash_card_empty),
                    icon = AppIcons.Delete,
                    onClick = onOpenTrash,
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(stringResource(R.string.home_categories))
            }
            items(Category.entries, key = { it.name }) { category ->
                CategoryTile(
                    category = category,
                    state = state,
                    onClick = { onOpenCategory(category) },
                )
            }
        }
    }
}

@Composable
private fun CategoryTile(category: Category, state: HomeUiState, onClick: () -> Unit) {
    val summary = when (category) {
        Category.PHOTOS -> state.photos
        Category.VIDEOS -> state.videos
        else -> null // categorías que se implementan en fases siguientes
    }
    val primary: String
    val secondary: String?
    val enabled: Boolean
    when {
        summary == null -> {
            primary = stringResource(R.string.category_not_available)
            secondary = null
            enabled = false
        }
        !state.hasScanned -> {
            primary = stringResource(R.string.category_not_scanned)
            secondary = null
            enabled = false
        }
        else -> {
            val countText = Formatters.count(summary.count)
            primary = if (category == Category.PHOTOS) {
                pluralStringResource(R.plurals.photos_count, summary.count, countText)
            } else {
                pluralStringResource(R.plurals.videos_count, summary.count, countText)
            }
            secondary = sizeText(summary.knownBytes, summary.unknownSizeCount)
            enabled = true
        }
    }
    CategoryCard(
        title = stringResource(category.titleRes()),
        icon = category.icon(),
        primaryLine = primary,
        secondaryLine = secondary,
        onClick = onClick,
        enabled = enabled,
    )
}

/** "1,4 GB" o, si hay archivos sin tamaño, "1,4 GB + 3 con tamaño desconocido". */
@Composable
private fun sizeText(knownBytes: Long, unknownCount: Int): String {
    val known = Formatters.bytes(knownBytes)
    if (unknownCount == 0) return known
    val unknown = pluralStringResource(
        R.plurals.unknown_size_count, unknownCount, Formatters.count(unknownCount)
    )
    return "$known + $unknown"
}

@Composable
private fun StorageSummaryCard(
    state: HomeUiState,
    onAnalyze: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dash = stringResource(R.string.not_available_value)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SummaryStat(
                    label = stringResource(R.string.home_photos),
                    value = if (state.hasScanned) Formatters.count(state.photos.count) else dash,
                )
                SummaryStat(
                    label = stringResource(R.string.home_videos),
                    value = if (state.hasScanned) Formatters.count(state.videos.count) else dash,
                )
                SummaryStat(
                    label = stringResource(R.string.home_total_size),
                    value = if (state.hasScanned) Formatters.bytes(state.totalKnownBytes) else dash,
                )
            }
            if (state.hasScanned && state.totalUnknownSizeCount > 0) {
                Text(
                    pluralStringResource(
                        R.plurals.unknown_size_count,
                        state.totalUnknownSizeCount,
                        Formatters.count(state.totalUnknownSizeCount),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            ScanStatus(state)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onAnalyze,
                enabled = !state.isScanning && !state.isLoading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(AppIcons.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(if (state.hasScanned) R.string.home_update else R.string.home_analyze))
            }
        }
    }
}

/** Línea de estado: progreso real si hay un escaneo, o fecha del último análisis. */
@Composable
private fun ScanStatus(state: HomeUiState) {
    val textStyle = MaterialTheme.typography.bodyMedium
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    when (val run = state.run) {
        is ScanRunState.Queued -> {
            Text(stringResource(R.string.home_scan_queued), style = textStyle, color = textColor)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        is ScanRunState.Running -> {
            val text = when (run.progress.phase) {
                ScanPhase.CATALOG -> stringResource(
                    R.string.home_scan_catalog, Formatters.count(run.progress.processed)
                )
                ScanPhase.RECONCILE -> stringResource(R.string.home_scan_reconcile)
            }
            Text(text, style = textStyle, color = textColor)
            Spacer(Modifier.height(8.dp))
            val total = run.progress.total
            if (total != null && total > 0) {
                // Solo hay barra con porcentaje cuando el total se conoce de verdad.
                LinearProgressIndicator(
                    progress = { (run.progress.processed.toFloat() / total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        is ScanRunState.Failed -> {
            val reason = when (run.reason) {
                ScanFailure.PERMISSION -> stringResource(R.string.scan_error_permission)
                ScanFailure.UNEXPECTED -> stringResource(R.string.scan_error_unexpected)
            }
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    AppIcons.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    stringResource(R.string.home_scan_failed, reason),
                    style = textStyle,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        ScanRunState.Idle -> {
            val lastScan = state.lastScanAt
            Text(
                if (lastScan != null) {
                    stringResource(R.string.home_last_scan, Formatters.dateTime(lastScan))
                } else {
                    stringResource(R.string.home_never_scanned)
                },
                style = textStyle,
                color = textColor,
            )
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.Start) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
