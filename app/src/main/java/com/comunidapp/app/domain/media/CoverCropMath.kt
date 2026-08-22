package com.comunidapp.app.domain.media

/**
 * Cover-crop pan/zoom that matches what the user sees in [LeoVerMediaCropper].
 * At scale 1 a non-square image still has overflow on the long axis — pan must work.
 */
data class CoverPanLimits(
    val maxX: Float,
    val maxY: Float
)

object CoverCropMath {

    fun panLimits(
        viewportPx: Float,
        scale: Float,
        imageWidth: Int,
        imageHeight: Int
    ): CoverPanLimits {
        val view = viewportPx.coerceAtLeast(1f)
        val srcW = imageWidth.coerceAtLeast(1)
        val srcH = imageHeight.coerceAtLeast(1)
        val userScale = scale.coerceIn(AvatarPhotoEditorState.MIN_SCALE, AvatarPhotoEditorState.MAX_SCALE)
        val cover = maxOf(view / srcW, view / srcH)
        val displayW = srcW * cover * userScale
        val displayH = srcH * cover * userScale
        return CoverPanLimits(
            maxX = ((displayW - view) / 2f).coerceAtLeast(0f),
            maxY = ((displayH - view) / 2f).coerceAtLeast(0f)
        )
    }
}
