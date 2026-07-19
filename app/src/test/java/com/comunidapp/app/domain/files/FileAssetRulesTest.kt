package com.comunidapp.app.domain.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileAssetRulesTest {

    @Test
    fun valid_draft_asset() {
        val r = FileAssetRules.validateNew(
            id = "a1",
            owner = FileAssetOwner.User("u1"),
            purpose = FileAssetPurpose.USER_AVATAR,
            visibility = FileAssetVisibility.PUBLIC,
            createdByUserId = "u1",
            nowEpochMs = 1000L
        )
        assertTrue(r.isSuccess)
    }

    @Test
    fun blank_id_rejected() {
        val r = FileAssetRules.validateNew(
            id = " ",
            owner = FileAssetOwner.User("u1"),
            purpose = FileAssetPurpose.USER_AVATAR,
            visibility = FileAssetVisibility.OWNER_ONLY,
            createdByUserId = "u1",
            nowEpochMs = 1L
        )
        assertTrue(r.isFailure)
        assertEquals("ASSET_ID_REQUIRED", r.exceptionOrNull()?.message)
    }

    @Test
    fun sensitive_public_rejected() {
        val r = FileAssetRules.validateNew(
            id = "a1",
            owner = FileAssetOwner.Platform(),
            purpose = FileAssetPurpose.MODERATION_EVIDENCE,
            visibility = FileAssetVisibility.PUBLIC,
            createdByUserId = "staff",
            nowEpochMs = 1L
        )
        assertTrue(r.isFailure)
    }

    @Test
    fun retention_in_past_rejected() {
        val r = FileAssetRules.validateNew(
            id = "a1",
            owner = FileAssetOwner.User("u1"),
            purpose = FileAssetPurpose.USER_AVATAR,
            visibility = FileAssetVisibility.OWNER_ONLY,
            createdByUserId = "u1",
            nowEpochMs = 1000L,
            retentionUntilEpochMs = 500L
        )
        assertTrue(r.isFailure)
        assertEquals("RETENTION_IN_PAST", r.exceptionOrNull()?.message)
    }

    @Test
    fun must_not_persist_signed_url() {
        assertTrue(FileAssetRules.mustNotPersistSignedUrl())
    }

    @Test
    fun other_purpose_upload_denied() {
        val r = FileAssetRules.validateNew(
            id = "a1",
            owner = FileAssetOwner.User("u1"),
            purpose = FileAssetPurpose.OTHER,
            visibility = FileAssetVisibility.OWNER_ONLY,
            createdByUserId = "u1",
            nowEpochMs = 1L
        )
        assertTrue(r.isFailure)
        assertEquals("PURPOSE_UPLOAD_DENIED", r.exceptionOrNull()?.message)
    }
}
