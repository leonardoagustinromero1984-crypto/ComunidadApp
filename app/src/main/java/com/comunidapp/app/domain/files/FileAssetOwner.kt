package com.comunidapp.app.domain.files

/**
 * Ownership único: USER | ORGANIZATION | PLATFORM. Sin ownership dual.
 */
sealed class FileAssetOwner {
    abstract val kind: FileOwnerKind

    data class User(val userId: String) : FileAssetOwner() {
        override val kind: FileOwnerKind = FileOwnerKind.USER
    }

    data class Organization(val organizationId: String) : FileAssetOwner() {
        override val kind: FileOwnerKind = FileOwnerKind.ORGANIZATION
    }

    data class Platform(val label: String = "platform") : FileAssetOwner() {
        override val kind: FileOwnerKind = FileOwnerKind.PLATFORM
    }
}

object FileOwnershipRules {

    fun validate(owner: FileAssetOwner): Result<FileAssetOwner> = when (owner) {
        is FileAssetOwner.User -> {
            if (owner.userId.isBlank()) fileFailure("OWNER_USER_REQUIRED")
            else Result.success(owner)
        }
        is FileAssetOwner.Organization -> {
            if (owner.organizationId.isBlank()) fileFailure("OWNER_ORG_REQUIRED")
            else Result.success(owner)
        }
        is FileAssetOwner.Platform -> Result.success(owner)
    }

    fun compatibleWithPurpose(owner: FileAssetOwner, purpose: FileAssetPurpose): Result<Unit> {
        val spec = FilePurposePolicy.spec(purpose)
        if (owner.kind !in spec.allowedOwnerKinds) {
            return fileFailure("OWNER_PURPOSE_INCOMPATIBLE")
        }
        return Result.success(Unit)
    }

    /**
     * Rechaza cualquier intento de ownership dual (p.ej. userId + orgId juntos).
     */
    fun rejectDual(
        userId: String?,
        organizationId: String?,
        platform: Boolean
    ): Result<FileAssetOwner> {
        val hasUser = !userId.isNullOrBlank()
        val hasOrg = !organizationId.isNullOrBlank()
        val count = listOf(hasUser, hasOrg, platform).count { it }
        if (count != 1) return fileFailure("OWNER_DUAL_OR_MISSING")
        return when {
            hasUser -> validate(FileAssetOwner.User(userId!!.trim()))
            hasOrg -> validate(FileAssetOwner.Organization(organizationId!!.trim()))
            else -> validate(FileAssetOwner.Platform())
        }
    }

    fun organizationIdOrNull(owner: FileAssetOwner): String? =
        (owner as? FileAssetOwner.Organization)?.organizationId

    fun userIdOrNull(owner: FileAssetOwner): String? =
        (owner as? FileAssetOwner.User)?.userId
}
