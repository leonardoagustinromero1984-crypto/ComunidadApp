package com.comunidapp.app.domain.media

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoCanvasTransformResetTest {
    @Test
    fun resetReturnsInitialTransform() {
        val moved = PhotoCanvasTransform(offsetX = 0.2f, offsetY = -0.1f, scale = 2.4f, fitMode = PhotoCanvasFitMode.FIT.name)
        assertEquals(PhotoCanvasTransform(), moved.reset())
    }
}
