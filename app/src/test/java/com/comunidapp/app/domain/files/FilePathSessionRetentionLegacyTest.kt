package com.comunidapp.app.domain.files

import com.comunidapp.app.data.remote.storage.StoragePaths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilePathBuilderTest {

    @Test
    fun builds_avatar_path_with_asset_id() {
        val r = FilePathBuilder.build(
            FilePathBuildRequest(
                purpose = FileAssetPurpose.USER_AVATAR,
                owner = FileAssetOwner.User("user-1"),
                assetId = "asset-1",
                safeFilename = "avatar.jpg"
            )
        )
        assertTrue(r.isSuccess)
        assertEquals("users/user-1/avatars/asset-1/avatar.jpg", r.getOrNull())
    }

    @Test
    fun builds_org_logo() {
        val r = FilePathBuilder.build(
            FilePathBuildRequest(
                purpose = FileAssetPurpose.ORGANIZATION_LOGO,
                owner = FileAssetOwner.Organization("org-1"),
                assetId = "a1",
                safeFilename = "logo.png"
            )
        )
        assertTrue(r.isSuccess)
        assertEquals("organizations/org-1/logo/a1/logo.png", r.getOrNull())
    }

    @Test
    fun client_bucket_override_denied() {
        val r = FilePathRules.clientMustNotChooseSensitiveBucket(
            FileAssetPurpose.MODERATION_EVIDENCE,
            FileLogicalBucket.PUBLIC_MEDIA
        )
        assertTrue(r.isFailure)
        assertEquals("CLIENT_BUCKET_OVERRIDE_DENIED", r.exceptionOrNull()?.message)
    }

    @Test
    fun arbitrary_path_with_url_rejected() {
        assertTrue(FilePathRules.validateStoragePath("https://evil/x").isFailure)
    }

    @Test
    fun recognizes_legacy_storage_paths() {
        assertNotNull(FilePathBuilder.recognizeLegacyReadPath(StoragePaths.userAvatar("u1")))
        assertNotNull(FilePathBuilder.recognizeLegacyReadPath(StoragePaths.postImage("p1")))
        assertTrue(StoragePaths.isLegacyStylePath(StoragePaths.petPhoto("u", "p")))
    }

    @Test
    fun evidence_requires_case_resource() {
        val r = FilePathBuilder.build(
            FilePathBuildRequest(
                purpose = FileAssetPurpose.MODERATION_EVIDENCE,
                owner = FileAssetOwner.Platform(),
                assetId = "a1",
                safeFilename = "e.pdf",
                resourceRef = null
            )
        )
        assertTrue(r.isFailure)
    }
}

class FileUploadSessionRulesTest {

    @Test
    fun progress_bounds() {
        assertTrue(FileUploadSessionRules.validateProgress(-1).isFailure)
        assertTrue(FileUploadSessionRules.validateProgress(101).isFailure)
        assertTrue(FileUploadSessionRules.validateProgress(50).isSuccess)
    }

    @Test
    fun complete_requires_ready_version() {
        val s = FileUploadSession(
            id = "s1",
            assetId = "a1",
            versionId = "v1",
            state = FileUploadSessionState.UPLOADING,
            createdAtEpochMs = 1L
        )
        assertTrue(FileUploadSessionRules.canComplete(s, versionReady = false).isFailure)
        assertTrue(FileUploadSessionRules.canComplete(s, versionReady = true).isSuccess)
    }

    @Test
    fun cancel_idempotent() {
        val s = FileUploadSession(
            id = "s1",
            assetId = "a1",
            versionId = "v1",
            state = FileUploadSessionState.UPLOADING,
            createdAtEpochMs = 1L
        )
        val c1 = FileUploadSessionRules.cancelIdempotent(s, 2L)
        val c2 = FileUploadSessionRules.cancelIdempotent(c1, 3L)
        assertEquals(FileUploadSessionState.CANCELLED, c1.state)
        assertEquals(FileUploadSessionState.CANCELLED, c2.state)
    }

    @Test
    fun double_submit_while_uploading() {
        val s = FileUploadSession(
            id = "s1",
            assetId = "a1",
            versionId = "v1",
            state = FileUploadSessionState.UPLOADING,
            createdAtEpochMs = 1L
        )
        assertTrue(
            FileUploadSessionRules.rejectDoubleSubmit(s, FileUploadSessionState.UPLOADING).isFailure
        )
    }

    @Test
    fun expired_session_rejected() {
        val s = FileUploadSession(
            id = "s1",
            assetId = "a1",
            versionId = "v1",
            state = FileUploadSessionState.READY_TO_UPLOAD,
            createdAtEpochMs = 1L,
            expiresAtEpochMs = 10L
        )
        assertTrue(FileUploadSessionRules.rejectIfExpired(s, 11L).isFailure)
    }
}

class FileSignedAccessRulesTest {

    @Test
    fun public_resolution_requires_public() {
        val asset = FileAsset(
            id = "a1",
            owner = FileAssetOwner.User("u1"),
            purpose = FileAssetPurpose.USER_AVATAR,
            visibility = FileAssetVisibility.OWNER_ONLY,
            status = FileAssetStatus.READY,
            createdByUserId = "u1",
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L
        )
        assertTrue(
            FileSignedAccessRules.validateRequest(asset, FileSignedTtlClass.PUBLIC_RESOLUTION)
                .isFailure
        )
    }

    @Test
    fun sensitive_ttl_clamped() {
        assertEquals(
            FileSignedAccessRules.SENSITIVE_SHORT_MAX_SECONDS,
            FileSignedAccessRules.clampTtl(FileSignedTtlClass.SENSITIVE_SHORT, 99999)
        )
    }

    @Test
    fun must_not_persist_or_log_token() {
        assertTrue(FileSignedAccessRules.mustNotPersistUrl())
        assertTrue(FileSignedAccessRules.mustNotLogToken())
    }

    @Test
    fun deleted_asset_no_signed() {
        val asset = FileAsset(
            id = "a1",
            owner = FileAssetOwner.User("u1"),
            purpose = FileAssetPurpose.USER_AVATAR,
            visibility = FileAssetVisibility.PUBLIC,
            status = FileAssetStatus.DELETED,
            createdByUserId = "u1",
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L,
            deletedAtEpochMs = 2L
        )
        assertTrue(
            FileSignedAccessRules.validateRequest(asset, FileSignedTtlClass.STANDARD_PRIVATE)
                .isFailure
        )
    }
}

class FileRetentionRulesTest {

    @Test
    fun unlink_does_not_delete_physical() {
        assertTrue(FileRetentionRules.unlinkDoesNotDeletePhysical())
        assertTrue(FileAssetLinkRules.unlinkDoesNotDeletePhysical())
    }

    @Test
    fun physical_delete_blocked_by_links() {
        val asset = FileAsset(
            id = "a1",
            owner = FileAssetOwner.User("u1"),
            purpose = FileAssetPurpose.USER_AVATAR,
            visibility = FileAssetVisibility.PUBLIC,
            status = FileAssetStatus.READY,
            createdByUserId = "u1",
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L
        )
        assertTrue(
            FileRetentionRules.canPhysicallyDelete(asset, activeLinkCount = 1, nowEpochMs = 10L)
                .isFailure
        )
    }

    @Test
    fun legal_hold_blocks_delete() {
        val asset = FileAsset(
            id = "a1",
            owner = FileAssetOwner.Platform(),
            purpose = FileAssetPurpose.MODERATION_EVIDENCE,
            visibility = FileAssetVisibility.AUTHORIZED_STAFF,
            status = FileAssetStatus.READY,
            createdByUserId = "s",
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L,
            legalHold = true
        )
        assertTrue(
            FileRetentionRules.canPhysicallyDelete(asset, 0, 10L).isFailure
        )
    }

    @Test
    fun retention_future_blocks() {
        val asset = FileAsset(
            id = "a1",
            owner = FileAssetOwner.User("u1"),
            purpose = FileAssetPurpose.SUPPORT_ATTACHMENT,
            visibility = FileAssetVisibility.OWNER_ONLY,
            status = FileAssetStatus.DELETED,
            createdByUserId = "u1",
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L,
            deletedAtEpochMs = 1L,
            retentionUntilEpochMs = 10_000L
        )
        assertTrue(
            FileRetentionRules.canPhysicallyDelete(asset, 0, 100L).isFailure
        )
    }
}

class FileLegacyCompatibilityTest {

    @Test
    fun public_url_read_only() {
        val r = FileLegacyCompatibility.parsePublicUrl("https://cdn.example/x.jpg")
        assertTrue(r.isSuccess)
        assertFalse(FileLegacyCompatibility.grantsOwnership(r.getOrNull()!!))
        assertFalse(FileLegacyCompatibility.allowsUpload(r.getOrNull()!!))
        assertFalse(FileLegacyCompatibility.isVerifiedAsset(r.getOrNull()!!))
    }

    @Test
    fun content_uri_rejected() {
        assertTrue(FileLegacyCompatibility.parsePublicUrl("content://media/1").isFailure)
    }

    @Test
    fun legacy_path_recognized() {
        val r = FileLegacyCompatibility.parseStoragePath("posts/p1/image.jpg")
        assertTrue(r.isSuccess)
        assertEquals(FileLogicalBucket.LEGACY_LEOVER_READ_ONLY, r.getOrNull()?.logicalBucket)
    }

    @Test
    fun no_download_in_validators() {
        assertFalse(FileLegacyCompatibility.downloadDuringValidation())
        assertTrue(FileLegacyCompatibility.mustNotGenerateNewLegacyValues())
    }
}
