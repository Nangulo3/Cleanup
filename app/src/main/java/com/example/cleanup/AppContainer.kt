package com.example.cleanup

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.cleanup.data.db.AppDatabase
import com.example.cleanup.data.media.MediaAccessRepository
import com.example.cleanup.data.media.MediaStoreSource
import com.example.cleanup.data.repo.DuplicatesRepository
import com.example.cleanup.data.repo.MediaRepository
import com.example.cleanup.data.repo.ReviewRepository
import com.example.cleanup.deletion.DeletionCoordinator
import com.example.cleanup.deletion.TrashRepository
import com.example.cleanup.detection.ContentHasher
import com.example.cleanup.settings.SettingsRepository
import com.example.cleanup.worker.CatalogScanner
import com.example.cleanup.worker.HashScanner
import com.example.cleanup.worker.LibraryAutoUpdater
import com.example.cleanup.worker.ScanScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Inyección de dependencias manual: aquí se crean una vez los objetos compartidos
 * (repositorios, base de datos, etc.) y las pantallas los piden a través de [LocalAppContainer].
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Ámbito de corrutinas que vive lo mismo que el proceso (para tareas que no dependen de una pantalla). */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase = AppDatabase.build(appContext)

    val settingsRepository = SettingsRepository(appContext)
    val mediaAccessRepository = MediaAccessRepository(appContext)
    val mediaStoreSource = MediaStoreSource(appContext)
    val mediaRepository = MediaRepository(database.mediaDao(), database.scanDao())

    val trashRepository = TrashRepository(database)
    val reviewRepository = ReviewRepository(database)
    val duplicatesRepository = DuplicatesRepository(database, trashRepository)
    val deletionCoordinator = DeletionCoordinator(
        db = database,
        source = mediaStoreSource,
        trash = trashRepository,
        settings = settingsRepository,
        access = mediaAccessRepository,
    )

    val catalogScanner = CatalogScanner(mediaStoreSource, database)
    val hashScanner = HashScanner(database, ContentHasher(appContext))
    val scanScheduler = ScanScheduler(appContext)
    val libraryAutoUpdater = LibraryAutoUpdater(
        context = appContext,
        scope = applicationScope,
        accessRepository = mediaAccessRepository,
        scanDao = database.scanDao(),
        scheduler = scanScheduler,
    )
}

/** Permite a cualquier composable obtener el contenedor sin pasarlo por parámetros. */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer no fue proporcionado. Envuelve el contenido con CompositionLocalProvider.")
}
