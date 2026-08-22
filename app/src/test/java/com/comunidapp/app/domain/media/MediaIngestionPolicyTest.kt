package com.comunidapp.app.domain.media

import com.comunidapp.app.domain.files.FileAssetPurpose
import com.comunidapp.app.domain.files.ResumableUploadPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaIngestionPolicyTest {

    @Test
    fun NORMAL_PHONE_PHOTO_IS_NOT_USER_REJECTED() {
        assertFalse(MediaIngestionPolicy.NORMAL_PHONE_PHOTO_USER_REJECTION)
        assertNull(MediaIngestionPolicy.rejectSource(12L * 1024 * 1024, "image/jpeg"))
        assertNull(MediaIngestionPolicy.rejectSource(30L * 1024 * 1024, "image/jpeg"))
        assertEquals("SOURCE_MALICIOUS_SIZE", MediaIngestionPolicy.rejectSource(201L * 1024 * 1024, "image/jpeg"))
    }

    @Test
    fun LARGE_12MP_DOWNSAMPLES_TO_MASTER() {
        val (w, h) = MediaIngestionPolicy.outputSize(4032, 3024, MediaIngestionPolicy.AVATAR_MASTER_EDGE_PX)
        assertTrue(w <= MediaIngestionPolicy.AVATAR_MASTER_EDGE_PX)
        assertTrue(h <= MediaIngestionPolicy.AVATAR_MASTER_EDGE_PX)
        assertEquals(1, MediaIngestionPolicy.sampleSize(800, 600, 1280))
        assertTrue(MediaIngestionPolicy.sampleSize(8000, 6000, 1024) >= 4)
    }

    @Test
    fun EXIF_ROTATED_DIMENSIONS_SWAP() {
        assertEquals(3024 to 4032, AvatarPhotoCropMath.orientedSize(4032, 3024, 6))
        assertTrue(AvatarPhotoCropMath.swapsAxes(6))
    }

    @Test
    fun LANDSCAPE_SCALE_ONE_CAN_PAN() {
        val before = AvatarPhotoEditorState("file://x", imageWidth = 2000, imageHeight = 1200)
        val panned = before.pan(80f, 0f)
        assertTrue(panned.offsetX > 0f)
        val cropA = AvatarPhotoCropMath.sourceCropRect(2000, 1200, 0f, 0f, 1f, 280f)
        val cropB = AvatarPhotoCropMath.sourceCropRect(2000, 1200, panned.offsetX, 0f, 1f, 280f)
        assertTrue(cropB.left != cropA.left || cropB.top != cropA.top)
    }

    @Test
    fun SQUARE_SCALE_ONE_HAS_NO_PAN() {
        val limits = CoverCropMath.panLimits(280f, 1f, 1000, 1000)
        assertEquals(0f, limits.maxX, 0.01f)
        assertEquals(0f, limits.maxY, 0.01f)
    }

    @Test
    fun PORTRAIT_SCALE_ONE_PANS_VERTICALLY() {
        val limits = CoverCropMath.panLimits(280f, 1f, 1200, 2000)
        assertEquals(0f, limits.maxX, 0.01f)
        assertTrue(limits.maxY > 0f)
    }

    @Test
    fun TUS_ROUTING_FOR_VIDEO_AND_LARGE_FILES() {
        assertFalse(MediaIngestionPolicy.shouldUseTus(5L * 1024 * 1024))
        assertTrue(MediaIngestionPolicy.shouldUseTus(7L * 1024 * 1024))
        assertEquals(ResumableUploadPolicy.STANDARD_THRESHOLD_BYTES, MediaIngestionPolicy.STANDARD_UPLOAD_THRESHOLD_BYTES)
        assertTrue(MediaIngestionPolicy.shouldUseTus(40L * 1024 * 1024))
    }

    @Test
    fun VIDEO_4K_NORMALIZES_TO_1080() {
        val (w, h) = MediaIngestionPolicy.outputSize(3840, 2160, MediaIngestionPolicy.VIDEO_TARGET_EDGE_PX)
        assertEquals(1080, w)
        assertTrue(h <= 1080)
        assertEquals(1080, com.comunidapp.app.domain.social.VideoExportPolicy.TARGET_MAX_EDGE)
    }

    @Test
    fun HEIC_IS_INGESTED_NOT_UPLOADED_RAW() {
        assertTrue(MediaIngestionPolicy.isHeic("image/heic"))
        assertTrue(MediaIngestionPolicy.normalizesBeforeUpload(FileAssetPurpose.USER_AVATAR, "image/heic"))
        assertTrue(ImageIngestPolicy.shouldIngest(FileAssetPurpose.USER_AVATAR, "image/heic"))
    }

    @Test
    fun CORRUPT_JPEG_HAS_NO_GPS_AND_IS_NOT_EXIF() {
        val corrupt = byteArrayOf(0x00, 0x01, 0x02, 0x03)
        assertFalse(AvatarPhotoJpeg.containsGpsExif(corrupt))
        val truncated = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00)
        assertFalse(AvatarPhotoJpeg.containsGpsExif(truncated))
    }

    @Test
    fun DESTINATION_CAPS_MATCH_STAGING_BUCKETS() {
        assertEquals(5L * 1024 * 1024, MediaIngestionPolicy.processedMaxBytes(FileAssetPurpose.USER_AVATAR))
        assertEquals(8L * 1024 * 1024, MediaIngestionPolicy.processedMaxBytes(FileAssetPurpose.PET_AVATAR))
        assertEquals(5L * 1024 * 1024, MediaIngestionPolicy.processedMaxBytes(FileAssetPurpose.ORGANIZATION_LOGO))
        assertNotEquals(MediaIngestionPolicy.MALICIOUS_SOURCE_MAX_BYTES, MediaIngestionPolicy.AVATAR_MASTER_MAX_BYTES)
    }
}
