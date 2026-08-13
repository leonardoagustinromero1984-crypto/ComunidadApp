package com.comunidapp.shared.media

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private sealed interface ImageUiState {
    data object Idle : ImageUiState
    data object Loading : ImageUiState
    data class Ready(val bitmap: ImageBitmap) : ImageUiState
    data object Placeholder : ImageUiState
}

/**
 * Renderer CMP SAFE — resuelve [MediaRef] sin exponer URLs firmadas en UI.
 */
@Composable
fun SharedRemoteImage(
    mediaRef: MediaRef?,
    mediaResolver: MediaResolver?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp
) {
    var state by remember(mediaRef, mediaResolver) { mutableStateOf<ImageUiState>(ImageUiState.Idle) }

    LaunchedEffect(mediaRef, mediaResolver) {
        if (mediaRef == null || mediaResolver == null) {
            state = ImageUiState.Placeholder
            return@LaunchedEffect
        }
        state = ImageUiState.Loading
        when (val result = mediaResolver.resolve(mediaRef)) {
            is MediaResolveResult.Success -> {
                val bitmap = decodeImageBytes(result.resource.bytes)
                state = if (bitmap != null) ImageUiState.Ready(bitmap) else ImageUiState.Placeholder
            }
            else -> state = ImageUiState.Placeholder
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        when (val s = state) {
            ImageUiState.Idle, ImageUiState.Placeholder -> {
                Text(
                    "Sin foto",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ImageUiState.Loading -> CircularProgressIndicator()
            is ImageUiState.Ready -> {
                Image(
                    bitmap = s.bitmap,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}
