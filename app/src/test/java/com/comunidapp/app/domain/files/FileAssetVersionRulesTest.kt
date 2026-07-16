package com.comunidapp.app.domain.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileAssetVersionRulesTest {

    @Test
    fun valid_version() {
        val r = FileAssetVersionRules.validate(
            id = "v1",
            assetId = "a1",
            logicalBucket = FileLogicalBucket.PROFILE_AVATARS,
            storagePath = "users/u1/avatars/a1/photo.jpg",
            originalFilename = "photo.jpg",
            safeFilename = "photo.jpg",
            declaredMimeType = "image/jpeg",
            detectedMimeType = null,
            sizeBytes = 1024,
            status = FileVersionStatus.READY,
            nowEpochMs = 1L,
            purpose = FileAssetPurpose.USER_AVATAR
        )
        assertTrue(r.isSuccess)
    }

    @Test
    fun legacy_bucket_no_upload() {
        val r = FileAssetVersionRules.validate(
            id = "v1",
            assetId = "a1",
            logicalBucket = FileLogicalBucket.LEGACY_LEOVER_READ_ONLY,
            storagePath = "posts/p1/image.jpg",
            originalFilename = "x.jpg",
            safeFilename = "x.jpg",
            declaredMimeType = "image/jpeg",
            detectedMimeType = null,
            sizeBytes = 10,
            status = FileVersionStatus.PENDING,
            nowEpochMs = 1L,
            purpose = FileAssetPurpose.POST_MEDIA
        )
        assertTrue(r.isFailure)
        assertEquals("LEGACY_BUCKET_NO_UPLOAD", r.exceptionOrNull()?.message)
    }

    @Test
    fun detected_mime_prevails() {
        val r = FileAssetVersionRules.validate(
            id = "v1",
            assetId = "a1",
            logicalBucket = FileLogicalBucket.PROFILE_AVATARS,
            storagePath = "users/u1/avatars/a1/photo.png",
            originalFilename = "photo.png",
            safeFilename = "photo.png",
            declaredMimeType = "image/jpeg",
            detectedMimeType = "image/png",
            sizeBytes = 100,
            status = FileVersionStatus.READY,
            nowEpochMs = 1L,
            purpose = FileAssetPurpose.USER_AVATAR
        )
        assertTrue(r.isSuccess)
        assertEquals("image/png", r.getOrNull()?.effectiveMimeType)
    }

    @Test
    fun path_traversal_rejected() {
        val r = FileAssetVersionRules.validate(
            id = "v1",
            assetId = "a1",
            logicalBucket = FileLogicalBucket.PUBLIC_MEDIA,
            storagePath = "posts/../secret/x.jpg",
            originalFilename = "x.jpg",
            safeFilename = "x.jpg",
            declaredMimeType = "image/jpeg",
            detectedMimeType = null,
            sizeBytes = 10,
            status = FileVersionStatus.PENDING,
            nowEpochMs = 1L,
            purpose = FileAssetPurpose.POST_MEDIA
        )
        assertTrue(r.isFailure)
    }

    @Test
    fun size_zero_rejected() {
        val r = FileAssetVersionRules.validate(
            id = "v1",
            assetId = "a1",
            logicalBucket = FileLogicalBucket.PROFILE_AVATARS,
            storagePath = "users/u1/avatars/a1/x.jpg",
            originalFilename = "x.jpg",
            safeFilename = "x.jpg",
            declaredMimeType = "image/jpeg",
            detectedMimeType = null,
            sizeBytes = 0,
            status = FileVersionStatus.PENDING,
            nowEpochMs = 1L,
            purpose = FileAssetPurpose.USER_AVATAR
        )
        assertTrue(r.isFailure)
        assertEquals("SIZE_INVALID", r.exceptionOrNull()?.message)
    }
}
