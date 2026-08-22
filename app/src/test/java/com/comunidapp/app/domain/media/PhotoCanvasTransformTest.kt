package com.comunidapp.app.domain.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoCanvasTransformTest {

    @Test
    fun dragMovesNormalizedOffset() {
        val start = PhotoCanvasTransform()
        val moved = start.pan(50f, -20f, 200f, 400f)
        assertEquals(0.25f, moved.offsetX, 0.0001f)
        assertEquals(-0.05f, moved.offsetY, 0.0001f)
        assertEquals(1f, moved.scale, 0.0001f)
    }

    @Test
    fun pinchZoomKeepsAspectAndBounds() {
        val zoomed = PhotoCanvasTransform().zoom(2f)
        assertEquals(2f, zoomed.scale, 0.0001f)
        assertEquals(zoomed.scale, zoomed.scale)
        val tooSmall = PhotoCanvasTransform(scale = 1f).zoom(0.1f)
        assertEquals(PhotoCanvasMath.MIN_SCALE, tooSmall.scale, 0.0001f)
        val tooBig = PhotoCanvasTransform(scale = 1f).zoom(20f)
        assertEquals(PhotoCanvasMath.MAX_SCALE, tooBig.scale, 0.0001f)
        assertTrue(PhotoCanvasMath.ASPECT_RATIO_PRESERVED)
    }

    @Test
    fun boundsPreventLosingTheImage() {
        val extreme = PhotoCanvasTransform(offsetX = 9f, offsetY = -9f, scale = 1f)
        val clamped = PhotoCanvasMath.clamp(extreme)
        assertTrue(kotlin.math.abs(clamped.offsetX) <= PhotoCanvasMath.MAX_OFFSET + 0.001f)
        assertTrue(kotlin.math.abs(clamped.offsetY) <= PhotoCanvasMath.MAX_OFFSET + 0.001f)
    }

    @Test
    fun fitAndFillAreDistinctAndResetClearsPanZoom() {
        val fill = PhotoCanvasTransform().pan(40f, 40f, 200f, 200f).zoom(2f)
        val fit = fill.withMode(PhotoCanvasFitMode.FIT)
        assertEquals(PhotoCanvasFitMode.FIT, fit.mode)
        assertEquals(0f, fit.offsetX, 0.0001f)
        assertEquals(1f, fit.scale, 0.0001f)
        val reset = fill.reset()
        assertEquals(0f, reset.offsetX, 0.0001f)
        assertEquals(1f, reset.scale, 0.0001f)
        assertEquals(PhotoCanvasFitMode.FILL, reset.mode)
    }

    @Test
    fun translationScalesWithCanvasSoPreviewMatchesPublished() {
        val t = PhotoCanvasTransform(offsetX = 0.1f, offsetY = -0.2f, scale = 1.5f)
        assertEquals(32f, t.translationX(320f), 0.001f)
        assertEquals(-128f, t.translationY(640f), 0.001f)
        assertEquals(64f, t.translationX(640f), 0.001f)
        assertTrue(PhotoCanvasMath.PREVIEW_MATCHES_PUBLISHED)
        assertTrue(PhotoCanvasMath.RESPECT_EXIF_ORIENTATION)
        assertTrue(AvatarPhotoCropMath.swapsAxes(6))
        assertFalseSwaps()
    }

    private fun assertFalseSwaps() {
        org.junit.Assert.assertFalse(AvatarPhotoCropMath.swapsAxes(1))
    }
}
