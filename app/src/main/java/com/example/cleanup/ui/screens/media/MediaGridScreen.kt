package com.example.cleanup.ui.screens.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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

/** Cuadrícula de una categoría plana (Fotos, Videos) con orden y apertura del visor. */
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
        MediaGridViewModel(category, container.mediaRepository)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var sortMenuOpen by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
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
                    if (category == Category.VIDEOS) R.string.grid_empty_videos else R.string.grid_empty_photos
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
                items(
                    items = state.items,
                    key = { it.id },
                    contentType = { it.mediaType },
                ) { item ->
                    MediaTile(item = item, onClick = { onOpenItem(item.id) })
                }
            }
        }
    }
}

private fun MediaSort.labelRes(): Int = when (this) {
    MediaSort.DATE_DESC -> R.string.sort_date_desc
    MediaSort.DATE_ASC -> R.string.sort_date_asc
    MediaSort.SIZE_DESC -> R.string.sort_size_desc
    MediaSort.NAME_ASC -> R.string.sort_name_asc
}
