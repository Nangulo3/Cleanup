package com.example.cleanup.ui.screens.viewer

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.example.cleanup.LocalAppContainer
import com.example.cleanup.R
import com.example.cleanup.data.db.entity.MediaItemEntity
import com.example.cleanup.data.media.MediaThumbnail
import com.example.cleanup.domain.model.MediaType
import com.example.cleanup.ui.components.AppIcons
import com.example.cleanup.ui.components.EmptyState
import com.example.cleanup.ui.components.LoadingState
import com.example.cleanup.ui.components.openMediaExternally
import com.example.cleanup.util.Formatters

/** Visor de un elemento con sus detalles. No modifica el archivo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(mediaId: Long, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val viewModel: MediaViewerViewModel = viewModel {
        MediaViewerViewModel(mediaId, container.mediaRepository, container.trashRepository)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val title = (state as? MediaViewerUiState.Loaded)?.item?.displayName
        ?: stringResource(R.string.viewer_item_fallback)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        when (val current = state) {
            MediaViewerUiState.Loading -> LoadingState(
                message = stringResource(R.string.loading),
                modifier = Modifier.padding(padding),
            )
            MediaViewerUiState.Missing -> EmptyState(
                icon = AppIcons.Warning,
                title = stringResource(R.string.viewer_missing_title),
                message = stringResource(R.string.viewer_missing_body),
                modifier = Modifier.padding(padding),
            )
            is MediaViewerUiState.Loaded -> ViewerContent(
                item = current.item,
                isPending = current.isPending,
                onToggleMark = viewModel::toggleMark,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun ViewerContent(
    item: MediaItemEntity,
    isPending: Boolean,
    onToggleMark: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uri = remember(item.contentUri) { item.contentUri.toUri() }
    val openExternally = {
        if (!context.openMediaExternally(uri, item.mimeType)) {
            Toast.makeText(context, R.string.viewer_no_app, Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(item.displayAspectRatio())
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (item.mediaType == MediaType.VIDEO) {
                AsyncImage(
                    model = MediaThumbnail(uri, item.dateModifiedMs ?: 0L),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                FilledIconButton(onClick = openExternally, modifier = Modifier.size(64.dp)) {
                    Icon(
                        AppIcons.PlayArrow,
                        contentDescription = stringResource(R.string.viewer_play),
                        modifier = Modifier.size(36.dp),
                    )
                }
            } else {
                // Coil decodifica la foto reducida al tamaño de la pantalla, no la original completa.
                AsyncImage(
                    model = uri,
                    contentDescription = item.displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Column(Modifier.padding(16.dp)) {
            MarkForDeletionSection(isPending = isPending, onToggleMark = onToggleMark)
            Spacer(Modifier.height(16.dp))
            val unknown = stringResource(R.string.unknown)
            DetailRow(stringResource(R.string.detail_name), item.displayName ?: unknown)
            DetailRow(
                stringResource(R.string.detail_date),
                if (item.sortDateMs > 0) Formatters.dateTime(item.sortDateMs) else unknown,
            )
            DetailRow(
                stringResource(R.string.detail_size),
                item.sizeBytes?.let { Formatters.bytes(it) } ?: unknown,
            )
            DetailRow(
                stringResource(R.string.detail_dimensions),
                if (item.width != null && item.height != null) {
                    stringResource(R.string.dimensions_format, item.width, item.height)
                } else {
                    unknown
                },
            )
            if (item.mediaType == MediaType.VIDEO) {
                DetailRow(
                    stringResource(R.string.detail_duration),
                    item.durationMs?.let { Formatters.duration(it) } ?: unknown,
                )
            }
            DetailRow(stringResource(R.string.detail_folder), item.relativePath ?: unknown)
            DetailRow(stringResource(R.string.detail_type), item.mimeType ?: unknown)

            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = openExternally, modifier = Modifier.fillMaxWidth()) {
                Icon(AppIcons.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.viewer_open_external))
            }
        }
    }
}

/** Marcar/desmarcar para eliminar, con el recordatorio de que el archivo sigue en el teléfono. */
@Composable
private fun MarkForDeletionSection(isPending: Boolean, onToggleMark: () -> Unit) {
    if (isPending) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                AppIcons.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.viewer_in_trash),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onToggleMark, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text(stringResource(R.string.viewer_unmark))
        }
    } else {
        FilledTonalButton(
            onClick = onToggleMark,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        ) {
            Icon(AppIcons.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.viewer_mark))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

/** Proporción de la imagen girada según su orientación, limitada para no ocupar media pantalla en vertical. */
private fun MediaItemEntity.displayAspectRatio(): Float {
    val w = width ?: return 1f
    val h = height ?: return 1f
    val rotated = orientation == 90 || orientation == 270
    val ratio = if (rotated) h.toFloat() / w else w.toFloat() / h
    return ratio.coerceIn(0.6f, 2.2f)
}
