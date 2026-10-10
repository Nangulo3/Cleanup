package com.example.cleanup.ui.screens.trash

import androidx.compose.foundation.layout.heightIn
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.example.cleanup.LocalAppContainer
import com.example.cleanup.R
import com.example.cleanup.data.db.entity.ActionType
import com.example.cleanup.data.db.entity.PendingDeletionEntity
import com.example.cleanup.data.media.MediaThumbnail
import com.example.cleanup.deletion.DeletionCoordinator
import com.example.cleanup.deletion.DeletionReport
import com.example.cleanup.domain.model.MediaType
import com.example.cleanup.settings.DeletionMode
import com.example.cleanup.ui.components.AppIcons
import com.example.cleanup.ui.components.EmptyState
import com.example.cleanup.ui.components.LoadingState
import com.example.cleanup.ui.screens.media.VideoBadge
import com.example.cleanup.util.Formatters

/** Papelera de revisión: lista de marcas, confirmación y eliminación mediante Android. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(onOpenItem: (Long) -> Unit, modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val viewModel: TrashViewModel = viewModel {
        TrashViewModel(
            container.trashRepository,
            container.deletionCoordinator,
            container.settingsRepository,
            container.duplicatesRepository,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Diálogo de confirmación de Android (createDeleteRequest / createTrashRequest).
    val systemLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result -> viewModel.onSystemResult(result.resultCode) }

    LaunchedEffect(state.launchRequest) {
        val sender = state.launchRequest ?: return@LaunchedEffect
        viewModel.onLaunchHandled()
        systemLauncher.launch(IntentSenderRequest.Builder(sender).build())
    }

    TrashMessageEffect(state.message, snackbarHostState, onUndo = viewModel::undo, onConsumed = viewModel::consumeMessage)
    BackHandler(enabled = state.selected.isNotEmpty()) { viewModel.clearSelection() }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.selected.isEmpty()) stringResource(R.string.trash_title)
                        else pluralStringResource(
                            R.plurals.selected_count, state.selected.size, Formatters.count(state.selected.size)
                        )
                    )
                },
                navigationIcon = {
                    if (state.selected.isNotEmpty()) {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(AppIcons.Close, contentDescription = stringResource(R.string.selection_clear))
                        }
                    }
                },
                actions = {
                    if (state.items.isNotEmpty() && state.selected.size < state.items.size) {
                        TextButton(onClick = viewModel::selectAll) { Text(stringResource(R.string.selection_all)) }
                    }
                    IconButton(onClick = viewModel::undo, enabled = state.canUndo && !state.busy) {
                        Icon(AppIcons.Undo, contentDescription = stringResource(R.string.action_undo))
                    }
                },
            )
        },
        bottomBar = {
            if (state.items.isNotEmpty()) {
                TrashBottomBar(
                    state = state,
                    onDelete = viewModel::requestDelete,
                    onUnmark = viewModel::unmarkSelected,
                )
            }
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingState(stringResource(R.string.loading), Modifier.padding(padding))
            state.items.isEmpty() -> EmptyState(
                icon = AppIcons.Delete,
                title = stringResource(R.string.trash_empty_title),
                message = stringResource(R.string.trash_empty_body),
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "info") { StillOnDeviceNotice() }
                items(state.items, key = { it.mediaId }) { item ->
                    PendingRow(
                        item = item,
                        selected = item.mediaId in state.selected,
                        onToggle = { viewModel.toggle(item.mediaId) },
                        onOpen = { onOpenItem(item.mediaId) },
                    )
                }
            }
        }
    }

    state.confirm?.let { confirm ->
        ConfirmDeletionDialog(confirm, onConfirm = viewModel::confirmDelete, onDismiss = viewModel::dismissConfirm)
    }
    state.report?.let { report ->
        DeletionReportDialog(report, onDismiss = viewModel::dismissReport)
    }
}

/** Recordatorio de que la papelera interna no libera espacio. */
@Composable
private fun StillOnDeviceNotice() {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(AppIcons.Info, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.trash_still_on_device), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PendingRow(
    item: PendingDeletionEntity,
    selected: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    val thumbnail = remember(item.contentUri) { MediaThumbnail(item.contentUri.toUri(), 0L) }
    val typeLabel = stringResource(if (item.mediaType == MediaType.VIDEO) R.string.type_video else R.string.type_photo)
    val size = item.sizeBytes?.let { Formatters.bytes(it) } ?: stringResource(R.string.unknown)
    val date = if (item.sortDateMs > 0) Formatters.date(item.sortDateMs) else stringResource(R.string.tile_no_date)

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() }),
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                AsyncImage(
                    model = thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(64.dp),
                )
                if (item.mediaType == MediaType.VIDEO) {
                    VideoBadge(durationMs = null, modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.displayName ?: stringResource(R.string.viewer_item_fallback),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.trash_row_details, typeLabel, size, date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onOpen) {
                Icon(AppIcons.OpenInNew, contentDescription = stringResource(R.string.action_view))
            }
            // onCheckedChange = null: el clic lo gestiona toda la fila (mejor para TalkBack).
            Checkbox(checked = selected, onCheckedChange = null)
            Spacer(Modifier.width(8.dp))
        }
    }
}

@Composable
private fun TrashBottomBar(state: TrashUiState, onDelete: () -> Unit, onUnmark: () -> Unit) {
    val destructive = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.error,
        contentColor = MaterialTheme.colorScheme.onError,
    )
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 2.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            Text(
                stringResource(
                    R.string.trash_summary,
                    pluralStringResource(R.plurals.items_count, state.targetCount, Formatters.count(state.targetCount)),
                    estimatedSize(state.targetKnownBytes, state.targetUnknownSizeCount),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            if (state.busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.trash_waiting_system))
                }
            } else if (state.selected.isEmpty()) {
                Button(onClick = onDelete, colors = destructive, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Icon(AppIcons.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(deleteAllLabel(state.mode)))
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onUnmark, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                        Text(stringResource(R.string.trash_unmark_selected))
                    }
                    Button(onClick = onDelete, colors = destructive, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                        Text(stringResource(R.string.trash_delete_selected))
                    }
                }
            }
        }
    }
}

private fun deleteAllLabel(mode: DeletionMode): Int =
    if (mode == DeletionMode.SYSTEM_TRASH) R.string.trash_move_to_system_trash else R.string.trash_delete_permanently

/** "≈ 1,4 GB" y, si hay archivos sin tamaño, "+ N con tamaño desconocido". */
@Composable
private fun estimatedSize(knownBytes: Long, unknownCount: Int): String {
    val known = stringResource(R.string.approx_size, Formatters.bytes(knownBytes))
    if (unknownCount == 0) return known
    return known + " + " + pluralStringResource(R.plurals.unknown_size_count, unknownCount, Formatters.count(unknownCount))
}

@Composable
private fun ConfirmDeletionDialog(confirm: ConfirmDeletion, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val items = pluralStringResource(R.plurals.items_count, confirm.count, Formatters.count(confirm.count))
    val size = estimatedSize(confirm.knownBytes, confirm.unknownSizeCount)
    val trashMode = confirm.mode == DeletionMode.SYSTEM_TRASH
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(AppIcons.Warning, contentDescription = null) },
        title = {
            Text(stringResource(if (trashMode) R.string.confirm_trash_title else R.string.confirm_delete_title))
        },
        text = {
            Column {
                Text(
                    stringResource(
                        if (trashMode) R.string.confirm_trash_body else R.string.confirm_delete_body,
                        items,
                        size,
                    )
                )
                if (confirm.groupsLosingAllCopies > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        pluralStringResource(
                            R.plurals.confirm_all_copies_warning,
                            confirm.groupsLosingAllCopies,
                            Formatters.count(confirm.groupsLosingAllCopies),
                        ),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (confirm.exceedsLimit) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(
                            R.string.confirm_limit_note,
                            Formatters.count(DeletionCoordinator.MAX_PER_REQUEST),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(if (trashMode) R.string.trash_move_to_system_trash else R.string.trash_delete_permanently),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun DeletionReportDialog(report: DeletionReport, onDismiss: () -> Unit) {
    val trashMode = report.mode == DeletionMode.SYSTEM_TRASH
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.report_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(
                        if (trashMode) R.string.report_trashed else R.string.report_deleted,
                        Formatters.count(report.deleted),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (report.deleted > 0) {
                    val bytes = Formatters.bytes(report.deletedKnownBytes)
                    Text(
                        if (trashMode) stringResource(R.string.report_trash_space, bytes)
                        else stringResource(R.string.report_freed, bytes)
                    )
                }
                if (report.skippedMissing > 0) {
                    Text(stringResource(R.string.report_missing, Formatters.count(report.skippedMissing)))
                }
                if (report.skippedInaccessible > 0) {
                    Text(stringResource(R.string.report_inaccessible, Formatters.count(report.skippedInaccessible)))
                }
                if (report.failed > 0) {
                    Text(
                        stringResource(R.string.report_failed, Formatters.count(report.failed)),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (report.kept > 0) {
                    Text(
                        stringResource(
                            if (report.systemAccepted == false) R.string.report_cancelled else R.string.report_unconfirmed,
                            Formatters.count(report.kept),
                        )
                    )
                }
                if (report.remainingAfterLimit > 0) {
                    Text(
                        stringResource(
                            R.string.report_remaining,
                            Formatters.count(report.remainingAfterLimit),
                            Formatters.count(DeletionCoordinator.MAX_PER_REQUEST),
                        )
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
    )
}

/** Muestra cada mensaje como Snackbar; algunos ofrecen "Deshacer". */
@Composable
private fun TrashMessageEffect(
    message: TrashMessage?,
    hostState: SnackbarHostState,
    onUndo: () -> Unit,
    onConsumed: () -> Unit,
) {
    val text = when (message) {
        null -> null
        is TrashMessage.Unmarked ->
            pluralStringResource(R.plurals.msg_unmarked, message.count, Formatters.count(message.count))
        is TrashMessage.Undone -> when (message.type) {
            ActionType.MARK ->
                pluralStringResource(R.plurals.msg_undo_mark, message.count, Formatters.count(message.count))
            ActionType.UNMARK ->
                pluralStringResource(R.plurals.msg_undo_unmark, message.count, Formatters.count(message.count))
            ActionType.REVIEW -> stringResource(R.string.msg_undo_review)
        }
        TrashMessage.NothingToUndo -> stringResource(R.string.msg_nothing_to_undo)
        is TrashMessage.MissingRemoved ->
            pluralStringResource(R.plurals.msg_missing_removed, message.count, Formatters.count(message.count))
        TrashMessage.RequestFailed -> stringResource(R.string.msg_request_failed)
    }
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(message) {
        if (message == null || text == null) return@LaunchedEffect
        val result = hostState.showSnackbar(
            message = text,
            actionLabel = if (message is TrashMessage.Unmarked) undoLabel else null,
        )
        // Se marca como mostrado al terminar; hacerlo antes cancelaría este mismo efecto.
        onConsumed()
        if (result == SnackbarResult.ActionPerformed) onUndo()
    }
}
