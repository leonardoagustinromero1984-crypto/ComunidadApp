package com.comunidapp.app.domain.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoExportPolicyTest {

    @Test
    fun qaEightSecondCameraOriginalsRequireTranscode() {
        val sizeA = 15_706_082L
        val sizeB = 30_723_645L
        val durationMs = 8_000L
        assertEquals(15_706_082L, VideoExportPolicy.bitrateBps(sizeA, durationMs))
        assertEquals(30_723_645L, VideoExportPolicy.bitrateBps(sizeB, durationMs))
        assertFalse(
            VideoExportPolicy.shouldPassthrough(
                maxEdge = 1920,
                bitrateBps = VideoExportPolicy.bitrateBps(sizeA, durationMs),
                sizeBytes = sizeA,
                mimeOrCodec = "video/mp4 avc",
                durationMs = durationMs
            )
        )
        assertFalse(
            VideoExportPolicy.shouldPassthrough(
                maxEdge = 3840,
                bitrateBps = VideoExportPolicy.bitrateBps(sizeB, durationMs),
                sizeBytes = sizeB,
                mimeOrCodec = "video/mp4",
                durationMs = durationMs
            )
        )
    }

    @Test
    fun optimizedSocialReelPassesThrough() {
        assertTrue(
            VideoExportPolicy.shouldPassthrough(
                maxEdge = 1080,
                bitrateBps = 4_500_000L,
                sizeBytes = 6L * 1024 * 1024,
                mimeOrCodec = "video/avc",
                durationMs = 8_000L
            )
        )
        assertFalse(
            VideoExportPolicy.shouldPassthrough(
                maxEdge = 720,
                bitrateBps = 5_000_000L,
                sizeBytes = 4L * 1024 * 1024,
                mimeOrCodec = "video/hevc",
                durationMs = 8_000L
            )
        )
        assertFalse(VideoExportPolicy.needsScale(1080))
        assertTrue(VideoExportPolicy.needsScale(1920))
        assertFalse(
            VideoExportPolicy.shouldPassthrough(
                maxEdge = 1080,
                bitrateBps = 4_500_000L,
                sizeBytes = 6L * 1024 * 1024,
                mimeOrCodec = "video/mp4",
                durationMs = 8_000L
            )
        )
        assertFalse(
            VideoExportPolicy.shouldPassthrough(
                maxEdge = 1080,
                bitrateBps = 6_000_000L,
                sizeBytes = 8L * 1024 * 1024,
                mimeOrCodec = "video/avc",
                durationMs = 8_000L
            )
        )
        assertTrue(
            VideoExportPolicy.shouldPassthrough(
                maxEdge = 720,
                bitrateBps = 3_500_000L,
                sizeBytes = 5L * 1024 * 1024,
                mimeOrCodec = "video/avc",
                durationMs = 15_000L
            )
        )
        assertFalse(
            VideoExportPolicy.shouldPassthrough(
                maxEdge = 720,
                bitrateBps = 4_500_000L,
                sizeBytes = 8L * 1024 * 1024,
                mimeOrCodec = "video/avc",
                durationMs = 15_000L
            )
        )
        assertEquals(4_500_000L, VideoExportPolicy.targetBitrateBps(1080))
        assertEquals(3_500_000L, VideoExportPolicy.targetBitrateBps(720))
        assertEquals(5_000_000L, VideoExportPolicy.maxPassthroughBitrateBps(1080))
        assertEquals(4_000_000L, VideoExportPolicy.maxPassthroughBitrateBps(720))
    }

    @Test
    fun durationAloneDoesNotRejectFiftySecondOptimizedReel() {
        val durationMs = 50_000L
        val sizeBytes = 4_500_000L * durationMs / 8_000L
        assertTrue(
            VideoExportPolicy.shouldPassthrough(
                maxEdge = 1080,
                bitrateBps = 4_500_000L,
                sizeBytes = sizeBytes,
                mimeOrCodec = "video/avc",
                durationMs = durationMs
            )
        )
        assertTrue(sizeBytes < VideoExportPolicy.PROCESSED_HARD_CAP_BYTES)
        assertTrue(15_706_082L < VideoExportPolicy.RAW_REJECT_BYTES)
    }

    @Test
    fun exportTempCleanupOnlyDeletesPolicyOutputs() {
        val export = java.io.File.createTempFile("leover_export_", ".mp4")
        val original = java.io.File.createTempFile("camera_original_", ".mp4")
        try {
            assertTrue(VideoExportPolicy.isExportOutputName(export.name))
            assertFalse(VideoExportPolicy.isExportOutputName(original.name))
            VideoExportPolicy.deleteExportOutput(export.absolutePath)
            VideoExportPolicy.deleteExportOutput(original.absolutePath)
            assertFalse(export.exists())
            assertTrue(original.exists())
        } finally {
            export.delete()
            original.delete()
        }
    }
}
