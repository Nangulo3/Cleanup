package com.example.cleanup.ui.screens.media

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import com.example.cleanup.R
import com.example.cleanup.data.db.dao.MediaGridItem
import com.example.cleanup.data.media.MediaThumbnail
import com.example.cleanup.domain.model.MediaType
import com.example.cleanup.ui.components.AppIcons
import com.example.cleanup.util.Formatters

/**
 * Miniatura cuadrada de la cuadrícula. La imagen la carga Coil con la miniatura del sistema
 * solo cuando la celda es visible (LazyVerticalGrid compone solo lo que está en pantalla).
 *
 * Estados (siempre con icono, no solo color): seleccionado (borde + check) y
 * en la papelera (velo oscuro + icono de papelera).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaTile(
    item: MediaGridItem,
    selected: Boolean,
    pending: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val thumbnail = remember(item.contentUri, item.dateModifiedMs) {
        MediaThumbnail(item.contentUri.toUri(), item.dateModifiedMs ?: 0L)
    }
    val date = if (item.sortDateMs > 0) Formatters.date(item.sortDateMs) else stringResource(R.string.tile_no_date)
    val baseDescription = if (item.mediaType == MediaType.VIDEO) {
        stringResource(R.string.tile_video_desc, Formatters.duration(item.durationMs ?: 0L), date)
    } else {
        stringResource(R.string.tile_photo_desc, date)
    }
    val description = if (pending) baseDescription + ", " + stringResource(R.string.tile_in_trash) else baseDescription
    val primary = MaterialTheme.colorScheme.primary

    Box(
        modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(if (selected) Modifier.border(3.dp, primary, MaterialTheme.shapes.extraSmall) else Modifier)
            .combinedClickable(
                onClickLabel = stringResource(if (selectionMode) R.string.action_select else R.string.action_open),
                onLongClickLabel = stringResource(R.string.action_select),
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .semantics {
                contentDescription = description
                this.selected = selected
            },
    ) {
        AsyncImage(
            model = thumbnail,
            contentDescription = null, // la descripción está en el contenedor
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (pending) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .size(24.dp)
                    .background(MaterialTheme.colorScheme.error, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    AppIcons.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        if (selectionMode) {
            val corner = Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp)
            if (selected) {
                Box(corner.background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(AppIcons.CheckCircle, contentDescription = null, tint = primary, modifier = Modifier.size(24.dp))
                }
            } else {
                Box(corner.border(2.dp, Color.White, CircleShape))
            }
        }
        if (item.mediaType == MediaType.VIDEO) {
            VideoBadge(
                durationMs = item.durationMs,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp),
            )
        }
    }
}

/** Distintivo de video: icono de reproducir + duración (no depende solo del color). */
@Composable
fun VideoBadge(durationMs: Long?, modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        if (durationMs != null) {
            Spacer(Modifier.width(2.dp))
            Text(
                Formatters.duration(durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
            )
        }
    }
}
