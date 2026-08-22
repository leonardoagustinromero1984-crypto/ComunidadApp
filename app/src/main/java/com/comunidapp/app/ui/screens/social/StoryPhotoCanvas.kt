package com.comunidapp.app.ui.screens.social

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.zIndex
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.comunidapp.app.domain.media.PhotoCanvasFitMode
import com.comunidapp.app.domain.media.PhotoCanvasMath
import com.comunidapp.app.domain.media.PhotoCanvasTransform
import com.comunidapp.app.ui.theme.LeoCaption

@Composable
fun StoryPhotoCanvas(
    imageModel: Any,
    transform: PhotoCanvasTransform,
    modifier: Modifier = Modifier,
    contentDescription: String? = "Foto",
    editable: Boolean = false,
    onTransformChange: (PhotoCanvasTransform) -> Unit = {},
    onReset: (() -> Unit)? = null
) {
    var canvas by remember { mutableStateOf(IntSize.Zero) }
    val context = LocalContext.current
    val request = remember(imageModel) {
        ImageRequest.Builder(context)
            .data(imageModel)
            .crossfade(true)
            .build()
    }
    val scale = if (transform.mode == PhotoCanvasFitMode.FILL) {
        ContentScale.Crop
    } else {
        ContentScale.Fit
    }
    Box(
        modifier = modifier
            .clipToBounds()
            .background(Color.Black)
            .onSizeChanged { canvas = it }
    ) {
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            contentScale = scale,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = transform.translationX(size.width)
                    translationY = transform.translationY(size.height)
                    scaleX = transform.scale
                    scaleY = transform.scale
                }
                .then(
                    if (editable) {
                        Modifier
                            .pointerInput(canvas) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val w = canvas.width.toFloat().coerceAtLeast(1f)
                                    val h = canvas.height.toFloat().coerceAtLeast(1f)
                                    onTransformChange(transform.pan(pan.x, pan.y, w, h).zoom(zoom))
                                }
                            }
                            .pointerInput(transform.fitMode) {
                                detectTapGestures(
                                    onDoubleTap = { (onReset ?: { onTransformChange(transform.reset()) }).invoke() }
                                )
                            }
                    } else {
                        Modifier
                    }
                )
        )
        if (editable) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(2f)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CanvasModeChip(
                    label = "Rellenar",
                    selected = transform.mode == PhotoCanvasFitMode.FILL,
                    onClick = { onTransformChange(transform.withMode(PhotoCanvasFitMode.FILL)) }
                )
                CanvasModeChip(
                    label = "Ajustar",
                    selected = transform.mode == PhotoCanvasFitMode.FIT,
                    onClick = { onTransformChange(transform.withMode(PhotoCanvasFitMode.FIT)) }
                )
                CanvasModeChip(
                    label = "Restablecer",
                    selected = false,
                    onClick = { (onReset ?: { onTransformChange(transform.reset()) }).invoke() }
                )
            }
        }
        @Suppress("UNUSED_VARIABLE")
        val preserved = PhotoCanvasMath.ASPECT_RATIO_PRESERVED && PhotoCanvasMath.RESPECT_EXIF_ORIENTATION
    }
}

@Composable
private fun CanvasModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) Color.White else Color.Black.copy(alpha = 0.55f)
    ) {
        Text(
            text = label,
            style = LeoCaption,
            color = if (selected) Color.Black else Color.White,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
