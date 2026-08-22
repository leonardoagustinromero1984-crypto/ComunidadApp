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
 * Tema claro LeoVer — paleta oficial UI-01.
 *
 * Background: BrandBackground (#FAFBF8). Surface: BrandWhite.
 * Primary UI green: BrandGreen (#49B749). Orange is scarce accent only.
 * Cream (#FFF8E1) is an accent only (surfaceVariant), never a full-screen default.
 * ProfileGreen is profile/context-only; do not replace BrandGreen.
 * Logo foreground artwork is not recoloured here.
 *
 * Prefer [LeoVerTheme] tokens in new screens instead of literal hex colors.
 */
private val LightColorScheme = lightColorScheme(
    primary = BrandGreen,
    onPrimary = BrandWhite,
    primaryContainer = BrandGreenContainer,
    onPrimaryContainer = BrandGreenDark,
    secondary = BrandGreen,
    onSecondary = BrandText,
    secondaryContainer = BrandGreenContainer,
    onSecondaryContainer = BrandGreenDark,
    tertiary = BrandOrange,
    onTertiary = BrandText,
    tertiaryContainer = BrandOrangeContainer,
    onTertiaryContainer = BrandOrange,
    background = BrandBackground,
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
            window.statusBarColor = BrandBackground.toArgb()
            window.navigationBarColor = BrandBackground.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = true
            controller.isAppearanceLightNavigationBars = true
        }
    }

    VisualDirectionPilot {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = LeoShapes,
            content = content
        )
    }
}
