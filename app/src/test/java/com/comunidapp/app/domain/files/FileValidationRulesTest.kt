package com.comunidapp.app.domain.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileValidationRulesTest {

    @Test
    fun size_exceeded() {
        val r = FileValidationRules.validateMimeAndExtension(
            purpose = FileAssetPurpose.USER_AVATAR,
            safeFilename = "a.jpg",
            declaredMimeType = "image/jpeg",
            detectedMimeType = null,
            sizeBytes = 201L * 1024 * 1024
        )
        assertTrue(r.isFailure)
        assertEquals("SOURCE_MALICIOUS_SIZE", r.exceptionOrNull()?.message)
    }

    @Test
    fun normal_phone_photo_not_rejected_before_ingest() {
        val r = FileValidationRules.validateMimeAndExtension(
            purpose = FileAssetPurpose.USER_AVATAR,
            safeFilename = "a.jpg",
            declaredMimeType = "image/jpeg",
            detectedMimeType = null,
            sizeBytes = 26L * 1024 * 1024
        )
        assertTrue(r.isSuccess)
    }

    @Test
    fun processed_avatar_over_master_cap_rejected() {
        val r = FileValidationRules.validateProcessedSize(
            FileAssetPurpose.USER_AVATAR,
            6L * 1024 * 1024
        )
        assertTrue(r.isFailure)
        assertEquals("SIZE_EXCEEDED", r.exceptionOrNull()?.message)
    }

    @Test
    fun mime_extension_mismatch() {
        val r = FileValidationRules.validateMimeAndExtension(
            purpose = FileAssetPurpose.USER_AVATAR,
            safeFilename = "a.png",
            declaredMimeType = "image/jpeg",
            detectedMimeType = null,
            sizeBytes = 100
        )
        assertTrue(r.isFailure)
        assertEquals("MIME_EXTENSION_MISMATCH", r.exceptionOrNull()?.message)
    }

    @Test
    fun dangerous_extension_blocked() {
        val r = FileValidationRules.validateMimeAndExtension(
            purpose = FileAssetPurpose.USER_AVATAR,
            safeFilename = "a.exe",
            declaredMimeType = "image/jpeg",
            detectedMimeType = null,
            sizeBytes = 100
        )
        assertTrue(r.isFailure)
    }

    @Test
    fun processing_capabilities_not_claimed() {
        assertFalse(FileValidationRules.imageProcessingAvailable())
        assertFalse(FileValidationRules.antivirusAvailable())
        assertFalse(FileValidationRules.exifStripAvailable())
        assertFalse(FileValidationRules.thumbnailAvailable())
    }

    @Test
    fun count_exceeded_on_request() {
        val req = FileUploadRequest(
            purpose = FileAssetPurpose.USER_AVATAR,
            owner = FileAssetOwner.User("u1"),
            originalFilename = "a.jpg",
            declaredMimeType = "image/jpeg",
            sizeBytes = 100,
            requestedVisibility = FileAssetVisibility.PUBLIC
        )
        val r = FileValidationRules.validateUploadRequest(req, existingCountForResource = 1)
        assertTrue(r.isFailure)
        assertEquals("COUNT_EXCEEDED", r.exceptionOrNull()?.message)
    }

    @Test
    fun heic_source_is_accepted_for_ingest() {
        val r = FileValidationRules.validateMimeAndExtension(
            purpose = FileAssetPurpose.USER_AVATAR,
            safeFilename = "IMG_0001.HEIC",
            declaredMimeType = "image/heic",
            detectedMimeType = null,
            sizeBytes = 18L * 1024 * 1024
        )
        assertTrue(r.isSuccess)
    }
}

class FileNameSanitizerTest {

    @Test
    fun strips_path_and_traversal() {
        assertTrue(FileNameSanitizer.sanitize("../etc/passwd.jpg").isFailure)
        val ok = FileNameSanitizer.sanitize("folder/photo.jpg")
        assertTrue(ok.isSuccess)
        assertEquals("photo.jpg", ok.getOrNull())
    }

    @Test
    fun double_dangerous_extension() {
        val r = FileNameSanitizer.sanitize("invoice.pdf.exe")
        assertTrue(r.isFailure)
        assertEquals("FILENAME_DOUBLE_DANGEROUS", r.exceptionOrNull()?.message)
    }

    @Test
    fun svg_blocked() {
        assertTrue(FileNameSanitizer.sanitize("x.svg").isFailure)
    }

    @Test
    fun spaces_normalized() {
        val r = FileNameSanitizer.sanitize("my  photo.jpg")
        assertTrue(r.isSuccess)
        assertEquals("my_photo.jpg", r.getOrNull())
    }
}
