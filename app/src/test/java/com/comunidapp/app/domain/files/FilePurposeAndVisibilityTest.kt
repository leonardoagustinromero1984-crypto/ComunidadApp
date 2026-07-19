package com.comunidapp.app.domain.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilePurposePolicyTest {

    @Test
    fun evidence_is_sensitive_and_not_public_media() {
        assertTrue(FilePurposePolicy.isSensitive(FileAssetPurpose.MODERATION_EVIDENCE))
        assertEquals(
            FileLogicalBucket.MODERATION_EVIDENCE,
            FilePurposePolicy.resolveLogicalBucket(FileAssetPurpose.MODERATION_EVIDENCE)
        )
        assertFalse(
            FilePurposePolicy.allowsVisibility(
                FileAssetPurpose.MODERATION_EVIDENCE,
                FileAssetVisibility.PUBLIC
            )
        )
    }

    @Test
    fun verification_and_support_sensitive() {
        assertTrue(FilePurposePolicy.isSensitive(FileAssetPurpose.ORGANIZATION_VERIFICATION_DOCUMENT))
        assertTrue(FilePurposePolicy.isSensitive(FileAssetPurpose.SUPPORT_ATTACHMENT))
    }

    @Test
    fun legacy_bucket_rejects_new_upload_for_other() {
        assertFalse(FilePurposePolicy.acceptsNewUpload(FileAssetPurpose.OTHER))
        assertEquals(
            FileLogicalBucket.LEGACY_LEOVER_READ_ONLY,
            FilePurposePolicy.resolveLogicalBucket(FileAssetPurpose.OTHER)
        )
    }

    @Test
    fun avatar_maps_to_profile_avatars() {
        assertEquals(
            FileLogicalBucket.PROFILE_AVATARS,
            FilePurposePolicy.resolveLogicalBucket(FileAssetPurpose.USER_AVATAR)
        )
    }
}

class FileVisibilityRulesTest {

    @Test
    fun sensitive_cannot_be_public() {
        val r = FileVisibilityRules.validate(
            FileAssetPurpose.SUPPORT_ATTACHMENT,
            FileAssetVisibility.PUBLIC
        )
        assertTrue(r.isFailure)
    }

    @Test
    fun public_avatar_ok() {
        val r = FileVisibilityRules.validate(
            FileAssetPurpose.USER_AVATAR,
            FileAssetVisibility.PUBLIC
        )
        assertTrue(r.isSuccess)
    }

    @Test
    fun signed_link_does_not_imply_auth() {
        assertFalse(FileVisibilityRules.signedLinkImpliesAuthorization())
    }
}
