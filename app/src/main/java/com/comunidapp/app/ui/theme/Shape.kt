package com.comunidapp.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes

/** Formas Material3 alineadas a LeoVer UI V2 / LeoDimens. */
val LeoShapes = Shapes(
    extraSmall = RoundedCornerShape(LeoDimens.RadiusChip / 2),
    small = RoundedCornerShape(LeoDimens.RadiusChip),
    medium = RoundedCornerShape(LeoDimens.RadiusField),
    large = RoundedCornerShape(LeoDimens.RadiusCard),
    extraLarge = RoundedCornerShape(LeoDimens.RadiusLarge)
)
