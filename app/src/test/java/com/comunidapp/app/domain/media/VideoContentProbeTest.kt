package com.comunidapp.app.domain.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoContentProbeTest {

    @Test
    fun detectsVideoFromDimensions() {
        assertTrue(VideoContentProbe.hasVideoTrack(durationMs = null, width = 1920, height = 1080, mimeType = null))
    }

    @Test
    fun detectsVideoFromDuration() {
        assertTrue(VideoContentProbe.hasVideoTrack(durationMs = 12_000L, width = null, height = null, mimeType = null))
    }

    @Test
    fun detectsVideoFromMime() {
        assertTrue(
            VideoContentProbe.hasVideoTrack(
                durationMs = null,
                width = null,
                height = null,
                mimeType = "video/mp4"
            )
        )
    }

    @Test
    fun rejectsNonVideoSignals() {
        assertFalse(VideoContentProbe.hasVideoTrack(durationMs = null, width = null, height = null, mimeType = null))
        assertFalse(
            VideoContentProbe.hasVideoTrack(
                durationMs = null,
                width = null,
                height = null,
                mimeType = "application/octet-stream"
            )
        )
    }
}
