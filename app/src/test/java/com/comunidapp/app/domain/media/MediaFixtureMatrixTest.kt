package com.comunidapp.app.domain.media

import com.comunidapp.app.domain.files.FileAssetPurpose
import com.comunidapp.app.domain.files.FileValidationRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Generated fixtures (not checked into the repo as binaries).
 * Full Bitmap decode of 12MP/HEIC remains PHYSICAL QA REQUIRED.
 */
class MediaFixtureMatrixTest {

    @Test
    fun small_jpeg_is_accepted_as_source() {
        val jpeg = jpegMarker(64)
        assertTrue(jpeg.size < 1024)
        assertTrue(startsWithJpegSoi(jpeg))
        val r = FileValidationRules.validateMimeAndExtension(
            purpose = FileAssetPurpose.USER_AVATAR,
            safeFilename = "small.jpg",
            declaredMimeType = "image/jpeg",
            detectedMimeType = null,
            sizeBytes = jpeg.size.toLong()
        )
        assertTrue(r.isSuccess)
    }

    @Test
    fun jpeg_12mp_dimensions_downsample_to_avatar_master() {
        val (w, h) = MediaIngestionPolicy.outputSize(4032, 3024, MediaIngestionPolicy.AVATAR_MASTER_EDGE_PX)
        assertTrue(w <= 1024)
        assertTrue(h <= 1024)
        assertEquals(1024, maxOf(w, h))
    }

    @Test
    fun jpeg_15_to_30mb_is_not_user_rejected() {
        assertEquals(null, MediaIngestionPolicy.rejectSource(15L * 1024 * 1024, "image/jpeg"))
        assertEquals(null, MediaIngestionPolicy.rejectSource(30L * 1024 * 1024, "image/jpeg"))
        val r = FileValidationRules.validateMimeAndExtension(
            purpose = FileAssetPurpose.USER_AVATAR,
            safeFilename = "camera.jpg",
            declaredMimeType = "image/jpeg",
            detectedMimeType = null,
            sizeBytes = 28L * 1024 * 1024
        )
        assertTrue(r.isSuccess)
    }

    @Test
    fun portrait_landscape_square_and_png_webp_policies() {
        val portrait = CoverCropMath.panLimits(280f, 1f, 1200, 2000)
        assertTrue(portrait.maxY > 0f)
        val landscape = CoverCropMath.panLimits(280f, 1f, 2000, 1200)
        assertTrue(landscape.maxX > 0f)
        val square = CoverCropMath.panLimits(280f, 1f, 1000, 1000)
        assertEquals(0f, square.maxX, 0.01f)
        assertTrue(MediaIngestionPolicy.normalizesBeforeUpload(FileAssetPurpose.USER_AVATAR, "image/png"))
        assertTrue(MediaIngestionPolicy.normalizesBeforeUpload(FileAssetPurpose.POST_MEDIA, "image/webp"))
    }

    @Test
    fun corrupt_bytes_are_not_exif_and_malicious_size_fails() {
        val corrupt = byteArrayOf(0x00, 0x01, 0x02)
        assertFalse(startsWithJpegSoi(corrupt))
        assertFalse(AvatarPhotoJpeg.containsGpsExif(corrupt))
        assertEquals(
            "SOURCE_MALICIOUS_SIZE",
            MediaIngestionPolicy.rejectSource(201L * 1024 * 1024, "image/jpeg")
        )
    }

    @Test
    fun video_routing_uses_tus_above_threshold() {
        assertFalse(MediaIngestionPolicy.shouldUseTus(720L * 1024))
        assertTrue(MediaIngestionPolicy.shouldUseTus(8L * 1024 * 1024))
        val fourK = MediaIngestionPolicy.outputSize(3840, 2160, MediaIngestionPolicy.VIDEO_TARGET_EDGE_PX)
        assertEquals(1080, fourK.first)
        val portrait = MediaIngestionPolicy.outputSize(1080, 1920, MediaIngestionPolicy.VIDEO_TARGET_EDGE_PX)
        assertEquals(1080, portrait.second)
    }

    private fun startsWithJpegSoi(bytes: ByteArray): Boolean =
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()

    private fun jpegMarker(payload: Int): ByteArray {
        val body = ByteArray(payload.coerceAtLeast(4)) { 0x00 }
        body[0] = 0xFF.toByte()
        body[1] = 0xD8.toByte()
        body[body.lastIndex - 1] = 0xFF.toByte()
        body[body.lastIndex] = 0xD9.toByte()
        return body
    }
}
