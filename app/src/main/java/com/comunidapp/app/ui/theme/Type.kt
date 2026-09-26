package com.comunidapp.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.comunidapp.app.R

/** Nunito Sans — tipografía principal LeoVer */
val NunitoSans = FontFamily(
    Font(R.font.nunito_sans_regular, FontWeight.Normal),
    Font(R.font.nunito_sans_medium, FontWeight.Medium),
    Font(R.font.nunito_sans_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_sans_bold, FontWeight.Bold),
    Font(R.font.nunito_sans_extrabold, FontWeight.ExtraBold)
)

private fun nunito(
    weight: FontWeight,
    size: Int,
    lineHeight: Int
) = TextStyle(
    fontFamily = NunitoSans,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp
)

/** Tipografía LeoVer — Design System 2.0 */
val LeoDisplay = nunito(FontWeight.Bold, 28, 36)
val LeoPageTitle = nunito(FontWeight.SemiBold, 22, 28)
val LeoScreenTitle = LeoPageTitle
val LeoSectionTitle = nunito(FontWeight.SemiBold, 18, 24)
val LeoCardTitle = nunito(FontWeight.SemiBold, 16, 22)
val LeoBody = nunito(FontWeight.Normal, 15, 22)
val LeoSecondary = nunito(FontWeight.Normal, 14, 20)
val LeoCaption = nunito(FontWeight.Normal, 13, 18)
val LeoButton = nunito(FontWeight.SemiBold, 16, 22)
val LeoChip = nunito(FontWeight.Medium, 13, 18)
val LeoNavLabel = nunito(FontWeight.SemiBold, 12, 16)

val Typography = Typography(
    displayMedium = LeoDisplay,
    headlineLarge = LeoPageTitle.copy(fontSize = 28.sp, lineHeight = 36.sp),
    headlineMedium = LeoPageTitle,
    titleLarge = LeoSectionTitle,
    titleMedium = LeoCardTitle,
    bodyLarge = LeoBody.copy(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = LeoBody,
    bodySmall = LeoCaption,
    labelLarge = LeoButton,
    labelMedium = LeoNavLabel,
    labelSmall = nunito(FontWeight.Medium, 11, 14)
)

