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

/**
 * Tema claro LeoVer — paleta pastel interna (decisión definitiva 2026-08-05).
 *
 * Predominio: BrandOrangeSoft (#FFA64D).
 * Apoyo: BrandGreen (#49B749).
 * BrandOrange / BrandGreenDark: solo acentos puntuales (tertiary / onSecondaryContainer).
 * Logo, launcher y splash: no se alteran aquí.
 */
private val LightColorScheme = lightColorScheme(
    primary = BrandOrangeSoft,
    onPrimary = BrandText,
    primaryContainer = BrandOrangeContainer,
    onPrimaryContainer = BrandText,
    secondary = BrandGreen,
    onSecondary = BrandText,
    secondaryContainer = BrandGreenContainer,
    onSecondaryContainer = BrandGreenDark,
    tertiary = BrandOrange,
    onTertiary = BrandText,
    tertiaryContainer = BrandOrangeContainer,
    onTertiaryContainer = BrandOrange,
    background = BrandCream,
    onBackground = BrandText,
    surface = BrandWhite,
    onSurface = BrandText,
    surfaceVariant = BrandCream,
    onSurfaceVariant = BrandTextSecondary,
    outline = BrandGrayMedium,
    error = UrgentRed,
    onError = BrandWhite,
    errorContainer = UrgentContainer,
    onErrorContainer = UrgentRed
)

private val DarkColorScheme = darkColorScheme(
    primary = BrandOrangeSoft,
    onPrimary = BrandText,
    primaryContainer = BrandOrangeDeep,
    onPrimaryContainer = BrandCream,
    secondary = BrandGreen,
    onSecondary = BrandText,
    secondaryContainer = BrandGreenDark,
    onSecondaryContainer = BrandGreenSoft,
    tertiary = BrandOrange,
    onTertiary = BrandText,
    tertiaryContainer = BrandOrangeDeep,
    onTertiaryContainer = BrandOrangeSoft,
    background = BackgroundDark,
    onBackground = BrandWhite,
    surface = SurfaceDark,
    onSurface = BrandWhite,
    surfaceVariant = BrandGrayDark,
    onSurfaceVariant = BrandGrayMedium,
    error = UrgentRed,
    onError = BrandWhite
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
            window.statusBarColor = BrandCream.toArgb()
            window.navigationBarColor = BrandCream.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = true
            controller.isAppearanceLightNavigationBars = true
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
