package com.comunidapp.app.data.repository

import com.comunidapp.app.domain.files.FileAssetOwner
import com.comunidapp.app.domain.files.FileAssetPurpose
import com.comunidapp.app.domain.files.FileLogicalBucket
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class M05SupabaseMappingTest {

    @Test
    fun `asset columns map to user-owned domain asset`() {
        val asset = M05SupabaseRpcSupport.parseAsset(
            assetJson(
                ownerKind = "USER",
                ownerUserId = "123e4567-e89b-12d3-a456-426614174000"
            )
        )

        assertEquals(FileAssetPurpose.USER_AVATAR, asset.purpose)
        assertTrue(asset.owner is FileAssetOwner.User)
        assertEquals(
            "123e4567-e89b-12d3-a456-426614174000",
            (asset.owner as FileAssetOwner.User).userId
        )
    }

    @Test
    fun `physical buckets map to logical buckets`() {
        assertEquals(
            FileLogicalBucket.MODERATION_EVIDENCE,
            M05SupabaseRpcSupport.physicalBucketToLogical("moderation-evidence")
        )
        assertEquals(
            FileLogicalBucket.LEGACY_LEOVER_READ_ONLY,
            M05SupabaseRpcSupport.physicalBucketToLogical("leover")
        )
    }

    @Test(expected = IllegalStateException::class)
    fun `owner kind requires its matching owner ID`() {
        M05SupabaseRpcSupport.parseAsset(
            assetJson(ownerKind = "ORGANIZATION", ownerUserId = null, ownerOrgId = null)
        )
    }

    private fun assetJson(
        ownerKind: String,
        ownerUserId: String? = null,
        ownerOrgId: String? = null
    ) = buildJsonObject {
        put("id", "223e4567-e89b-12d3-a456-426614174000")
        put("owner_kind", ownerKind)
        if (ownerUserId == null) put("owner_user_id", JsonNull) else put("owner_user_id", ownerUserId)
        if (ownerOrgId == null) {
            put("owner_organization_id", JsonNull)
        } else {
            put("owner_organization_id", ownerOrgId)
        }
        put("purpose", "USER_AVATAR")
        put("visibility", "PUBLIC")
        put("status", "READY")
        put("current_version_id", "323e4567-e89b-12d3-a456-426614174000")
        put("created_by", "123e4567-e89b-12d3-a456-426614174000")
        put("created_at", "2026-07-16T18:00:00Z")
        put("updated_at", "2026-07-16T18:05:00Z")
        put("deleted_at", JsonNull)
        put("retention_until", JsonNull)
        put("legal_hold", false)
        put("processing_status", "READY")
    }
}
