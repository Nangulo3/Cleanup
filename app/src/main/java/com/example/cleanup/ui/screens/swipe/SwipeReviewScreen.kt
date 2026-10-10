package com.example.cleanup.ui.screens.swipe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.imageLoader
import coil3.request.ImageRequest
import com.example.cleanup.LocalAppContainer
import com.example.cleanup.R
import com.example.cleanup.data.db.dao.SwipeItem
import com.example.cleanup.data.media.MediaThumbnail
import com.example.cleanup.domain.model.MediaType
import com.example.cleanup.ui.components.AppIcons
import com.example.cleanup.ui.components.LoadingState
import com.example.cleanup.util.Formatters
import kotlinx.coroutines.launch

/**
 * Revisión de un mes, una foto o video a la vez.
 * Conservar no toca el archivo; Eliminar solo lo añade a la Papelera de revisión.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeReviewScreen(
    monthKey: Int,
    onBack: () -> Unit,
    onOpenItem: (Long) -> Unit,
    onOpenTrash: () -> Unit,
    onOpenMonth: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val viewModel: SwipeReviewViewModel = viewModel {
        SwipeReviewViewModel(monthKey, container.reviewRepository, container.trashRepository, container.mediaStoreSource)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    val messageText = when (state.message) {
        SwipeMessage.NothingToUndo -> stringResource(R.string.msg_nothing_to_undo)
        SwipeMessage.ItemMissing -> stringResource(R.string.swipe_item_missing)
        null -> null
    }
    LaunchedEffect(state.message) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(monthLabel(monthKey))
                        if (state.total > 0 && !state.isMonthComplete) {
                            Text(
                                stringResource(
                                    R.string.swipe_position,
                                    Formatters.count(state.position),
                                    Formatters.count(state.total),
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
                    IconButton(onClick = onOpenTrash) {
                        BadgedBox(badge = {
                            if (state.trashCount > 0) Badge { Text(Formatters.count(state.trashCount)) }
                        }) {
                            Icon(AppIcons.Delete, contentDescription = stringResource(R.string.trash_title))
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(AppIcons.Sort, contentDescription = stringResource(R.string.swipe_more_options))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.swipe_reset_month)) },
                                onClick = {
                                    menuOpen = false
                                    confirmReset = true
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> LoadingState(stringResource(R.string.loading))
                state.isMonthComplete -> MonthCompleteContent(
                    state = state,
                    onOpenTrash = onOpenTrash,
                    onNextMonth = { state.nextMonthWithPending?.let(onOpenMonth) },
                    onReset = { confirmReset = true },
                    onBack = onBack,
                )
                state.current == null -> MonthCompleteContent(
                    state = state,
                    onOpenTrash = onOpenTrash,
                    onNextMonth = { state.nextMonthWithPending?.let(onOpenMonth) },
                    onReset = null,
                    onBack = onBack,
                )
                else -> ReviewContent(
                    state = state,
                    onKeep = viewModel::keep,
                    onDelete = viewModel::markForDeletion,
                    onUndo = viewModel::undo,
                    onOpenItem = onOpenItem,
                    onLoadError = viewModel::onLoadError,
                )
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.swipe_reset_month)) },
            text = { Text(stringResource(R.string.swipe_reset_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    viewModel.resetMonth()
                }) { Text(stringResource(R.string.swipe_reset_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun ReviewContent(
    state: SwipeReviewUiState,
    onKeep: () -> Unit,
    onDelete: () -> Unit,
    onUndo: () -> Unit,
    onOpenItem: (Long) -> Unit,
    onLoadError: (SwipeItem) -> Unit,
) {
    val current = state.current ?: return
    val scope = rememberCoroutineScope()
    // Un estado de arrastre nuevo por elemento: al cambiar de elemento la tarjeta vuelve al centro.
    val cardState = remember(current.id) { SwipeCardState() }
    PreloadUpcoming(state.queue.drop(2))

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // Tarjeta de fondo: el siguiente elemento, ya cargado para que el cambio sea instantáneo.
            state.queue.getOrNull(1)?.let { next ->
                key(next.id) {
                    SwipeCardImage(
                        item = next,
                        unavailable = false,
                        onLoadError = {},
                        modifier = Modifier
                            .graphicsLayer { scaleX = 0.94f; scaleY = 0.94f; alpha = 0.7f }
                            .clip(MaterialTheme.shapes.extraLarge),
                    )
                }
            }
            key(current.id) {
                SwipeCard(
                    item = current,
                    state = cardState,
                    alreadyInTrash = state.currentIsPending,
                    unavailable = state.unavailableId == current.id,
                    description = itemDescription(current),
                    onKeep = onKeep,
                    onDelete = onDelete,
                    onOpen = { onOpenItem(current.id) },
                    onLoadError = { onLoadError(current) },
                )
            }
        }

        ItemInfo(current)

        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(
                onClick = { scope.launch { cardState.flyOut(-1); onDelete() } },
                modifier = Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Icon(AppIcons.Delete, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.swipe_delete))
            }
            IconButton(onClick = onUndo, enabled = state.canUndo, modifier = Modifier.size(56.dp)) {
                Icon(AppIcons.Undo, contentDescription = stringResource(R.string.action_undo))
            }
            FilledTonalButton(
                onClick = { scope.launch { cardState.flyOut(1); onKeep() } },
                modifier = Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Icon(AppIcons.Check, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.swipe_keep))
            }
        }
    }
}

/** Fecha, tamaño y nombre del elemento actual. */
@Composable
private fun ItemInfo(item: SwipeItem) {
    val unknown = stringResource(R.string.unknown)
    val date = if (item.sortDateMs > 0) Formatters.dateTime(item.sortDateMs) else stringResource(R.string.tile_no_date)
    val size = item.sizeBytes?.let { Formatters.bytes(it) } ?: unknown
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Text(date, style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.swipe_item_details, item.displayName ?: unknown, size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun itemDescription(item: SwipeItem): String {
    val date = if (item.sortDateMs > 0) Formatters.date(item.sortDateMs) else stringResource(R.string.tile_no_date)
    return if (item.mediaType == MediaType.VIDEO) {
        stringResource(R.string.tile_video_desc, Formatters.duration(item.durationMs ?: 0L), date)
    } else {
        stringResource(R.string.tile_photo_desc, date)
    }
}

/**
 * Precarga en memoria los siguientes elementos (además de la tarjeta de fondo) para que el
 * paso de uno a otro no muestre espacios en negro. Solo se piden versiones reducidas.
 */
@Composable
private fun PreloadUpcoming(items: List<SwipeItem>) {
    val context = LocalContext.current
    LaunchedEffect(items.map { it.id }) {
        val loader = context.imageLoader
        items.forEach { item ->
            val uri = item.contentUri.toUri()
            val data: Any = if (item.mediaType == MediaType.VIDEO) MediaThumbnail(uri, item.dateModifiedMs ?: 0L) else uri
            loader.enqueue(ImageRequest.Builder(context).data(data).size(PRELOAD_SIZE_PX).build())
        }
    }
}

private const val PRELOAD_SIZE_PX = 1080

@Composable
private fun MonthCompleteContent(
    state: SwipeReviewUiState,
    onOpenTrash: () -> Unit,
    onNextMonth: () -> Unit,
    onReset: (() -> Unit)?,
    onBack: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            AppIcons.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.swipe_month_complete_title, monthLabel(state.monthKey)),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        state.progress?.let { progress ->
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(
                    R.string.swipe_month_complete_body,
                    Formatters.count(progress.kept),
                    Formatters.count(progress.marked),
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(24.dp))
        if (state.nextMonthWithPending != null) {
            Button(onClick = onNextMonth, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(stringResource(R.string.swipe_next_month, monthLabel(state.nextMonthWithPending)))
            }
            Spacer(Modifier.height(8.dp))
        }
        if (state.trashCount > 0) {
            OutlinedButton(onClick = onOpenTrash, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(stringResource(R.string.swipe_open_trash, Formatters.count(state.trashCount)))
            }
            Spacer(Modifier.height(8.dp))
        }
        if (onReset != null) {
            TextButton(onClick = onReset) { Text(stringResource(R.string.swipe_reset_month)) }
        }
        TextButton(onClick = onBack) { Text(stringResource(R.string.swipe_back_to_months)) }
    }
}
