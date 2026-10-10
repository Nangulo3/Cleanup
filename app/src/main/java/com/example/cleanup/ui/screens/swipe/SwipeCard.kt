package com.example.cleanup.ui.screens.swipe

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import com.example.cleanup.R
import com.example.cleanup.data.db.dao.SwipeItem
import com.example.cleanup.data.media.MediaThumbnail
import com.example.cleanup.domain.model.MediaType
import com.example.cleanup.ui.components.AppIcons
import com.example.cleanup.ui.screens.media.VideoBadge
import kotlin.math.abs
import kotlinx.coroutines.launch

/** Estado de arrastre de la tarjeta actual. Los botones también lo usan para animar la salida. */
@Stable
class SwipeCardState {
    val offsetX = Animatable(0f)
    var widthPx = 1f
    private var crossedThreshold = false

    val threshold: Float get() = widthPx * 0.28f

    /** Lleva la tarjeta fuera de la pantalla: [direction] 1 = derecha (conservar), -1 = izquierda (eliminar). */
    suspend fun flyOut(direction: Int) {
        offsetX.animateTo(direction * widthPx * 1.4f, tween(durationMillis = 200))
    }

    suspend fun reset() {
        offsetX.animateTo(0f, spring())
        crossedThreshold = false
    }

    /** true la primera vez que el arrastre supera el umbral (para la vibración). */
    fun checkThresholdCrossed(): Boolean {
        val crossed = abs(offsetX.value) > threshold
        val firstTime = crossed && !crossedThreshold
        crossedThreshold = crossed
        return firstTime
    }
}

/**
 * Tarjeta de Deslizar: arrastrar a la derecha conserva y a la izquierda marca para eliminar.
 * Los mismos gestos están disponibles como acciones de accesibilidad y como botones aparte.
 */
@Composable
fun SwipeCard(
    item: SwipeItem,
    state: SwipeCardState,
    alreadyInTrash: Boolean,
    unavailable: Boolean,
    description: String,
    onKeep: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
    onLoadError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val keepLabel = stringResource(R.string.swipe_keep)
    val deleteLabel = stringResource(R.string.swipe_delete)

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { state.widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .graphicsLayer {
                translationX = state.offsetX.value
                rotationZ = state.offsetX.value / state.widthPx * 12f
            }
            .clip(MaterialTheme.shapes.extraLarge)
            .background(Color.Black)
            .pointerInput(item.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            when {
                                state.offsetX.value > state.threshold -> { state.flyOut(1); onKeep() }
                                state.offsetX.value < -state.threshold -> { state.flyOut(-1); onDelete() }
                                else -> state.reset()
                            }
                        }
                    },
                    onDragCancel = { scope.launch { state.reset() } },
                ) { change, dragAmount ->
                    change.consume()
                    scope.launch {
                        state.offsetX.snapTo(state.offsetX.value + dragAmount)
                        if (state.checkThresholdCrossed()) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }
                }
            }
            .clickable(onClickLabel = stringResource(R.string.action_open), onClick = onOpen)
            .semantics {
                contentDescription = description
                customActions = listOf(
                    CustomAccessibilityAction(keepLabel) { onKeep(); true },
                    CustomAccessibilityAction(deleteLabel) { onDelete(); true },
                )
            },
    ) {
        SwipeCardImage(item = item, unavailable = unavailable, onLoadError = onLoadError)

        if (item.mediaType == MediaType.VIDEO) {
            VideoBadge(item.durationMs, Modifier.align(Alignment.BottomEnd).padding(12.dp))
        }
        if (alreadyInTrash) {
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
                    .background(MaterialTheme.colorScheme.error, MaterialTheme.shapes.small)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AppIcons.Delete, null, tint = MaterialTheme.colorScheme.onError, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(R.string.swipe_already_in_trash),
                    color = MaterialTheme.colorScheme.onError,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        // Etiquetas que aparecen al arrastrar: texto + icono, no solo color.
        val progress = (state.offsetX.value / state.threshold).coerceIn(-1f, 1f)
        if (progress > 0.05f) {
            DecisionStamp(
                text = keepLabel,
                color = MaterialTheme.colorScheme.primary,
                keep = true,
                modifier = Modifier.align(Alignment.TopStart).padding(20.dp).graphicsLayer { alpha = progress },
            )
        } else if (progress < -0.05f) {
            DecisionStamp(
                text = deleteLabel,
                color = MaterialTheme.colorScheme.error,
                keep = false,
                modifier = Modifier.align(Alignment.TopEnd).padding(20.dp).graphicsLayer { alpha = -progress },
            )
        }
    }
}

/** Imagen de la tarjeta (también se usa para la tarjeta de fondo, ya precargada). */
@Composable
fun SwipeCardImage(item: SwipeItem, unavailable: Boolean, onLoadError: () -> Unit, modifier: Modifier = Modifier) {
    val uri = remember(item.contentUri) { item.contentUri.toUri() }
    val model: Any = remember(item.id, item.dateModifiedMs) {
        // Fotos: la imagen real reducida al tamaño de la tarjeta. Videos: fotograma del sistema.
        if (item.mediaType == MediaType.VIDEO) MediaThumbnail(uri, item.dateModifiedMs ?: 0L) else uri
    }
    Box(modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        if (unavailable) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Icon(AppIcons.Warning, null, tint = Color.White, modifier = Modifier.size(40.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.swipe_cannot_display), color = Color.White)
            }
        } else {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onError = { onLoadError() },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun DecisionStamp(text: String, color: Color, keep: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier
            .border(3.dp, color, MaterialTheme.shapes.small)
            .background(Color.Black.copy(alpha = 0.55f), MaterialTheme.shapes.small)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (keep) AppIcons.Check else AppIcons.Delete, null, tint = color)
        Spacer(Modifier.width(6.dp))
        Text(text.uppercase(), color = color, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}
