package com.comunidapp.app.domain.files.authorization

import com.comunidapp.app.domain.authorization.PermissionCode
import com.comunidapp.app.domain.files.FileAsset
import com.comunidapp.app.domain.files.FileAssetOwner
import com.comunidapp.app.domain.files.FileAssetPurpose
import com.comunidapp.app.domain.files.FileAssetStatus
import com.comunidapp.app.domain.files.FileAssetVisibility
import com.comunidapp.app.domain.organization.authorization.OrganizationPermissionCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FileAuthorizationTest {

    private fun avatar(owner: String = "u1") = FileAsset(
        id = "a1",
        owner = FileAssetOwner.User(owner),
        purpose = FileAssetPurpose.USER_AVATAR,
        visibility = FileAssetVisibility.OWNER_ONLY,
        status = FileAssetStatus.READY,
        createdByUserId = owner,
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L
    )

    @Test
    fun unknown_on_lookup_failure() {
        val d = FileAuthorization.canRead(
            FileAuthContext(actorUserId = "u1", permissionLookupFailed = true),
            avatar()
        )
        assertEquals(FileAccessDecision.DENIED_UNKNOWN, d)
    }

    @Test
    fun unauthenticated_denied_for_private() {
        val d = FileAuthorization.canRead(
            FileAuthContext(actorUserId = null),
            avatar()
        )
        assertEquals(FileAccessDecision.DENIED_NOT_AUTHENTICATED, d)
    }

    @Test
    fun public_ready_allowed_without_actor() {
        val asset = avatar().copy(visibility = FileAssetVisibility.PUBLIC)
        val d = FileAuthorization.canRead(FileAuthContext(actorUserId = null), asset)
        assertEquals(FileAccessDecision.ALLOWED, d)
    }

    @Test
    fun org_role_limited_to_own_org() {
        val asset = FileAsset(
            id = "a1",
            owner = FileAssetOwner.Organization("org-a"),
            purpose = FileAssetPurpose.ORGANIZATION_LOGO,
            visibility = FileAssetVisibility.ORGANIZATION_PRIVATE,
            status = FileAssetStatus.READY,
            createdByUserId = "u1",
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L
        )
        val d = FileAuthorization.canRead(
            FileAuthContext(
                actorUserId = "u1",
                organizationId = "org-b",
                organizationPermissions = setOf(OrganizationPermissionCode.ORGANIZATION_VIEW)
            ),
            asset
        )
        assertEquals(FileAccessDecision.DENIED_RESOURCE_MISMATCH, d)
    }

    @Test
    fun account_type_never_grants() {
        assertFalse(FileAuthorization.grantsFromAccountTypeOrModules())
    }

    @Test
    fun evidence_requires_platform_permission() {
        val d = FileAuthorization.canUpload(
            FileAuthContext(actorUserId = "u1", platformPermissions = emptySet()),
            FileAssetPurpose.MODERATION_EVIDENCE,
            FileAssetOwner.Platform()
        )
        assertEquals(FileAccessDecision.DENIED_MISSING_PLATFORM_PERMISSION, d)
        val ok = FileAuthorization.canUpload(
            FileAuthContext(
                actorUserId = "staff",
                platformPermissions = setOf(PermissionCode.MODERATION_VIEW_SENSITIVE)
            ),
            FileAssetPurpose.MODERATION_EVIDENCE,
            FileAssetOwner.Platform()
        )
        assertEquals(FileAccessDecision.ALLOWED, ok)
    }
}
