package com.comunidapp.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.comunidapp.app.R

enum class BrandLogoVariant {
    /** Imagotipo vertical oficial (login, onboarding, presentación). */
    Vertical,
    /** Imagotipo horizontal (encabezados anchos). */
    Horizontal,
    /** Solo isotipo (espacios reducidos). */
    Isotype
}

@Composable
fun BrandLogo(
    modifier: Modifier = Modifier,
    widthFraction: Float = 0.78f,
    height: Dp = 120.dp,
    variant: BrandLogoVariant = BrandLogoVariant.Vertical
) {
    Image(
        painter = painterResource(id = variant.drawableRes),
        contentDescription = stringResource(R.string.brand_name),
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(height),
        contentScale = ContentScale.Fit
    )
}

private val BrandLogoVariant.drawableRes: Int
    @DrawableRes
    get() = when (this) {
        BrandLogoVariant.Vertical -> R.drawable.leover_logo_official
        BrandLogoVariant.Horizontal -> R.drawable.leover_logo_horizontal
        BrandLogoVariant.Isotype -> R.drawable.leover_isotype_official
    }
