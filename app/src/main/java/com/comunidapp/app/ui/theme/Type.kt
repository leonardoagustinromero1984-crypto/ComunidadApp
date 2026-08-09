package com.comunidapp.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Tipografía LeoVer — jerarquía RC1.2 */
val LeoPageTitle = TextStyle(
    fontWeight = FontWeight.Bold,
    fontSize = 26.sp,
    lineHeight = 34.sp
)
val LeoSectionTitle = TextStyle(
    fontWeight = FontWeight.SemiBold,
    fontSize = 20.sp,
    lineHeight = 28.sp
)
val LeoCardTitle = TextStyle(
    fontWeight = FontWeight.SemiBold,
    fontSize = 17.sp,
    lineHeight = 24.sp
)
val LeoBody = TextStyle(
    fontWeight = FontWeight.Normal,
    fontSize = 15.sp,
    lineHeight = 22.sp
)
val LeoCaption = TextStyle(
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    lineHeight = 18.sp
)
val LeoNavLabel = TextStyle(
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 16.sp
)

val Typography = Typography(
    headlineLarge = LeoPageTitle.copy(fontSize = 28.sp, lineHeight = 36.sp),
    headlineMedium = LeoPageTitle,
    titleLarge = LeoSectionTitle,
    titleMedium = LeoCardTitle,
    bodyLarge = LeoBody.copy(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = LeoBody,
    bodySmall = LeoCaption,
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = LeoNavLabel,
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp)
)
