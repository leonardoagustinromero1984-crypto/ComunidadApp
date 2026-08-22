package com.comunidapp.app.domain.media

/**
 * Crop math that matches the circular editor preview:
 * ContentScale.Crop, then user pan/zoom around the viewport center.
 */
data class AvatarCropRect(
    val left: Int,
    val top: Int,
    val size: Int
) {
    val right: Int get() = left + size
    val bottom: Int get() = top + size
}

object AvatarPhotoCropMath {

    fun orientedSize(width: Int, height: Int, exifOrientation: Int): Pair<Int, Int> =
        if (swapsAxes(exifOrientation)) height to width else width to height

    fun swapsAxes(exifOrientation: Int): Boolean = when (exifOrientation) {
        5, 6, 7, 8 -> true
        else -> false
    }

    fun sourceCropRect(
        sourceWidth: Int,
        sourceHeight: Int,
        offsetX: Float,
        offsetY: Float,
        scale: Float,
        viewportPx: Float
    ): AvatarCropRect {
        val srcW = sourceWidth.coerceAtLeast(1)
        val srcH = sourceHeight.coerceAtLeast(1)
        val view = viewportPx.coerceAtLeast(1f)
        val userScale = scale.coerceIn(AvatarPhotoEditorState.MIN_SCALE, AvatarPhotoEditorState.MAX_SCALE)
        val fillScale = maxOf(view / srcW, view / srcH)
        val totalScale = (fillScale * userScale).coerceAtLeast(0.0001f)
        val visible = (view / totalScale).coerceAtMost(minOf(srcW, srcH).toFloat())
        val centerX = srcW / 2f - offsetX / totalScale
        val centerY = srcH / 2f - offsetY / totalScale
        val half = visible / 2f
        val left = (centerX - half).coerceIn(0f, srcW - visible)
        val top = (centerY - half).coerceIn(0f, srcH - visible)
        val size = visible.toInt().coerceIn(1, minOf(srcW, srcH))
        return AvatarCropRect(
            left = left.toInt().coerceIn(0, srcW - size),
            top = top.toInt().coerceIn(0, srcH - size),
            size = size
        )
    }

    fun outputSide(preferred: Int = AvatarPhotoEditorState.OUTPUT_SIZE_PX): Int =
        preferred.coerceIn(64, AvatarPhotoEditorState.OUTPUT_SIZE_PX)
}
