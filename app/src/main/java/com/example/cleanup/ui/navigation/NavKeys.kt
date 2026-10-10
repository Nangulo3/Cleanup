package com.example.cleanup.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import com.example.cleanup.R
import com.example.cleanup.domain.model.Category
import com.example.cleanup.ui.components.AppIcons
import kotlinx.serialization.Serializable

// Cada pantalla es una "clave" de navegación. Son @Serializable para que el historial
// sobreviva a que Android cierre el proceso en segundo plano.

@Serializable
data object HomeKey : NavKey

@Serializable
data object SwipeKey : NavKey

@Serializable
data object TrashKey : NavKey

@Serializable
data object SettingsKey : NavKey

/** Cuadrícula de una categoría (Fotos, Videos, ...). */
@Serializable
data class CategoryKey(val category: Category) : NavKey

/** Revisión de un mes en Deslizar (monthKey = yyyyMM; 0 = sin fecha). */
@Serializable
data class SwipeReviewKey(val monthKey: Int) : NavKey

/** Visor de un elemento por su ID de MediaStore. */
@Serializable
data class MediaViewerKey(val mediaId: Long) : NavKey

/** Destinos de la barra inferior. */
data class TopLevelDestination(
    val key: NavKey,
    val icon: ImageVector,
    @StringRes val labelRes: Int,
)

val TopLevelDestinations: List<TopLevelDestination> by lazy {
    listOf(
        TopLevelDestination(HomeKey, AppIcons.Home, R.string.nav_home),
        TopLevelDestination(SwipeKey, AppIcons.Swipe, R.string.nav_swipe),
        TopLevelDestination(TrashKey, AppIcons.Delete, R.string.nav_trash),
        TopLevelDestination(SettingsKey, AppIcons.Settings, R.string.nav_settings),
    )
}
