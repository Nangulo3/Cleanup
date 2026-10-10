package com.example.cleanup.ui.screens.duplicates

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.example.cleanup.LocalAppContainer
import com.example.cleanup.R
import com.example.cleanup.data.db.dao.DuplicateRow
import com.example.cleanup.data.media.MediaThumbnail
import com.example.cleanup.detection.DuplicateGroup
import com.example.cleanup.detection.KeepReason
import com.example.cleanup.domain.model.MediaType
import com.example.cleanup.ui.components.AppIcons
import com.example.cleanup.ui.components.EmptyState
import com.example.cleanup.ui.components.LoadingState
import com.example.cleanup.ui.screens.media.VideoBadge
import com.example.cleanup.util.Formatters
import com.example.cleanup.worker.ScanRunState

/**
 * Duplicados exactos: grupos de archivos idénticos byte a byte. La app sugiere qué copia conservar
 * y el usuario puede cambiarla. "Marcar sobrantes" nunca marca la copia que se conserva.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicatesScreen(onBack: () -> Unit, onOpenItem: (Long) -> Unit, modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val viewModel: DuplicatesViewModel = viewModel {
        DuplicatesViewModel(container.duplicatesRepository, container.trashRepository, container.scanScheduler)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    MarkedMessageEffect(state, snackbarHostState, viewModel::undo, viewModel::consumeMessage)

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.category_duplicates))
                        if (state.groups.isNotEmpty()) {
                            Text(
                                stringResource(
                                    R.string.duplicates_subtitle,
                                    pluralStringResource(
                                        R.plurals.duplicate_groups_count,
                                        state.groups.size,
                                        Formatters.count(state.groups.size),
                                    ),
                                    Formatters.bytes(state.surplusBytes),
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
            if (state.markableCount > 0) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 2.dp) {
                    Button(
                        onClick = viewModel::markAllSurplus,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(16.dp)
                            .height(52.dp),
                    ) {
                        Icon(AppIcons.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            pluralStringResource(
                                R.plurals.duplicates_mark_all,
                                state.markableCount,
                                Formatters.count(state.markableCount),
                            )
                        )
                    }
                }
            }
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingState(stringResource(R.string.loading), Modifier.padding(padding))
            state.groups.isEmpty() && !state.isHashing -> EmptyState(
                icon = AppIcons.Copy,
                title = stringResource(R.string.duplicates_empty_title),
                message = stringResource(R.string.duplicates_empty_body),
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
                if (state.isHashing) {
                    item(key = "progress") { HashingBanner(state.run) }
                }
                item(key = "info") {
                    Text(
                        stringResource(R.string.duplicates_info),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(state.groups, key = { it.key }) { group ->
                    DuplicateGroupCard(
                        group = group,
                        pendingIds = state.pendingIds,
                        onSetKeeper = { id -> viewModel.setKeeper(group.key, id) },
                        onMarkSurplus = { viewModel.markGroupSurplus(group.key) },
                        onOpenItem = onOpenItem,
                    )
                }
            }
        }
    }
}

@Composable
private fun HashingBanner(run: ScanRunState) {
    val progress = (run as? ScanRunState.Running)?.progress
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (progress?.total != null) {
                    stringResource(
                        R.string.home_scan_hash,
                        Formatters.count(progress.processed),
                        Formatters.count(progress.total),
                    )
                } else {
                    stringResource(R.string.home_scan_queued)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            val total = progress?.total
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
private fun DuplicateGroupCard(
    group: DuplicateGroup,
    pendingIds: Set<Long>,
    onSetKeeper: (Long) -> Unit,
    onMarkSurplus: () -> Unit,
    onOpenItem: (Long) -> Unit,
) {
    val keeperPending = group.keeperId in pendingIds
    val markable = if (keeperPending) 0 else group.surplus.count { it.id !in pendingIds }
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(
                    R.string.duplicates_group_title,
                    Formatters.count(group.members.size),
                    Formatters.bytes(group.sizeEach),
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            if (group.members.all { it.isScreenshot }) {
                Text(
                    stringResource(R.string.duplicates_screenshot_chip),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Text(
                keepReasonText(group.reason),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                group.members.forEach { member ->
                    MemberColumn(
                        member = member,
                        isKeeper = member.id == group.keeperId,
                        isPending = member.id in pendingIds,
                        onSetKeeper = { onSetKeeper(member.id) },
                        onOpen = { onOpenItem(member.id) },
                    )
                }
            }
            if (keeperPending) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(AppIcons.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.duplicates_keeper_in_trash),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(
                onClick = onMarkSurplus,
                enabled = markable > 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (markable > 0) {
                        pluralStringResource(R.plurals.duplicates_mark_group, markable, Formatters.count(markable))
                    } else {
                        stringResource(R.string.duplicates_group_done)
                    }
                )
            }
        }
    }
}

@Composable
private fun MemberColumn(
    member: DuplicateRow,
    isKeeper: Boolean,
    isPending: Boolean,
    onSetKeeper: () -> Unit,
    onOpen: () -> Unit,
) {
    val thumbnail = remember(member.contentUri, member.dateModifiedMs) {
        MediaThumbnail(member.contentUri.toUri(), member.dateModifiedMs ?: 0L)
    }
    val unknown = stringResource(R.string.unknown)
    val date = if (member.sortDateMs > 0) Formatters.date(member.sortDateMs) else stringResource(R.string.tile_no_date)
    val folder = member.relativePath?.trimEnd('/') ?: unknown
    val stateLabel = when {
        isKeeper -> stringResource(R.string.duplicates_keep_label)
        isPending -> stringResource(R.string.tile_in_trash)
        else -> stringResource(R.string.duplicates_surplus_label)
    }
    Column(Modifier.width(132.dp)) {
        Box(
            Modifier
                .size(132.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .then(
                    if (isKeeper) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
                    else Modifier
                )
                .clickable(onClickLabel = stringResource(R.string.action_open), onClick = onOpen)
                .semantics { contentDescription = "$stateLabel. ${member.displayName ?: unknown}. $folder" },
        ) {
            AsyncImage(
                model = thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(132.dp),
            )
            if (isPending) {
                Box(Modifier.size(132.dp).background(Color.Black.copy(alpha = 0.45f)))
            }
            // Etiqueta de estado con icono: no se depende solo del color del borde.
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .background(
                        when {
                            isKeeper -> MaterialTheme.colorScheme.primary
                            isPending -> MaterialTheme.colorScheme.error
                            else -> Color.Black.copy(alpha = 0.6f)
                        },
                        CircleShape,
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    when {
                        isKeeper -> AppIcons.Check
                        isPending -> AppIcons.Delete
                        else -> AppIcons.Copy
                    },
                    contentDescription = null,
                    tint = when {
                        isKeeper -> MaterialTheme.colorScheme.onPrimary
                        isPending -> MaterialTheme.colorScheme.onError
                        else -> Color.White
                    },
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    stateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        isKeeper -> MaterialTheme.colorScheme.onPrimary
                        isPending -> MaterialTheme.colorScheme.onError
                        else -> Color.White
                    },
                    maxLines = 1,
                )
            }
            if (member.mediaType == MediaType.VIDEO) {
                VideoBadge(member.durationMs, Modifier.align(Alignment.BottomEnd).padding(4.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            member.displayName ?: unknown,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            folder,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (member.width != null && member.height != null) {
            Text(
                stringResource(R.string.dimensions_format, member.width, member.height),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!isKeeper) {
            TextButton(onClick = onSetKeeper, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Text(stringResource(R.string.duplicates_keep_this))
            }
        }
    }
}

@Composable
private fun keepReasonText(reason: KeepReason): String = stringResource(
    when (reason) {
        KeepReason.USER_CHOICE -> R.string.keep_reason_user
        KeepReason.BETTER_FOLDER -> R.string.keep_reason_folder
        KeepReason.HAS_CAPTURE_DATE -> R.string.keep_reason_capture_date
        KeepReason.OLDEST -> R.string.keep_reason_oldest
        KeepReason.STABLE_DEFAULT -> R.string.keep_reason_default
    }
)

@Composable
private fun MarkedMessageEffect(
    state: DuplicatesUiState,
    hostState: SnackbarHostState,
    onUndo: () -> Unit,
    onConsumed: () -> Unit,
) {
    val message = state.message ?: return
    val text = buildString {
        append(pluralStringResource(R.plurals.msg_marked, message.added, Formatters.count(message.added)))
        if (message.skippedGroups > 0) {
            append(". ")
            append(
                pluralStringResource(
                    R.plurals.duplicates_skipped_groups,
                    message.skippedGroups,
                    Formatters.count(message.skippedGroups),
                )
            )
        }
    }
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(message) {
        val result = hostState.showSnackbar(text, actionLabel = if (message.added > 0) undoLabel else null)
        onConsumed()
        if (result == SnackbarResult.ActionPerformed) onUndo()
    }
}
