package com.comunidapp.app.domain.media

/**
 * Crop/reposition state for the profile photo editor.
 * Coordinates are relative to the displayed image, not raw pixels.
 */
data class AvatarPhotoEditorState(
    val sourceUri: String,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    val viewportPx: Float = 280f,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0
) {
    fun pan(dx: Float, dy: Float): AvatarPhotoEditorState {
        val nextScale = scale.coerceIn(MIN_SCALE, MAX_SCALE)
        val limits = panLimits(viewportPx, nextScale, imageWidth, imageHeight)
        return copy(
            offsetX = (offsetX + dx).coerceIn(-limits.maxX, limits.maxX),
            offsetY = (offsetY + dy).coerceIn(-limits.maxY, limits.maxY),
            scale = nextScale
        )
    }

    fun zoom(factor: Float): AvatarPhotoEditorState {
        val nextScale = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        val limits = panLimits(viewportPx, nextScale, imageWidth, imageHeight)
        return copy(
            scale = nextScale,
            offsetX = offsetX.coerceIn(-limits.maxX, limits.maxX),
            offsetY = offsetY.coerceIn(-limits.maxY, limits.maxY)
        )
    }

    fun recenter(): AvatarPhotoEditorState = copy(offsetX = 0f, offsetY = 0f, scale = 1f)

    companion object {
        const val MIN_SCALE = 1f
        const val MAX_SCALE = 4f
        const val OUTPUT_SIZE_PX = 1024
        const val DECODE_MAX_SIDE_PX = 4096

        fun panLimits(
            viewportPx: Float,
            scale: Float,
            imageWidth: Int,
            imageHeight: Int
        ): CoverPanLimits = CoverCropMath.panLimits(viewportPx, scale, imageWidth, imageHeight)

        fun maxPanPx(viewportPx: Float, scale: Float, imageWidth: Int = 0, imageHeight: Int = 0): Float {
            val limits = panLimits(viewportPx, scale, imageWidth, imageHeight)
            return maxOf(limits.maxX, limits.maxY)
        }
    }
}

object AvatarPhotoRules {
    const val PICK_OPENS_EDITOR = true
    const val CANCEL_DOES_NOT_REPLACE = true
    const val STRIP_GPS_METADATA = true
    const val OUTPUT_JPEG_QUALITY = 88
}
