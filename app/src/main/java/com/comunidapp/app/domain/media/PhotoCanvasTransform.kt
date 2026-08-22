package com.comunidapp.app.domain.media

import kotlinx.serialization.Serializable

enum class PhotoCanvasFitMode {
    FILL,
    FIT
}

/**
 * Single source of truth for Story photo framing.
 * [offsetX]/[offsetY] are fractions of the canvas size, so editor and viewer match
 * regardless of pixel dimensions.
 */
@Serializable
data class PhotoCanvasTransform(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    val fitMode: String = PhotoCanvasFitMode.FILL.name
) {
    val mode: PhotoCanvasFitMode
        get() = runCatching { PhotoCanvasFitMode.valueOf(fitMode) }
            .getOrDefault(PhotoCanvasFitMode.FILL)

    fun pan(dxPx: Float, dyPx: Float, canvasW: Float, canvasH: Float): PhotoCanvasTransform {
        val w = canvasW.coerceAtLeast(1f)
        val h = canvasH.coerceAtLeast(1f)
        return PhotoCanvasMath.clamp(
            copy(offsetX = offsetX + dxPx / w, offsetY = offsetY + dyPx / h)
        )
    }

    fun zoom(factor: Float): PhotoCanvasTransform =
        PhotoCanvasMath.clamp(
            copy(scale = (scale * factor).coerceIn(PhotoCanvasMath.MIN_SCALE, PhotoCanvasMath.MAX_SCALE))
        )

    fun withMode(mode: PhotoCanvasFitMode): PhotoCanvasTransform =
        copy(fitMode = mode.name, offsetX = 0f, offsetY = 0f, scale = 1f)

    fun reset(): PhotoCanvasTransform = PhotoCanvasTransform()

    fun translationX(canvasW: Float): Float = offsetX * canvasW
    fun translationY(canvasH: Float): Float = offsetY * canvasH
}

object PhotoCanvasMath {
    const val MIN_SCALE = 1f
    const val MAX_SCALE = 4f
    const val MAX_OFFSET = 0.48f
    const val ASPECT_RATIO_PRESERVED = true
    const val RESPECT_EXIF_ORIENTATION = true
    const val PREVIEW_MATCHES_PUBLISHED = true

    fun clamp(transform: PhotoCanvasTransform): PhotoCanvasTransform {
        val scale = transform.scale.coerceIn(MIN_SCALE, MAX_SCALE)
        val reach = (MAX_OFFSET * scale).coerceAtMost(0.85f)
        return transform.copy(
            scale = scale,
            offsetX = transform.offsetX.coerceIn(-reach, reach),
            offsetY = transform.offsetY.coerceIn(-reach, reach)
        )
    }
}
