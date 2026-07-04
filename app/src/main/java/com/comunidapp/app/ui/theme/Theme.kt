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
    primary = OrangePrimary,
    onPrimary = White,
    primaryContainer = OrangeContainer,
    onPrimaryContainer = OrangePrimaryDark,
    secondary = GreenPrimary,
    onSecondary = White,
    secondaryContainer = GreenContainer,
    onSecondaryContainer = GreenPrimaryDark,
    tertiary = GreenPrimaryLight,
    onTertiary = GreenPrimaryDark,
    tertiaryContainer = GreenContainer,
    onTertiaryContainer = GreenPrimaryDark,
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
    primary = OrangePrimaryLight,
    onPrimary = TextPrimary,
    primaryContainer = OrangePrimaryDark,
    onPrimaryContainer = OrangeContainer,
    secondary = GreenPrimaryLight,
    onSecondary = TextPrimary,
    secondaryContainer = GreenPrimaryDark,
    onSecondaryContainer = GreenPrimaryLight,
    tertiary = GreenPrimary,
    onTertiary = White,
    tertiaryContainer = GreenPrimaryDark,
    onTertiaryContainer = GreenPrimaryLight,
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
