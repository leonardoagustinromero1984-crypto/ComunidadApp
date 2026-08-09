package com.comunidapp.app.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// LeoVer identidad visual oficial v1.0 — tokens centrales (fuente única)
// Fuente: docs/08-marca/D08-03-Sistema-de-Color-y-UI.md
// ---------------------------------------------------------------------------

val BrandOrange = Color(0xFFFF7A00)
val BrandOrangeSoft = Color(0xFFFFA64D)
val BrandGreen = Color(0xFF49B749)
val BrandGreenDark = Color(0xFF247A3D)
val BrandCream = Color(0xFFFFF6EA)
val BrandText = Color(0xFF2F3A37)
val BrandWhite = Color(0xFFFFFFFF)

/** Contenedor cálido derivado de crema + naranja suave. */
val BrandOrangeContainer = Color(0xFFFFE8CC)

/** Contenedor verde suave derivado de BrandGreen. */
val BrandGreenContainer = Color(0xFFE3F5E3)

/** Verde claro tonal derivado de BrandGreen (no es un verde arbitrario). */
val BrandGreenSoft = Color(0xFF8FD18F)

/** Naranja profundo tonal derivado de BrandOrange. */
val BrandOrangeDeep = Color(0xFFE56E00)

/** Texto secundario / muted derivado de BrandText. */
val BrandTextSecondary = Color(0xFF5C6965)
val MutedText = BrandTextSecondary

/** Borde neutro cálido derivado de crema. */
val NeutralBorder = Color(0xFFE8DFD2)

val BrandGrayLight = Color(0xFFE0E0E0)
val BrandGrayMedium = Color(0xFF9E9E9E)
val BrandGrayDark = Color(0xFF424242)

// Aliases de compatibilidad — UI interna usa naranja suave / verde principal
val OrangePrimary = BrandOrangeSoft
val OrangePrimaryDark = BrandOrange // acento puntual (no superficie grande)
val OrangePrimaryLight = BrandOrangeSoft
val OrangeContainer = BrandOrangeContainer
val GreenPrimary = BrandGreen
val GreenPrimaryDark = BrandGreenDark // reservado: contraste / positivo fuerte
val GreenPrimaryLight = BrandGreenSoft
val GreenContainer = BrandGreenContainer
val White = BrandWhite
val BackgroundLight = BrandCream
val SurfaceLight = BrandWhite
val GrayLight = BrandGrayLight
val GrayMedium = BrandGrayMedium
val GrayDark = BrandGrayDark
val TextPrimary = BrandText
val TextSecondary = BrandTextSecondary
val OrangeAccent = BrandOrangeSoft
val CyanAccent = BrandGreen
val CyanContainer = BrandGreenContainer

// Estados semánticos (no sustituir por naranja/verde de marca)
val UrgentRed = Color(0xFFE53935)
val UrgentContainer = Color(0xFFFFEBEE)
val SuccessGreen = BrandGreen
val WarningAmber = Color(0xFFED6C02)
/** Alias legacy: ámbar semántico (no es BrandOrange). */
val WarningOrange = WarningAmber

// Dark theme (futuro)
val OrangePrimaryDarkTheme = BrandOrangeSoft
val GreenPrimaryDarkTheme = BrandGreen
val BackgroundDark = Color(0xFF121212)
val SurfaceDark = Color(0xFF1E1E1E)
