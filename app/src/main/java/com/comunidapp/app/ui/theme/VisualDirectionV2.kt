package com.comunidapp.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Active visual palette — LeoVer Design System 2.0.
 *
 * Orange is the CTA / selection accent. Green is success / positive.
 * Surfaces stay white / off-white. Sage/teal V2 primaries stay retired.
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

        /** Design System 2.0 — orange CTA, green success, warm neutrals. */
        val v3Experimental = LeoVerVisualPalette(
            background = BrandBackground, // 0xFFFAFBF8
            surface = BrandWhite,
            textPrimary = BrandText,
            textSecondary = BrandTextSecondary,
            borderSoft = NeutralBorder,
            primary = BrandOrange,
            primaryDark = BrandOrangeDeep,
            primarySoft = BrandOrangeContainer,
            onPrimary = BrandWhite,
            secondary = BrandGreen, // 0xFF49B749
            secondarySoft = BrandGreenContainer,
            accent = BrandOrange,
            accentSoft = BrandOrangeContainer,
            error = UrgentRed,
            profileAccent = ProfileGreen
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
