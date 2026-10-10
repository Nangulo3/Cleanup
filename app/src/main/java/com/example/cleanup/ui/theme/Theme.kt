package com.example.cleanup.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.example.cleanup.settings.ThemeMode

private val DarkColors = darkColorScheme(
    primary = MintPrimaryDark,
    onPrimary = OnMintPrimaryDark,
    primaryContainer = MintContainerDark,
    onPrimaryContainer = OnMintContainerDark,
    secondary = BlueSecondaryDark,
    onSecondary = OnBlueSecondaryDark,
    secondaryContainer = BlueContainerDark,
    onSecondaryContainer = OnBlueContainerDark,
    tertiary = AmberTertiaryDark,
    onTertiary = OnAmberTertiaryDark,
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = OnAmberContainerDark,
    error = CoralErrorDark,
    onError = OnCoralErrorDark,
    errorContainer = CoralContainerDark,
    onErrorContainer = OnCoralContainerDark,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceContainerHigh,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
)

private val LightColors = lightColorScheme(
    primary = MintPrimaryLight,
    onPrimary = OnMintPrimaryLight,
    primaryContainer = MintContainerLight,
    onPrimaryContainer = OnMintContainerLight,
    secondary = BlueSecondaryLight,
    onSecondary = OnBlueSecondaryLight,
    secondaryContainer = BlueContainerLight,
    onSecondaryContainer = OnBlueContainerLight,
    tertiary = AmberTertiaryLight,
    onTertiary = OnAmberTertiaryLight,
    tertiaryContainer = AmberContainerLight,
    onTertiaryContainer = OnAmberContainerLight,
    error = CoralErrorLight,
    onError = OnCoralErrorLight,
    errorContainer = CoralContainerLight,
    onErrorContainer = OnCoralContainerLight,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceContainerHigh,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
)

/** Devuelve si debe usarse el esquema oscuro para el modo elegido en Ajustes. */
@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun CleanupTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        shapes = CleanupShapes,
        content = content
    )
}
