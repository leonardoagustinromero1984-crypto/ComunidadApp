package com.comunidapp.app.ui.screens.social

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
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
    val currentTransform by rememberUpdatedState(transform)
    val currentOnChange by rememberUpdatedState(onTransformChange)
    val currentOnReset by rememberUpdatedState(onReset)
    val request = remember(imageModel) {
        ImageRequest.Builder(context)
            .data(imageModel)
            .crossfade(true)
            .build()
    }
    val contentScale = if (transform.mode == PhotoCanvasFitMode.FILL) {
        ContentScale.Crop
    } else {
        ContentScale.Fit
    }
    // Consume parent verticalScroll while editing so pinch/pan stay on the canvas.
    val blockParentScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                if (editable) available else Offset.Zero

            override suspend fun onPreFling(available: Velocity): Velocity =
                if (editable) available else Velocity.Zero
        }
    }
    Box(
        modifier = modifier
            .clipToBounds()
            .background(Color.Black)
            .onSizeChanged { canvas = it }
            .then(if (editable) Modifier.nestedScroll(blockParentScroll) else Modifier)
            .then(
                if (editable) {
                    Modifier.pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val w = canvas.width.toFloat().coerceAtLeast(1f)
                            val h = canvas.height.toFloat().coerceAtLeast(1f)
                            currentOnChange(
                                currentTransform.pan(pan.x, pan.y, w, h).zoom(zoom)
                            )
                        }
                    }
                } else {
                    Modifier
                }
            )
            .then(
                if (editable) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                (currentOnReset ?: {
                                    currentOnChange(currentTransform.reset())
                                }).invoke()
                            }
                        )
                    }
                } else {
                    Modifier
                }
            )
    ) {
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = transform.translationX(size.width)
                    translationY = transform.translationY(size.height)
                    scaleX = transform.scale
                    scaleY = transform.scale
                }
        )
        if (editable) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(2f)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
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
                val hasTransform =
                    transform.offsetX != 0f || transform.offsetY != 0f || transform.scale != 1f
                if (hasTransform) {
                    Surface(
                        onClick = {
                            (onReset ?: { onTransformChange(transform.reset()) }).invoke()
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Black.copy(alpha = 0.35f)
                    ) {
                        Text(
                            text = "↺ Restablecer",
                            style = LeoCaption,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
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
