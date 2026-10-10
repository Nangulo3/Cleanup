package com.example.cleanup

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cleanup.settings.AppSettings
import com.example.cleanup.ui.navigation.CleanupRoot
import com.example.cleanup.ui.theme.CleanupTheme
import com.example.cleanup.ui.theme.isDark

class MainActivity : ComponentActivity() {

    private val container get() = (application as CleanupApp).container

    override fun onStart() {
        super.onStart()
        // Mantiene el catálogo al día mientras la app está visible.
        container.libraryAutoUpdater.onForeground()
    }

    override fun onStop() {
        container.libraryAutoUpdater.onBackground()
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val settings by container.settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())
            val darkTheme = settings.themeMode.isDark()

            // Los iconos de la barra de estado y de navegación siguen el tema de la app
            // (que puede ser distinto del tema del sistema).
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(LightScrim, DarkScrim) { darkTheme },
                )
                onDispose {}
            }

            CleanupTheme(darkTheme = darkTheme) {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    CleanupRoot()
                }
            }
        }
    }
}

// Velos semitransparentes para la barra de navegación de 3 botones.
private val LightScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DarkScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
