package com.example.cleanup.ui.screens.media

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cleanup.LocalAppContainer
import com.example.cleanup.R
import com.example.cleanup.domain.model.Category
import com.example.cleanup.domain.model.MediaSort
import com.example.cleanup.ui.components.AppIcons
import com.example.cleanup.ui.components.EmptyState
import com.example.cleanup.ui.components.LoadingState
import com.example.cleanup.ui.components.icon
import com.example.cleanup.ui.components.titleRes
import com.example.cleanup.util.Formatters

/**
 * Cuadrícula de una categoría plana (Fotos, Videos).
 * Toque: abre el visor. Toque largo: entra en modo selección para enviar a la papelera.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaGridScreen(
    category: Category,
    onBack: () -> Unit,
    onOpenItem: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val viewModel: MediaGridViewModel = viewModel {
        MediaGridViewModel(category, container.mediaRepository, container.trashRepository)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var sortMenuOpen by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler(enabled = state.selectionMode) { viewModel.clearSelection() }
    GridMessageEffect(state.message, snackbarHostState, viewModel::undo, viewModel::consumeMessage)

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (state.selectionMode) {
                TopAppBar(
                    title = {
                        Text(
                            pluralStringResource(
                                R.plurals.selected_count, state.selected.size, Formatters.count(state.selected.size)
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(AppIcons.Close, contentDescription = stringResource(R.string.selection_clear))
                        }
                    },
                    actions = {
                        if (state.selected.size < state.items.size) {
                            TextButton(onClick = viewModel::selectAll) { Text(stringResource(R.string.selection_all)) }
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(category.titleRes()))
                            if (!state.isLoading) {
                                Text(
                                    stringResource(
                                        R.string.grid_subtitle,
                                        pluralStringResource(
                                            R.plurals.items_count,
                                            state.items.size,
                                            Formatters.count(state.items.size),
                                        ),
                                        Formatters.bytes(state.knownBytes),
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
                    actions = {
                        Box {
                            IconButton(onClick = { sortMenuOpen = true }) {
                                Icon(AppIcons.Sort, contentDescription = stringResource(R.string.sort))
                            }
                            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                                MediaSort.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(option.labelRes())) },
                                        onClick = {
                                            viewModel.setSort(option)
                                            sortMenuOpen = false
                                        },
                                        leadingIcon = {
                                            if (option == state.sort) {
                                                Icon(AppIcons.Check, contentDescription = null)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (state.selectionMode) {
                SelectionBottomBar(
                    state = state,
                    onMark = viewModel::markSelected,
                    onUnmark = viewModel::unmarkSelected,
                )
            }
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingState(
                message = stringResource(R.string.loading),
                modifier = Modifier.padding(padding),
            )

            state.items.isEmpty() -> EmptyState(
                icon = category.icon(),
                title = stringResource(R.string.grid_empty_title),
                message = stringResource(
                    when (category) {
                        Category.VIDEOS -> R.string.grid_empty_videos
                        Category.CHAT_PHOTOS -> R.string.grid_empty_chat
                        Category.OTHER_SCREENSHOTS -> R.string.grid_empty_screenshots
                        else -> R.string.grid_empty_photos
                    }
                ),
                modifier = Modifier.padding(padding),
            )

            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
                contentPadding = PaddingValues(
                    start = 2.dp,
                    end = 2.dp,
                    top = padding.calculateTopPadding() + 2.dp,
                    bottom = padding.calculateBottomPadding() + 2.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                categoryNoteRes(category)?.let { note ->
                    item(key = "note", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            stringResource(note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
                items(
                    items = state.items,
                    key = { it.id },
                    contentType = { it.mediaType },
                ) { item ->
                    MediaTile(
                        item = item,
                        selected = item.id in state.selected,
                        pending = item.id in state.pendingIds,
                        selectionMode = state.selectionMode,
                        onClick = {
                            if (state.selectionMode) viewModel.toggleSelection(item.id) else onOpenItem(item.id)
                        },
                        onLongClick = { viewModel.toggleSelection(item.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionBottomBar(state: MediaGridUiState, onMark: () -> Unit, onUnmark: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 2.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.selectedPendingCount > 0) {
                OutlinedButton(onClick = onUnmark, modifier = Modifier.weight(1f).height(52.dp)) {
                    Text(stringResource(R.string.trash_unmark_selected))
                }
            }
            if (state.selectedNotPendingCount > 0) {
                Button(onClick = onMark, modifier = Modifier.weight(1f).height(52.dp)) {
                    Icon(AppIcons.Delete, contentDescription = null)
                    Text(stringResource(R.string.grid_send_to_trash), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

/** Snackbar tras marcar o desmarcar, con "Deshacer". */
@Composable
private fun GridMessageEffect(
    message: GridMessage?,
    hostState: SnackbarHostState,
    onUndo: () -> Unit,
    onConsumed: () -> Unit,
) {
    val text = when (message) {
        null -> null
        is GridMessage.Marked ->
            pluralStringResource(R.plurals.msg_marked, message.count, Formatters.count(message.count))
        is GridMessage.Unmarked ->
            pluralStringResource(R.plurals.msg_unmarked, message.count, Formatters.count(message.count))
    }
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(message) {
        if (message == null || text == null) return@LaunchedEffect
        val count = when (message) {
            is GridMessage.Marked -> message.count
            is GridMessage.Unmarked -> message.count
        }
        val result = hostState.showSnackbar(message = text, actionLabel = if (count > 0) undoLabel else null)
        onConsumed()
        if (result == SnackbarResult.ActionPerformed) onUndo()
    }
}

/** Nota explicativa para categorías inferidas. */
private fun categoryNoteRes(category: Category): Int? = when (category) {
    Category.CHAT_PHOTOS -> R.string.grid_note_chat
    Category.OTHER_SCREENSHOTS -> R.string.grid_note_other_screenshots
    else -> null
}

private fun MediaSort.labelRes(): Int = when (this) {
    MediaSort.DATE_DESC -> R.string.sort_date_desc
    MediaSort.DATE_ASC -> R.string.sort_date_asc
    MediaSort.SIZE_DESC -> R.string.sort_size_desc
    MediaSort.NAME_ASC -> R.string.sort_name_asc
}
