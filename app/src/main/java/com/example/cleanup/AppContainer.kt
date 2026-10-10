package com.example.cleanup

import com.example.cleanup.data.repo.AppDataCleaner
import android.content.Context
import android.view.WindowManager
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.cleanup.data.db.AppDatabase
import com.example.cleanup.data.media.MediaAccessRepository
import com.example.cleanup.data.media.MediaStoreSource
import com.example.cleanup.data.repo.DuplicatesRepository
import com.example.cleanup.data.repo.MediaRepository
import com.example.cleanup.data.repo.ReviewRepository
import com.example.cleanup.data.repo.SimilarRepository
import com.example.cleanup.deletion.DeletionCoordinator
import com.example.cleanup.deletion.TrashRepository
import com.example.cleanup.detection.ContentHasher
import com.example.cleanup.detection.ThumbnailSigner
import com.example.cleanup.detection.VideoFrameSampler
import com.example.cleanup.domain.model.ScreenInfo
import com.example.cleanup.settings.SettingsRepository
import com.example.cleanup.worker.CatalogScanner
import com.example.cleanup.worker.HashScanner
import com.example.cleanup.worker.LibraryAutoUpdater
import com.example.cleanup.worker.PerceptualScanner
import com.example.cleanup.worker.ScanScheduler
import com.example.cleanup.worker.SimilarGrouper
import com.example.cleanup.worker.VideoScanner
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

    val catalogScanner = CatalogScanner(mediaStoreSource, database, screen = { screenInfo(appContext) })
    val hashScanner = HashScanner(database, ContentHasher(appContext))
    val perceptualScanner = PerceptualScanner(database, ThumbnailSigner(appContext))
    val videoScanner = VideoScanner(database, VideoFrameSampler(appContext))
    val similarGrouper = SimilarGrouper(database, settingsRepository)
    val similarRepository = SimilarRepository(database, trashRepository)
    val scanScheduler = ScanScheduler(appContext)
    val libraryAutoUpdater = LibraryAutoUpdater(
        context = appContext,
        scope = applicationScope,
        accessRepository = mediaAccessRepository,
        scanDao = database.scanDao(),
        scheduler = scanScheduler,
        settings = settingsRepository,
    )
    val appDataCleaner = AppDataCleaner(database, scanScheduler)
}

/** Tamaño completo de la pantalla en píxeles (para reconocer capturas por sus dimensiones). */
private fun screenInfo(context: Context): ScreenInfo? = runCatching {
    val bounds = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
    ScreenInfo(bounds.width(), bounds.height())
}.getOrElse {
    // Alternativa si el sistema no da las métricas de ventana desde este contexto.
    val metrics = context.resources.displayMetrics
    ScreenInfo(metrics.widthPixels, metrics.heightPixels)
}

/** Permite a cualquier composable obtener el contenedor sin pasarlo por parámetros. */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer no fue proporcionado. Envuelve el contenido con CompositionLocalProvider.")
}
