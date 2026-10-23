package com.aiagent.assistant.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val DarkColors = darkColorScheme(
    primary = Violet500,
    onPrimary = Color.White,
    primaryContainer = Violet900,
    onPrimaryContainer = Violet200,
    secondary = Cyan400,
    onSecondary = Color(0xFF00202A),
    secondaryContainer = Cyan900,
    onSecondaryContainer = Color(0xFFB8F1FF),
    tertiary = Pink400,
    onTertiary = Color.White,
    background = DarkBackground,
    onBackground = Color(0xFFEDEBFF),
    surface = DarkSurface,
    onSurface = Color(0xFFEDEBFF),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFC6C3E0),
    outline = DarkOutline,
    error = ErrorRed
)

private val LightColors = lightColorScheme(
    primary = Violet500,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5DEFF),
    onPrimaryContainer = Violet900,
    secondary = Color(0xFF0891B2),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFF5FF),
    onSecondaryContainer = Cyan900,
    tertiary = Color(0xFFD6337F),
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = Color(0xFF1B1830),
    surface = LightSurface,
    onSurface = Color(0xFF1B1830),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF47445C),
    outline = LightOutline,
    error = Color(0xFFBA1A3A)
)

@Composable
fun AgentTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AgentTypography,
        shapes = AgentShapes,
        content = content
    )
}
