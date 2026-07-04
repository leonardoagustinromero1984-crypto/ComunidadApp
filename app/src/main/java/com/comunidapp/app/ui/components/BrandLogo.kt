package com.comunidapp.app.ui.components

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

@Composable
fun BrandLogo(
    modifier: Modifier = Modifier,
    widthFraction: Float = 0.78f,
    height: Dp = 120.dp
) {
    Image(
        painter = painterResource(id = R.drawable.logo_leover),
        contentDescription = stringResource(R.string.brand_name),
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(height),
        contentScale = ContentScale.Fit
    )
}
