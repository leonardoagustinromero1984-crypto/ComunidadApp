package com.comunidapp.app.domain.media

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoCanvasTransformResetTest {
    @Test
    fun resetReturnsInitialTransform() {
        val moved = PhotoCanvasTransform(offsetX = 0.2f, offsetY = -0.1f, scale = 2.4f, fitMode = PhotoCanvasFitMode.FIT.name)
        val reset = moved.reset()
        assertEquals(0f, reset.offsetX)
        assertEquals(0f, reset.offsetY)
        assertEquals(1f, reset.scale)
        assertEquals(PhotoCanvasFitMode.FIT.name, reset.fitMode)
        assertEquals(
            PhotoCanvasFitMode.FILL,
            moved.withMode(PhotoCanvasFitMode.FILL).mode
        )
    }
}
