package com.comunidapp.app.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.comunidapp.app.domain.media.AvatarPhotoEditorState

enum class LeoVerCropShape { CIRCLE, SQUARE }

/**
 * Shared cropper for onboarding, profile, pet and org avatars.
 * Dims outside the window; the image inside the crop is unmodified.
 */
@Composable
fun LeoVerMediaCropper(
    state: AvatarPhotoEditorState,
    onStateChange: (AvatarPhotoEditorState) -> Unit,
    modifier: Modifier = Modifier,
    viewport: Dp = 280.dp,
    shape: LeoVerCropShape = LeoVerCropShape.CIRCLE
) {
    val clipShape = if (shape == LeoVerCropShape.CIRCLE) CircleShape else RoundedCornerShape(12.dp)
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(viewport)
                .onSizeChanged { size ->
                    if (size.width > 0) {
                        onStateChange(state.copy(viewportPx = size.width.toFloat()))
                    }
                }
                .clip(clipShape)
                .border(2.dp, Color.White.copy(alpha = 0.85f), clipShape)
                .background(Color.Transparent)
                .pointerInput(state.sourceUri) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        onStateChange(state.pan(pan.x, pan.y).zoom(zoom))
                    }
                }
        ) {
            AsyncImage(
                model = state.sourceUri,
                contentDescription = "Ajustar recorte",
                contentScale = ContentScale.Crop,
                onSuccess = { result ->
                    val size = result.painter.intrinsicSize
                    val w = size.width.toInt()
                    val h = size.height.toInt()
                    if (w > 0 && h > 0 && (w != state.imageWidth || h != state.imageHeight)) {
                        onStateChange(state.copy(imageWidth = w, imageHeight = h))
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = state.offsetX
                        translationY = state.offsetY
                        scaleX = state.scale
                        scaleY = state.scale
                    }
            )
        }
    }
}
