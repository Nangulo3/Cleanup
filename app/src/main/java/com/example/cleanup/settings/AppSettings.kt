package com.example.cleanup.settings

/** Tema visual elegido por el usuario. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Qué le pide la app a Android al confirmar "Eliminar definitivamente" en la Papelera de revisión.
 * - [PERMANENT]: MediaStore.createDeleteRequest (libera espacio de inmediato).
 * - [SYSTEM_TRASH]: MediaStore.createTrashRequest (Android lo conserva ~30 días).
 */
enum class DeletionMode { PERMANENT, SYSTEM_TRASH }

/** Qué tan parecidas deben ser dos fotos para agruparlas como similares. */
enum class SimilarityLevel { STRICT, NORMAL, BROAD }

/** Preferencias locales de la app. Los valores por defecto se usan mientras DataStore carga. */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val deletionMode: DeletionMode = DeletionMode.PERMANENT,
    val similarityLevel: SimilarityLevel = SimilarityLevel.NORMAL,
    /** Analizar solo lo nuevo al abrir la app y cuando cambia la biblioteca. */
    val autoScan: Boolean = true,
)
