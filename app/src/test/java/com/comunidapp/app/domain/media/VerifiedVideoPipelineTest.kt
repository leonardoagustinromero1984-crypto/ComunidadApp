package com.comunidapp.app.domain.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VerifiedVideoPipelineTest {

    @Test
    fun mapsVideoAvcTrackMimeToMp4Container() {
        assertEquals("video/mp4", VerifiedVideoPipeline.normalizeContainerMime("video/avc"))
        assertEquals("video/mp4", VerifiedVideoPipeline.normalizeContainerMime("video/hevc"))
    }

    @Test
    fun mapsBareAvcCodecToMp4Container() {
        assertEquals("video/mp4", VerifiedVideoPipeline.normalizeContainerMime("avc1"))
    }

    @Test
    fun extensionForMimeMapsWebmAndMp4() {
        assertEquals("webm", VerifiedVideoPipeline.extensionForMime("video/webm"))
        assertEquals("mp4", VerifiedVideoPipeline.extensionForMime("video/mp4"))
    }

    @Test
    fun octetStreamMimeAloneIsNotVideoWithoutTrack() {
        assertTrue(
            !VideoContentProbe.hasVideoTrack(
                durationMs = null,
                width = null,
                height = null,
                mimeType = "application/octet-stream"
            )
        )
    }
}
