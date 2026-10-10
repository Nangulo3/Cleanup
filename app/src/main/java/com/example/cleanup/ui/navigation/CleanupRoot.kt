package com.example.cleanup.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.example.cleanup.LocalAppContainer
import com.example.cleanup.data.db.dao.TrashSummary
import com.example.cleanup.domain.model.Category
import com.example.cleanup.domain.model.MediaAccess
import com.example.cleanup.ui.screens.duplicates.DuplicatesScreen
import com.example.cleanup.ui.screens.home.HomeScreen
import com.example.cleanup.ui.screens.media.MediaGridScreen
import com.example.cleanup.ui.screens.permission.MediaPermissionRequester
import com.example.cleanup.ui.screens.permission.PermissionScreen
import com.example.cleanup.ui.screens.permission.rememberMediaPermissionRequester
import com.example.cleanup.ui.screens.settings.SettingsScreen
import com.example.cleanup.ui.screens.swipe.SwipeReviewScreen
import com.example.cleanup.ui.screens.swipe.SwipeScreen
import com.example.cleanup.ui.screens.trash.TrashScreen
import com.example.cleanup.ui.screens.viewer.MediaViewerScreen
import com.example.cleanup.util.Formatters

/**
 * Raíz de la interfaz: decide entre la pantalla de permisos y la navegación principal,
 * y vuelve a comprobar el permiso cada vez que la app pasa a primer plano.
 */
@Composable
fun CleanupRoot() {
    val accessRepository = LocalAppContainer.current.mediaAccessRepository
    val access by accessRepository.access.collectAsStateWithLifecycle()
    val requester = rememberMediaPermissionRequester(accessRepository)

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        accessRepository.refresh()
    }

    if (access == MediaAccess.DENIED) {
        PermissionScreen(requester)
    } else {
        MainNavigation(access = access, requester = requester)
    }
}

@Composable
private fun MainNavigation(access: MediaAccess, requester: MediaPermissionRequester) {
    // El historial empieza en Inicio. Navigation 3 lo guarda y restaura automáticamente.
    val backStack = rememberNavBackStack(HomeKey)
    val current = backStack.lastOrNull()
    val trashSummary by LocalAppContainer.current.trashRepository.summary
        .collectAsStateWithLifecycle(initialValue = TrashSummary(0, 0, 0))
    val showBottomBar = TopLevelDestinations.any { it.key == current }

    Scaffold(
        // Cada pantalla gestiona sus propios márgenes del sistema; aquí solo la barra inferior.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevelDestinations.forEach { destination ->
                        NavigationBarItem(
                            selected = current == destination.key,
                            onClick = { backStack.selectTopLevel(destination.key) },
                            icon = {
                                val count = if (destination.key == TrashKey) trashSummary.itemCount else 0
                                BadgedBox(badge = { if (count > 0) Badge { Text(Formatters.count(count)) } }) {
                                    Icon(destination.icon, contentDescription = null)
                                }
                            },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavDisplay(
            backStack = backStack,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
            onBack = { backStack.removeLastOrNull() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<HomeKey> {
                    HomeScreen(
                        access = access,
                        onExpandAccess = requester::request,
                        onOpenTrash = { backStack.selectTopLevel(TrashKey) },
                        onOpenCategory = { category -> backStack.add(CategoryKey(category)) },
                    )
                }
                entry<CategoryKey> { key ->
                    when (key.category) {
                        Category.DUPLICATES -> DuplicatesScreen(
                            onBack = { backStack.removeLastOrNull() },
                            onOpenItem = { id -> backStack.add(MediaViewerKey(id)) },
                        )
                        else -> MediaGridScreen(
                            category = key.category,
                            onBack = { backStack.removeLastOrNull() },
                            onOpenItem = { id -> backStack.add(MediaViewerKey(id)) },
                        )
                    }
                }
                entry<MediaViewerKey> { key ->
                    MediaViewerScreen(
                        mediaId = key.mediaId,
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
                entry<SwipeKey> {
                    SwipeScreen(onOpenMonth = { month -> backStack.add(SwipeReviewKey(month)) })
                }
                entry<SwipeReviewKey> { key ->
                    SwipeReviewScreen(
                        monthKey = key.monthKey,
                        onBack = { backStack.removeLastOrNull() },
                        onOpenItem = { id -> backStack.add(MediaViewerKey(id)) },
                        onOpenTrash = { backStack.add(TrashKey) },
                        onOpenMonth = { month ->
                            // Cambia de mes sin apilar revisiones: Atrás vuelve a la lista de meses.
                            backStack.removeLastOrNull()
                            backStack.add(SwipeReviewKey(month))
                        },
                    )
                }
                entry<TrashKey> {
                    TrashScreen(onOpenItem = { id -> backStack.add(MediaViewerKey(id)) })
                }
                entry<SettingsKey> {
                    SettingsScreen(access = access, onExpandAccess = requester::request)
                }
            },
        )
    }
}

/**
 * Cambia de pestaña: deja Inicio como raíz y pone encima la pestaña elegida,
 * así "Atrás" desde cualquier pestaña vuelve a Inicio.
 */
private fun NavBackStack<NavKey>.selectTopLevel(key: NavKey) {
    if (lastOrNull() == key) return
    while (size > 1) removeAt(lastIndex)
    if (key != HomeKey) add(key)
}
