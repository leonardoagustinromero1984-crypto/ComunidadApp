package com.comunidapp.app.domain.files.authorization

import com.comunidapp.app.domain.authorization.PermissionCode
import com.comunidapp.app.domain.files.FileAsset
import com.comunidapp.app.domain.files.FileAssetOwner
import com.comunidapp.app.domain.files.FileAssetPurpose
import com.comunidapp.app.domain.files.FileAssetStatus
import com.comunidapp.app.domain.files.FileAssetVisibility
import com.comunidapp.app.domain.files.FileOwnershipRules
import com.comunidapp.app.domain.files.FilePurposePolicy
import com.comunidapp.app.domain.files.FileRetentionRules
import com.comunidapp.app.domain.organization.authorization.OrganizationPermissionCode

enum class FileAccessDecision {
    ALLOWED,
    DENIED_NOT_AUTHENTICATED,
    DENIED_NOT_OWNER,
    DENIED_MISSING_ORG_PERMISSION,
    DENIED_MISSING_PLATFORM_PERMISSION,
    DENIED_PURPOSE,
    DENIED_VISIBILITY,
    DENIED_RESOURCE_MISMATCH,
    DENIED_RETENTION,
    DENIED_INVALID_STATE,
    DENIED_UNKNOWN
}

/**
 * Contexto de autorización de archivos.
 * AccountType / active_modules **no** forman parte del contexto (sin autoridad).
 */
data class FileAuthContext(
    val actorUserId: String?,
    val platformPermissions: Set<PermissionCode> = emptySet(),
    /** Permisos M03 solo de la organización del asset, si aplica. */
    val organizationId: String? = null,
    val organizationPermissions: Set<OrganizationPermissionCode> = emptySet(),
    val isResourceParticipant: Boolean = false,
    val permissionLookupFailed: Boolean = false
)

object FileAuthorization {

    fun canRead(context: FileAuthContext, asset: FileAsset): FileAccessDecision {
        if (context.permissionLookupFailed) return FileAccessDecision.DENIED_UNKNOWN
        if (asset.status == FileAssetStatus.DELETED) return FileAccessDecision.DENIED_INVALID_STATE
        if (asset.visibility == FileAssetVisibility.PUBLIC &&
            asset.status == FileAssetStatus.READY &&
            !FilePurposePolicy.isSensitive(asset.purpose)
        ) {
            return FileAccessDecision.ALLOWED
        }
        val actor = context.actorUserId?.trim().orEmpty()
        if (actor.isEmpty()) return FileAccessDecision.DENIED_NOT_AUTHENTICATED
        return when (asset.visibility) {
            FileAssetVisibility.PUBLIC ->
                if (asset.status == FileAssetStatus.READY) FileAccessDecision.ALLOWED
                else FileAccessDecision.DENIED_INVALID_STATE
            FileAssetVisibility.OWNER_ONLY -> ownerOrDeny(context, asset)
            FileAssetVisibility.ORGANIZATION_PRIVATE -> orgOrDeny(context, asset)
            FileAssetVisibility.AUTHORIZED_STAFF -> staffOrDeny(context, asset.purpose)
            FileAssetVisibility.RESOURCE_PARTICIPANTS ->
                if (context.isResourceParticipant || isOwner(context, asset)) {
                    FileAccessDecision.ALLOWED
                } else {
                    FileAccessDecision.DENIED_RESOURCE_MISMATCH
                }
            FileAssetVisibility.SIGNED_LINK_ONLY -> {
                // Signed link requiere autorización previa según ownership/staff
                when (val base = when (asset.owner) {
                    is FileAssetOwner.User -> ownerOrDeny(context, asset)
                    is FileAssetOwner.Organization -> orgOrDeny(context, asset)
                    is FileAssetOwner.Platform -> staffOrDeny(context, asset.purpose)
                }) {
                    FileAccessDecision.ALLOWED -> FileAccessDecision.ALLOWED
                    else -> base
                }
            }
        }
    }

    fun canUpload(context: FileAuthContext, purpose: FileAssetPurpose, owner: FileAssetOwner): FileAccessDecision {
        if (context.permissionLookupFailed) return FileAccessDecision.DENIED_UNKNOWN
        val actor = context.actorUserId?.trim().orEmpty()
        if (actor.isEmpty()) return FileAccessDecision.DENIED_NOT_AUTHENTICATED
        if (!FilePurposePolicy.acceptsNewUpload(purpose)) return FileAccessDecision.DENIED_PURPOSE
        FileOwnershipRules.compatibleWithPurpose(owner, purpose).getOrElse {
            return FileAccessDecision.DENIED_PURPOSE
        }
        return when (owner) {
            is FileAssetOwner.User ->
                if (owner.userId == actor) FileAccessDecision.ALLOWED
                else FileAccessDecision.DENIED_NOT_OWNER
            is FileAssetOwner.Organization -> {
                if (context.organizationId != null &&
                    context.organizationId != owner.organizationId
                ) {
                    return FileAccessDecision.DENIED_RESOURCE_MISMATCH
                }
                if (OrganizationPermissionCode.ORGANIZATION_UPDATE in context.organizationPermissions) {
                    FileAccessDecision.ALLOWED
                } else {
                    FileAccessDecision.DENIED_MISSING_ORG_PERMISSION
                }
            }
            is FileAssetOwner.Platform -> staffOrDeny(context, purpose)
        }
    }

    fun canReplace(context: FileAuthContext, asset: FileAsset): FileAccessDecision =
        canUpload(context, asset.purpose, asset.owner)

    fun canDelete(
        context: FileAuthContext,
        asset: FileAsset,
        activeLinkCount: Int,
        nowEpochMs: Long
    ): FileAccessDecision {
        val write = canUpload(context, asset.purpose, asset.owner)
        if (write != FileAccessDecision.ALLOWED) return write
        FileRetentionRules.canPhysicallyDelete(asset, activeLinkCount, nowEpochMs).getOrElse {
            return FileAccessDecision.DENIED_RETENTION
        }
        return FileAccessDecision.ALLOWED
    }

    fun canSoftDelete(context: FileAuthContext, asset: FileAsset): FileAccessDecision =
        canUpload(context, asset.purpose, asset.owner)

    /** AccountType / active_modules nunca otorgan acceso. */
    fun grantsFromAccountTypeOrModules(): Boolean = false

    private fun isOwner(context: FileAuthContext, asset: FileAsset): Boolean {
        val actor = context.actorUserId ?: return false
        return when (val o = asset.owner) {
            is FileAssetOwner.User -> o.userId == actor
            is FileAssetOwner.Organization ->
                context.organizationId == o.organizationId &&
                    OrganizationPermissionCode.ORGANIZATION_UPDATE in context.organizationPermissions
            is FileAssetOwner.Platform -> false
        }
    }

    private fun ownerOrDeny(context: FileAuthContext, asset: FileAsset): FileAccessDecision {
        val actor = context.actorUserId ?: return FileAccessDecision.DENIED_NOT_AUTHENTICATED
        return when (val o = asset.owner) {
            is FileAssetOwner.User ->
                if (o.userId == actor) FileAccessDecision.ALLOWED
                else FileAccessDecision.DENIED_NOT_OWNER
            is FileAssetOwner.Organization -> orgOrDeny(context, asset)
            is FileAssetOwner.Platform -> staffOrDeny(context, asset.purpose)
        }
    }

    private fun orgOrDeny(context: FileAuthContext, asset: FileAsset): FileAccessDecision {
        val orgId = FileOwnershipRules.organizationIdOrNull(asset.owner)
            ?: return FileAccessDecision.DENIED_UNKNOWN
        if (context.organizationId != null && context.organizationId != orgId) {
            return FileAccessDecision.DENIED_RESOURCE_MISMATCH
        }
        // Roles M03 solo dentro de su organización
        if (OrganizationPermissionCode.ORGANIZATION_VIEW in context.organizationPermissions ||
            OrganizationPermissionCode.ORGANIZATION_UPDATE in context.organizationPermissions
        ) {
            return FileAccessDecision.ALLOWED
        }
        return FileAccessDecision.DENIED_MISSING_ORG_PERMISSION
    }

    private fun staffOrDeny(context: FileAuthContext, purpose: FileAssetPurpose): FileAccessDecision {
        val required = when (purpose) {
            FileAssetPurpose.MODERATION_EVIDENCE ->
                PermissionCode.MODERATION_VIEW_SENSITIVE
            FileAssetPurpose.ORGANIZATION_VERIFICATION_DOCUMENT ->
                PermissionCode.ORGANIZATIONS_REVIEW_VERIFICATION
            FileAssetPurpose.SUPPORT_ATTACHMENT ->
                PermissionCode.SUPPORT_VIEW_SENSITIVE
            else -> PermissionCode.MODERATION_VIEW
        }
        return if (required in context.platformPermissions) {
            FileAccessDecision.ALLOWED
        } else {
            FileAccessDecision.DENIED_MISSING_PLATFORM_PERMISSION
        }
    }
}
