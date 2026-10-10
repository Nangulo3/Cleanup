package com.example.cleanup.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

// DataStore debe ser una única instancia por archivo en todo el proceso; el delegado lo garantiza.
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Lee y escribe las preferencias locales (archivo "settings" de DataStore). */
class SettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.settingsDataStore

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val DELETION_MODE = stringPreferencesKey("deletion_mode")
        val SIMILARITY = stringPreferencesKey("similarity_level")
    }

    val settings: Flow<AppSettings> = dataStore.data
        .catch { e ->
            // Si el archivo está dañado o no se puede leer, seguimos con los valores por defecto.
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs ->
            AppSettings(
                themeMode = prefs[Keys.THEME].toEnumOrNull<ThemeMode>() ?: AppSettings().themeMode,
                deletionMode = prefs[Keys.DELETION_MODE].toEnumOrNull<DeletionMode>()
                    ?: AppSettings().deletionMode,
                similarityLevel = prefs[Keys.SIMILARITY].toEnumOrNull<SimilarityLevel>()
                    ?: AppSettings().similarityLevel,
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME] = mode.name }
    }

    suspend fun setDeletionMode(mode: DeletionMode) {
        dataStore.edit { it[Keys.DELETION_MODE] = mode.name }
    }

    suspend fun setSimilarityLevel(level: SimilarityLevel) {
        dataStore.edit { it[Keys.SIMILARITY] = level.name }
    }
}

private inline fun <reified T : Enum<T>> String?.toEnumOrNull(): T? =
    this?.let { name -> enumValues<T>().firstOrNull { it.name == name } }
