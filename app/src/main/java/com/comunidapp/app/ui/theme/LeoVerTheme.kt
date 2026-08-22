package com.comunidapp.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp

/**
 * Canonical LeoVer design-system entry point (UI-01).
 *
 * New production screens should consume these tokens:
 * `LeoVerTheme.colors.background` — not `Color(0xFFFAFBF8)`.
 *
 * Existing `Brand*` vals remain the source of truth; this object is the
 * documented API so screens stop inventing local palettes.
 */
object LeoVerTheme {
    object colors {
        val background: Color = BrandBackground
        val surface: Color = BrandWhite
        val brandOrange: Color = BrandOrange
        val brandGreen: Color = BrandGreen
        val profileGreen: Color = ProfileGreen
        val softCream: Color = BrandCream
        val textPrimary: Color = BrandText
        val textSecondary: Color = BrandTextSecondary
        val error: Color = UrgentRed
        val warning: Color = WarningAmber
        val success: Color = SuccessGreen
    }

    object spacing {
        val xs: Dp = LeoDimens.SpaceXs
        val s: Dp = LeoDimens.SpaceS
        val m: Dp = LeoDimens.SpaceM
        val l: Dp = LeoDimens.SpaceL
        val xl: Dp = LeoDimens.SpaceXl
        val xxl: Dp = LeoDimens.SpaceXxl
    }

    object shapes {
        val small: Dp = LeoDimens.RadiusChip
        val card: Dp = LeoDimens.RadiusCard
        val large: Dp = LeoDimens.RadiusLarge
        val pill: Dp = LeoDimens.RadiusPill
        val field: Dp = LeoDimens.RadiusField
    }

    object typography {
        val screenTitle: TextStyle = LeoPageTitle
        val sectionTitle: TextStyle = LeoSectionTitle
        val cardTitle: TextStyle = LeoCardTitle
        val body: TextStyle = LeoBody
        val secondaryBody: TextStyle = LeoSecondary
        val caption: TextStyle = LeoCaption
        val button: TextStyle = LeoButton
        val chip: TextStyle = LeoChip
    }
}

/** Theme wrapper alias — same as [ComunidappTheme]; prefer this name in new screens. */
@Composable
fun LeoVerAppTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    ComunidappTheme(darkTheme = darkTheme, content = content)
}
