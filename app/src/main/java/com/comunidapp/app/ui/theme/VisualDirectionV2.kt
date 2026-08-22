package com.comunidapp.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Experimental visual direction palettes.
 *
 * V3: approved near-white background + original LeoVer green accent.
 * Sage/teal V2 primaries are retired from active UI.
 * Not permanent brand law. Logo colors stay unchanged.
 */
data class LeoVerVisualPalette(
    val background: Color,
    val surface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val borderSoft: Color,
    val primary: Color,
    val primaryDark: Color,
    val primarySoft: Color,
    val onPrimary: Color,
    val secondary: Color,
    val secondarySoft: Color,
    val accent: Color,
    val accentSoft: Color,
    val error: Color,
    val profileAccent: Color
) {
    companion object {
        val v1 = LeoVerVisualPalette(
            background = BrandBackground,
            surface = BrandWhite,
            textPrimary = BrandText,
            textSecondary = BrandTextSecondary,
            borderSoft = NeutralBorder,
            primary = BrandOrangeSoft,
            primaryDark = BrandOrange,
            primarySoft = BrandOrangeContainer,
            onPrimary = BrandText,
            secondary = BrandGreen,
            secondarySoft = BrandGreenContainer,
            accent = BrandOrange,
            accentSoft = BrandOrangeContainer,
            error = UrgentRed,
            profileAccent = ProfileGreen
        )

        /** UX-05 Color Direction V3 — experimental. Background approved. Accent under QA. */
        val v3Experimental = LeoVerVisualPalette(
            background = Color(0xFFFAFBF8),
            surface = Color(0xFFFFFFFF),
            textPrimary = Color(0xFF263238),
            textSecondary = Color(0xFF667085),
            borderSoft = Color(0xFFE5EAE4),
            primary = BrandGreen, // 0xFF49B749
            primaryDark = BrandGreenDark,
            primarySoft = Color(0xFFEEF8EE),
            onPrimary = Color(0xFFFFFFFF),
            secondary = BrandGreen,
            secondarySoft = Color(0xFFEEF8EE),
            accent = Color(0xFFEFA066),
            accentSoft = Color(0xFFFAEBDD),
            error = Color(0xFFD96B68),
            profileAccent = BrandGreen
        )
    }
}

val LocalLeoVerVisual = staticCompositionLocalOf { LeoVerVisualPalette.v3Experimental }

@Composable
fun leoVisual(): LeoVerVisualPalette = LocalLeoVerVisual.current

@Composable
fun VisualDirectionPilot(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLeoVerVisual provides LeoVerVisualPalette.v3Experimental) {
        content()
    }
}
