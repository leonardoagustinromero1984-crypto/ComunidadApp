package com.comunidapp.app.domain.files

/**
 * Rutas tipadas M05. Reutiliza convenciones de [com.comunidapp.app.data.remote.storage.StoragePaths]
 * para paths legacy de lectura; los paths nuevos incluyen assetId.
 */
object FilePathRules {

    private val SEGMENT = Regex("^[A-Za-z0-9._-]+$")
    private val UUID_LIKE = Regex(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$|" +
            "^[A-Za-z0-9._-]{1,128}$"
    )

    fun validateSegment(value: String, label: String = "segment"): Result<String> {
        val v = value.trim()
        if (v.isEmpty()) return fileFailure("${label.uppercase()}_EMPTY")
        if (v.contains("..") || v.contains('\\') || v.contains('/')) {
            return fileFailure("PATH_TRAVERSAL")
        }
        if (!SEGMENT.matches(v) && !UUID_LIKE.matches(v)) {
            return fileFailure("PATH_SEGMENT_INVALID")
        }
        return Result.success(v)
    }

    fun validateStoragePath(path: String): Result<String> {
        val p = path.trim()
        if (p.isEmpty()) return fileFailure("PATH_REQUIRED")
        if (p.startsWith("http://") || p.startsWith("https://") ||
            p.startsWith("content://") || p.startsWith("data:")
        ) {
            return fileFailure("PATH_NOT_URL")
        }
        if (p.contains("..") || p.contains('\\')) return fileFailure("PATH_TRAVERSAL")
        if (p.startsWith("/") || p.endsWith("/")) return fileFailure("PATH_FORMAT")
        val parts = p.split('/')
        if (parts.any { it.isEmpty() }) return fileFailure("PATH_EMPTY_SEGMENT")
        return Result.success(p)
    }

    fun clientMustNotChooseSensitiveBucket(
        purpose: FileAssetPurpose,
        clientRequestedBucket: FileLogicalBucket?
    ): Result<FileLogicalBucket> {
        val resolved = FilePurposePolicy.resolveLogicalBucket(purpose)
        if (clientRequestedBucket != null && clientRequestedBucket != resolved) {
            return fileFailure("CLIENT_BUCKET_OVERRIDE_DENIED")
        }
        if (FilePurposePolicy.isSensitive(purpose) &&
            resolved == FileLogicalBucket.PUBLIC_MEDIA
        ) {
            return fileFailure("SENSITIVE_BUCKET_PUBLIC")
        }
        if (resolved == FileLogicalBucket.LEGACY_LEOVER_READ_ONLY) {
            return fileFailure("LEGACY_BUCKET_NO_UPLOAD")
        }
        return Result.success(resolved)
    }
}

data class FilePathBuildRequest(
    val purpose: FileAssetPurpose,
    val owner: FileAssetOwner,
    val assetId: String,
    val safeFilename: String,
    val resourceRef: FileResourceRef? = null
)

object FilePathBuilder {

    fun build(request: FilePathBuildRequest): Result<String> {
        FileOwnershipRules.validate(request.owner).getOrElse { return Result.failure(it) }
        FileOwnershipRules.compatibleWithPurpose(request.owner, request.purpose)
            .getOrElse { return Result.failure(it) }
        if (!FilePurposePolicy.acceptsNewUpload(request.purpose)) {
            return fileFailure("PURPOSE_UPLOAD_DENIED")
        }
        val assetId = FilePathRules.validateSegment(request.assetId, "assetId")
            .getOrElse { return Result.failure(it) }
        val safe = FileNameSanitizer.sanitize(request.safeFilename)
            .getOrElse { return Result.failure(it) }
        FilePathRules.clientMustNotChooseSensitiveBucket(request.purpose, null)
            .getOrElse { return Result.failure(it) }

        val path = when (request.purpose) {
            FileAssetPurpose.USER_AVATAR -> {
                val userId = requireUser(request.owner).getOrElse { return Result.failure(it) }
                "users/$userId/avatars/$assetId/$safe"
            }
            FileAssetPurpose.USER_COVER -> {
                val userId = requireUser(request.owner).getOrElse { return Result.failure(it) }
                "users/$userId/covers/$assetId/$safe"
            }
            FileAssetPurpose.PET_AVATAR, FileAssetPurpose.PET_GALLERY -> {
                val userId = requireUser(request.owner).getOrElse { return Result.failure(it) }
                val petId = requireResource(request.resourceRef, FileResourceType.PET)
                    .getOrElse { return Result.failure(it) }
                val mid = if (request.purpose == FileAssetPurpose.PET_GALLERY) "gallery/" else ""
                "users/$userId/pets/$petId/$mid$assetId/$safe"
            }
            FileAssetPurpose.ORGANIZATION_LOGO -> {
                val orgId = requireOrg(request.owner).getOrElse { return Result.failure(it) }
                "organizations/$orgId/logo/$assetId/$safe"
            }
            FileAssetPurpose.ORGANIZATION_COVER -> {
                val orgId = requireOrg(request.owner).getOrElse { return Result.failure(it) }
                "organizations/$orgId/cover/$assetId/$safe"
            }
            FileAssetPurpose.ORGANIZATION_DOCUMENT -> {
                val orgId = requireOrg(request.owner).getOrElse { return Result.failure(it) }
                "organizations/$orgId/documents/$assetId/$safe"
            }
            FileAssetPurpose.ORGANIZATION_VERIFICATION_DOCUMENT -> {
                val orgId = when (request.owner) {
                    is FileAssetOwner.Organization -> request.owner.organizationId
                    is FileAssetOwner.Platform -> requireResource(
                        request.resourceRef,
                        FileResourceType.ORGANIZATION
                    ).getOrElse {
                        requireResource(request.resourceRef, FileResourceType.ORGANIZATION_VERIFICATION)
                            .getOrElse { return Result.failure(it) }
                    }
                    else -> return fileFailure("OWNER_ORG_REQUIRED")
                }
                FilePathRules.validateSegment(orgId, "organizationId").getOrElse { return Result.failure(it) }
                "organizations/$orgId/verification/$assetId/$safe"
            }
            FileAssetPurpose.POST_MEDIA -> {
                val postId = requireResource(request.resourceRef, FileResourceType.POST)
                    .getOrElse { return Result.failure(it) }
                "posts/$postId/$assetId/$safe"
            }
            FileAssetPurpose.ADOPTION_MEDIA -> {
                val id = requireResource(request.resourceRef, FileResourceType.ADOPTION)
                    .getOrElse { return Result.failure(it) }
                "adoptions/$id/$assetId/$safe"
            }
            FileAssetPurpose.LOST_FOUND_MEDIA -> {
                val id = requireResource(request.resourceRef, FileResourceType.LOST_FOUND_CASE)
                    .getOrElse { return Result.failure(it) }
                "lost_found/$id/$assetId/$safe"
            }
            FileAssetPurpose.SERVICE_PROFILE_MEDIA -> {
                val id = requireResource(request.resourceRef, FileResourceType.SERVICE_PROFILE)
                    .getOrElse { return Result.failure(it) }
                "services/$id/$assetId/$safe"
            }
            FileAssetPurpose.SUPPORT_ATTACHMENT -> {
                val id = requireResource(request.resourceRef, FileResourceType.SUPPORT_TICKET)
                    .getOrElse { return Result.failure(it) }
                "support/tickets/$id/$assetId/$safe"
            }
            FileAssetPurpose.MODERATION_EVIDENCE -> {
                val id = requireResource(request.resourceRef, FileResourceType.MODERATION_CASE)
                    .getOrElse { return Result.failure(it) }
                "moderation/cases/$id/evidence/$assetId/$safe"
            }
            FileAssetPurpose.MESSAGE_ATTACHMENT -> {
                val id = requireResource(request.resourceRef, FileResourceType.MESSAGE)
                    .getOrElse { return Result.failure(it) }
                "messages/$id/$assetId/$safe"
            }
            FileAssetPurpose.EVENT_MEDIA -> {
                val id = requireResource(request.resourceRef, FileResourceType.EVENT)
                    .getOrElse { return Result.failure(it) }
                "events/$id/$assetId/$safe"
            }
            FileAssetPurpose.PRODUCT_MEDIA -> {
                val id = requireResource(request.resourceRef, FileResourceType.PRODUCT)
                    .getOrElse { return Result.failure(it) }
                "products/$id/$assetId/$safe"
            }
            FileAssetPurpose.OTHER -> return fileFailure("PURPOSE_UPLOAD_DENIED")
        }
        return FilePathRules.validateStoragePath(path)
    }

    /**
     * Paths legacy existentes (StoragePaths) — solo reconocimiento de lectura.
     * No autorizan escritura M05 nueva.
     */
    fun recognizeLegacyReadPath(path: String): LegacyStoragePathReference? {
        val p = path.trim()
        if (p.isEmpty() || p.contains("..")) return null
        return when {
            p.matches(Regex("^users/[^/]+/avatar/[^/]+$")) ->
                LegacyStoragePathReference(p, FileLogicalBucket.PROFILE_AVATARS, "avatar")
            p.matches(Regex("^organizations/[^/]+/logo/[^/]+$")) ->
                LegacyStoragePathReference(p, FileLogicalBucket.ORGANIZATION_MEDIA, "logo")
            p.matches(Regex("^organizations/[^/]+/cover/[^/]+$")) ->
                LegacyStoragePathReference(p, FileLogicalBucket.ORGANIZATION_MEDIA, "cover")
            p.matches(Regex("^users/[^/]+/pets/[^/]+/photo\\.jpg$")) ||
                p.matches(Regex("^posts/[^/]+/image\\.jpg$")) ||
                p.matches(Regex("^adoptions/[^/]+/image\\.jpg$")) ||
                p.matches(Regex("^lost_found/[^/]+/image\\.jpg$")) ->
                LegacyStoragePathReference(p, FileLogicalBucket.LEGACY_LEOVER_READ_ONLY, "leover_media")
            else -> null
        }
    }

    private fun requireUser(owner: FileAssetOwner): Result<String> {
        val id = FileOwnershipRules.userIdOrNull(owner) ?: return fileFailure("OWNER_USER_REQUIRED")
        return FilePathRules.validateSegment(id, "userId")
    }

    private fun requireOrg(owner: FileAssetOwner): Result<String> {
        val id = FileOwnershipRules.organizationIdOrNull(owner)
            ?: return fileFailure("OWNER_ORG_REQUIRED")
        return FilePathRules.validateSegment(id, "organizationId")
    }

    private fun requireResource(
        ref: FileResourceRef?,
        expected: FileResourceType
    ): Result<String> {
        if (ref == null) return fileFailure("RESOURCE_REQUIRED")
        if (ref.type != expected) return fileFailure("RESOURCE_TYPE_MISMATCH")
        return FilePathRules.validateSegment(ref.resourceId, "resourceId")
    }
}
