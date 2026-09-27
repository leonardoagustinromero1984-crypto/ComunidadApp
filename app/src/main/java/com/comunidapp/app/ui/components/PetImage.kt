package com.comunidapp.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size

@Composable
fun PetImage(
    imageUrl: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    decodeSize: Dp? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl.isNullOrBlank()) {
            Icon(
                imageVector = Icons.Default.Pets,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        } else {
            val decodePx = decodeSize?.let { with(density) { it.roundToPx().coerceAtLeast(1) * 2 } } ?: 0
            val request = remember(imageUrl, decodePx) {
                val builder = ImageRequest.Builder(context).data(imageUrl).crossfade(true)
                if (decodePx > 0) builder.size(Size(decodePx, decodePx))
                builder.build()
            }
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale
            )
        }
    }
}
