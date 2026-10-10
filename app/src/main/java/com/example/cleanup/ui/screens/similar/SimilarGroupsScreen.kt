package com.example.cleanup.ui.screens.similar

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.example.cleanup.LocalAppContainer
import com.example.cleanup.R
import com.example.cleanup.data.db.dao.SimilarRow
import com.example.cleanup.data.db.entity.SimilarKind
import com.example.cleanup.data.media.MediaThumbnail
import com.example.cleanup.data.repo.SimilarGroup
import com.example.cleanup.domain.model.MediaType
import com.example.cleanup.settings.SimilarityLevel
import com.example.cleanup.ui.components.AppIcons
import com.example.cleanup.ui.components.EmptyState
import com.example.cleanup.ui.components.LoadingState
import com.example.cleanup.ui.screens.media.VideoBadge
import com.example.cleanup.util.Formatters
import com.example.cleanup.worker.ScanPhase
import com.example.cleanup.worker.ScanRunState

/**
 * Fotos o capturas parecidas. No son copias idénticas: el usuario elige cuáles eliminar.
 * Nada está seleccionado al entrar; la sugerencia (la más nítida) es solo una ayuda.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimilarGroupsScreen(
    kind: SimilarKind,
    onBack: () -> Unit,
    onOpenItem: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val viewModel: SimilarGroupsViewModel = viewModel {
        SimilarGroupsViewModel(
            kind,
            container.similarRepository,
            container.trashRepository,
            container.settingsRepository,
            container.scanScheduler,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val title = stringResource(
        when (kind) {
            SimilarKind.SCREENSHOT -> R.string.category_similar_screenshots
            SimilarKind.VIDEO -> R.string.category_similar_videos
            SimilarKind.PHOTO -> R.string.category_similar_photos
        }
    )
    val isVideo = kind == SimilarKind.VIDEO
    // Los videos usan textos en masculino ("el sugerido") y las fotos en femenino ("la sugerida").
    val suggestedLabel = stringResource(if (isVideo) R.string.similar_suggested_video else R.string.similar_suggested)
    val selectAllLabel = stringResource(
        if (isVideo) R.string.similar_select_all_but_suggested_video else R.string.similar_select_all_but_suggested
    )

    val marked = state.markedMessage
    val markedText = marked?.let { pluralStringResource(R.plurals.msg_marked, it, Formatters.count(it)) }
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(marked) {
        if (marked != null && markedText != null) {
            val result = snackbarHostState.showSnackbar(markedText, actionLabel = if (marked > 0) undoLabel else null)
            viewModel.consumeMessage()
            if (result == SnackbarResult.ActionPerformed) viewModel.undo()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title)
                        if (state.groups.isNotEmpty()) {
                            Text(
                                stringResource(
                                    R.string.similar_subtitle,
                                    pluralStringResource(R.plurals.duplicate_groups_count, state.groups.size, Formatters.count(state.groups.size)),
                                    pluralStringResource(R.plurals.items_count, state.itemCount, Formatters.count(state.itemCount)),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        bottomBar = {
            if (state.selected.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 2.dp) {
                    Button(
                        onClick = viewModel::markSelected,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(16.dp)
                            .heightIn(min = 52.dp),
                    ) {
                        Icon(AppIcons.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            pluralStringResource(
                                if (isVideo) R.plurals.similar_send_selected_video else R.plurals.similar_send_selected,
                                state.selected.size,
                                Formatters.count(state.selected.size),
                            )
                        )
                    }
                }
            }
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingState(stringResource(R.string.loading), Modifier.padding(padding))
            state.groups.isEmpty() && !state.isAnalyzing -> EmptyState(
                icon = when (kind) {
                    SimilarKind.SCREENSHOT -> AppIcons.Screenshot
                    SimilarKind.VIDEO -> AppIcons.VideoLibrary
                    SimilarKind.PHOTO -> AppIcons.PhotoLibrary
                },
                title = stringResource(if (kind == SimilarKind.VIDEO) R.string.similar_videos_empty_title else R.string.similar_empty_title),
                message = buildString {
                    append(stringResource(R.string.similar_empty_body, levelLabel(state.level)))
                    if (state.unreadableCount > 0) {
                        append("\n\n")
                        append(pluralStringResource(R.plurals.videos_unreadable, state.unreadableCount, Formatters.count(state.unreadableCount)))
                    }
                },
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.isAnalyzing) {
                    item(key = "progress") { AnalyzingBanner(state.run) }
                }
                item(key = "info") {
                    Text(
                        stringResource(
                            if (kind == SimilarKind.VIDEO) R.string.similar_videos_info else R.string.similar_info,
                            levelLabel(state.level),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.unreadableCount > 0) {
                    item(key = "unreadable") {
                        Text(
                            pluralStringResource(R.plurals.videos_unreadable, state.unreadableCount, Formatters.count(state.unreadableCount)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
                items(state.groups, key = { it.id }) { group ->
                    SimilarGroupCard(
                        group = group,
                        suggestedLabel = suggestedLabel,
                        selectAllLabel = selectAllLabel,
                        selected = state.selected,
                        pendingIds = state.pendingIds,
                        onToggle = viewModel::toggle,
                        onSelectAllButSuggested = { viewModel.selectAllButSuggested(group) },
                        onClearSelection = { viewModel.clearGroupSelection(group) },
                        onOpenItem = onOpenItem,
                    )
                }
            }
        }
    }
}

@Composable
private fun AnalyzingBanner(run: ScanRunState) {
    val progress = (run as? ScanRunState.Running)?.progress
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp)) {
            val total = progress?.total
            Text(
                when {
                    progress != null && total != null && progress.phase == ScanPhase.VIDEO ->
                        stringResource(R.string.home_scan_video, Formatters.count(progress.processed), Formatters.count(total))
                    progress != null && total != null ->
                        stringResource(R.string.home_scan_perceptual, Formatters.count(progress.processed), Formatters.count(total))
                    else -> stringResource(R.string.home_scan_grouping)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            if (progress != null && total != null && total > 0) {
                LinearProgressIndicator(
                    progress = { (progress.processed.toFloat() / total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SimilarGroupCard(
    group: SimilarGroup,
    suggestedLabel: String,
    selectAllLabel: String,
    selected: Set<Long>,
    pendingIds: Set<Long>,
    onToggle: (Long) -> Unit,
    onSelectAllButSuggested: () -> Unit,
    onClearSelection: () -> Unit,
    onOpenItem: (Long) -> Unit,
) {
    val first = group.members.minOf { it.sortDateMs }
    val last = group.members.maxOf { it.sortDateMs }
    val dateText = when {
        first <= 0 -> stringResource(R.string.tile_no_date)
        Formatters.date(first) == Formatters.date(last) -> Formatters.date(first)
        else -> stringResource(R.string.similar_date_range, Formatters.date(first), Formatters.date(last))
    }
    val anySelected = group.members.any { it.id in selected }
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(
                    R.string.similar_group_title,
                    pluralStringResource(R.plurals.items_count, group.members.size, Formatters.count(group.members.size)),
                    dateText,
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.similar_group_size, Formatters.bytes(group.totalBytes)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                group.members.forEach { member ->
                    SimilarTile(
                        member = member,
                        suggestedLabel = suggestedLabel,
                        isSuggested = member.id == group.suggestedId,
                        isSelected = member.id in selected,
                        isPending = member.id in pendingIds,
                        onToggle = { onToggle(member.id) },
                        onOpen = { onOpenItem(member.id) },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row {
                if (group.suggestedId != null) {
                    TextButton(onClick = onSelectAllButSuggested) {
                        Text(selectAllLabel)
                    }
                }
                if (anySelected) {
                    TextButton(onClick = onClearSelection) { Text(stringResource(R.string.similar_clear_selection)) }
                }
            }
        }
    }
}

@Composable
private fun SimilarTile(
    member: SimilarRow,
    suggestedLabel: String,
    isSuggested: Boolean,
    isSelected: Boolean,
    isPending: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    val thumbnail = remember(member.contentUri, member.dateModifiedMs) {
        MediaThumbnail(member.contentUri.toUri(), member.dateModifiedMs ?: 0L)
    }
    val primary = MaterialTheme.colorScheme.primary
    val stateText = buildList {
        if (isSuggested) add(suggestedLabel)
        if (isPending) add(stringResource(R.string.tile_in_trash))
    }.joinToString(", ")
    Column(Modifier.width(120.dp)) {
        Box(
            Modifier
                .size(120.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .then(if (isSelected) Modifier.border(3.dp, primary, MaterialTheme.shapes.medium) else Modifier)
                .toggleable(value = isSelected, enabled = !isPending, role = Role.Checkbox, onValueChange = { onToggle() })
                .semantics {
                    contentDescription = listOfNotNull(
                        member.displayName,
                        member.sizeBytes?.let { Formatters.bytes(it) },
                        stateText.takeIf { it.isNotEmpty() },
                    ).joinToString(". ")
                },
        ) {
            AsyncImage(
                model = thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(120.dp),
            )
            if (isPending) {
                Box(Modifier.size(120.dp).background(Color.Black.copy(alpha = 0.45f)))
                Icon(
                    AppIcons.Delete,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.Center).size(28.dp),
                )
            }
            // Casilla de selección visible (no solo el borde de color).
            val corner = Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp)
            if (isSelected) {
                Box(corner.background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(AppIcons.CheckCircle, contentDescription = null, tint = primary, modifier = Modifier.size(24.dp))
                }
            } else if (!isPending) {
                Box(corner.border(2.dp, Color.White, CircleShape).background(Color.Black.copy(alpha = 0.25f), CircleShape))
            }
            if (isSuggested) {
                Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .background(primary, CircleShape)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AppIcons.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        suggestedLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
            if (member.mediaType == MediaType.VIDEO) {
                VideoBadge(member.durationMs, Modifier.align(Alignment.BottomEnd).padding(6.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                member.sizeBytes?.let { Formatters.bytes(it) } ?: stringResource(R.string.unknown),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onOpen) { // 48 dp: tamaño táctil mínimo
                Icon(AppIcons.OpenInNew, contentDescription = stringResource(R.string.action_view), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun levelLabel(level: SimilarityLevel): String = stringResource(
    when (level) {
        SimilarityLevel.STRICT -> R.string.similarity_strict
        SimilarityLevel.NORMAL -> R.string.similarity_normal
        SimilarityLevel.BROAD -> R.string.similarity_broad
    }
)
