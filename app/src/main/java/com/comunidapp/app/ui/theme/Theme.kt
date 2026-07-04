package com.comunidapp.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = GreenPrimary,
    onPrimary = White,
    primaryContainer = GreenContainer,
    onPrimaryContainer = GreenPrimaryDark,
    secondary = CyanAccent,
    onSecondary = White,
    secondaryContainer = CyanContainer,
    onSecondaryContainer = TextPrimary,
    tertiary = OrangeAccent,
    onTertiary = White,
    tertiaryContainer = OrangeContainer,
    onTertiaryContainer = TextPrimary,
    background = BackgroundLight,
    onBackground = TextPrimary,
    surface = SurfaceLight,
    onSurface = TextPrimary,
    surfaceVariant = GrayLight,
    onSurfaceVariant = TextSecondary,
    error = UrgentRed,
    onError = White
)

private val DarkColorScheme = darkColorScheme(
    primary = GreenPrimaryDarkTheme,
    onPrimary = TextPrimary,
    primaryContainer = GreenPrimaryDark,
    onPrimaryContainer = GreenPrimaryLight,
    secondary = CyanAccent,
    onSecondary = TextPrimary,
    secondaryContainer = CyanContainer,
    onSecondaryContainer = TextPrimary,
    tertiary = OrangeAccent,
    onTertiary = TextPrimary,
    tertiaryContainer = OrangeContainer,
    onTertiaryContainer = TextPrimary,
    background = BackgroundDark,
    onBackground = White,
    surface = SurfaceDark,
    onSurface = White,
    surfaceVariant = GrayDark,
    onSurfaceVariant = GrayMedium,
    error = UrgentRed,
    onError = White
)

@Composable
fun ComunidappTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
