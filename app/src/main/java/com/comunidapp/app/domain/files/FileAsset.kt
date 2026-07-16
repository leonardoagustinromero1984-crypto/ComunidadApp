package com.comunidapp.app.domain.files

data class FileAsset(
    val id: String,
    val owner: FileAssetOwner,
    val purpose: FileAssetPurpose,
    val visibility: FileAssetVisibility,
    val status: FileAssetStatus,
    val currentVersionId: String? = null,
    val createdByUserId: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val deletedAtEpochMs: Long? = null,
    val retentionUntilEpochMs: Long? = null,
    val processingStatus: FileProcessingStatus = FileProcessingStatus.NOT_REQUIRED,
    val legalHold: Boolean = false
)

data class FileAssetVersion(
    val id: String,
    val assetId: String,
    val logicalBucket: FileLogicalBucket,
    val storagePath: String,
    val originalFilename: String,
    val safeFilename: String,
    val declaredMimeType: String?,
    val detectedMimeType: String? = null,
    val sizeBytes: Long,
    val checksum: String? = null,
    val status: FileVersionStatus,
    val createdAtEpochMs: Long
) {
    /** MIME efectivo: detected prevalece sobre declared. */
    val effectiveMimeType: String?
        get() = detectedMimeType?.takeIf { it.isNotBlank() } ?: declaredMimeType?.takeIf { it.isNotBlank() }
}

data class FileResourceRef(
    val type: FileResourceType,
    val resourceId: String
)

data class FileAssetLink(
    val assetId: String,
    val resource: FileResourceRef,
    val relationType: FileRelationType,
    val sortOrder: Int? = null,
    val isPrimary: Boolean = false,
    val createdAtEpochMs: Long
)

data class FileUploadRequest(
    val purpose: FileAssetPurpose,
    val owner: FileAssetOwner,
    val resourceRef: FileResourceRef? = null,
    val originalFilename: String,
    val declaredMimeType: String? = null,
    val sizeBytes: Long,
    val requestedVisibility: FileAssetVisibility
)

data class FileUploadSession(
    val id: String,
    val assetId: String,
    val versionId: String,
    val state: FileUploadSessionState,
    val progressPercent: Int = 0,
    val createdAtEpochMs: Long,
    val expiresAtEpochMs: Long? = null,
    val failureCode: String? = null
)

data class FileAccessRequest(
    val assetId: String,
    val actorUserId: String,
    val purpose: FileAssetPurpose,
    val ttlClass: FileSignedTtlClass
)

/**
 * Acceso firmado temporal. La URL nunca se persiste en el asset.
 */
data class FileSignedAccess(
    val assetId: String,
    val temporaryUrl: String,
    val expiresAtEpochMs: Long,
    val ttlClass: FileSignedTtlClass
)

data class FileRetentionPolicy(
    val purpose: FileAssetPurpose,
    val retainDays: Int?,
    val legalHoldAllowed: Boolean,
    val requiresAuditOnDelete: Boolean
)

object FileAssetRules {

    fun validateNew(
        id: String,
        owner: FileAssetOwner,
        purpose: FileAssetPurpose,
        visibility: FileAssetVisibility,
        createdByUserId: String,
        nowEpochMs: Long,
        retentionUntilEpochMs: Long? = null,
        status: FileAssetStatus = FileAssetStatus.DRAFT
    ): Result<FileAsset> {
        if (id.isBlank()) return fileFailure("ASSET_ID_REQUIRED")
        if (createdByUserId.isBlank()) return fileFailure("CREATED_BY_REQUIRED")
        FileOwnershipRules.validate(owner).getOrElse { return Result.failure(it) }
        FileOwnershipRules.compatibleWithPurpose(owner, purpose).getOrElse { return Result.failure(it) }
        FileVisibilityRules.validate(purpose, visibility).getOrElse { return Result.failure(it) }
        if (!FilePurposePolicy.acceptsNewUpload(purpose)) {
            return fileFailure("PURPOSE_UPLOAD_DENIED")
        }
        if (retentionUntilEpochMs != null && retentionUntilEpochMs < nowEpochMs) {
            return fileFailure("RETENTION_IN_PAST")
        }
        val processing = if (FilePurposePolicy.spec(purpose).requiresProcessing) {
            FileProcessingStatus.PENDING
        } else {
            FileProcessingStatus.NOT_REQUIRED
        }
        return Result.success(
            FileAsset(
                id = id.trim(),
                owner = owner,
                purpose = purpose,
                visibility = visibility,
                status = status,
                createdByUserId = createdByUserId.trim(),
                createdAtEpochMs = nowEpochMs,
                updatedAtEpochMs = nowEpochMs,
                retentionUntilEpochMs = retentionUntilEpochMs,
                processingStatus = processing
            )
        )
    }

    fun validateReadyWithVersion(
        asset: FileAsset,
        versionId: String?
    ): Result<Unit> {
        if (asset.status == FileAssetStatus.READY && versionId.isNullOrBlank()) {
            return fileFailure("READY_REQUIRES_VERSION")
        }
        if (asset.deletedAtEpochMs != null && asset.status != FileAssetStatus.DELETED) {
            return fileFailure("DELETED_AT_WITHOUT_STATUS")
        }
        if (asset.status == FileAssetStatus.DELETED && asset.deletedAtEpochMs == null) {
            return fileFailure("DELETED_STATUS_REQUIRES_TIMESTAMP")
        }
        return Result.success(Unit)
    }

    fun mustNotPersistSignedUrl(): Boolean = true
}

object FileAssetVersionRules {

    fun validate(
        id: String,
        assetId: String,
        logicalBucket: FileLogicalBucket,
        storagePath: String,
        originalFilename: String,
        safeFilename: String,
        declaredMimeType: String?,
        detectedMimeType: String?,
        sizeBytes: Long,
        status: FileVersionStatus,
        nowEpochMs: Long,
        purpose: FileAssetPurpose
    ): Result<FileAssetVersion> {
        if (id.isBlank()) return fileFailure("VERSION_ID_REQUIRED")
        if (assetId.isBlank()) return fileFailure("ASSET_ID_REQUIRED")
        if (logicalBucket == FileLogicalBucket.LEGACY_LEOVER_READ_ONLY) {
            return fileFailure("LEGACY_BUCKET_NO_UPLOAD")
        }
        if (FilePurposePolicy.resolveLogicalBucket(purpose) != logicalBucket) {
            return fileFailure("BUCKET_PURPOSE_MISMATCH")
        }
        if (FilePurposePolicy.isSensitive(purpose) &&
            logicalBucket == FileLogicalBucket.PUBLIC_MEDIA
        ) {
            return fileFailure("SENSITIVE_BUCKET_PUBLIC")
        }
        FilePathRules.validateStoragePath(storagePath).getOrElse { return Result.failure(it) }
        if (safeFilename.isBlank()) return fileFailure("SAFE_FILENAME_REQUIRED")
        if (sizeBytes <= 0L) return fileFailure("SIZE_INVALID")
        val effective = detectedMimeType?.takeIf { it.isNotBlank() }
            ?: declaredMimeType?.takeIf { it.isNotBlank() }
        FileValidationRules.validateMimeAndExtension(
            purpose = purpose,
            safeFilename = safeFilename,
            declaredMimeType = declaredMimeType,
            detectedMimeType = detectedMimeType,
            sizeBytes = sizeBytes
        ).getOrElse { return Result.failure(it) }
        if (status == FileVersionStatus.READY && storagePath.isBlank()) {
            return fileFailure("READY_PATH_REQUIRED")
        }
        // URL no forma parte del modelo de versión
        return Result.success(
            FileAssetVersion(
                id = id.trim(),
                assetId = assetId.trim(),
                logicalBucket = logicalBucket,
                storagePath = storagePath.trim(),
                originalFilename = originalFilename,
                safeFilename = safeFilename,
                declaredMimeType = declaredMimeType?.trim()?.ifBlank { null },
                detectedMimeType = detectedMimeType?.trim()?.ifBlank { null },
                sizeBytes = sizeBytes,
                status = status,
                createdAtEpochMs = nowEpochMs
            )
        ).also {
            // silence unused effective for clarity of rule "detected prevails"
            effective
        }
    }
}

object FileAssetLinkRules {

    fun validate(
        asset: FileAsset,
        link: FileAssetLink,
        existingPrimaryForRelation: Boolean
    ): Result<FileAssetLink> {
        if (link.assetId != asset.id) return fileFailure("LINK_ASSET_MISMATCH")
        if (link.resource.resourceId.isBlank()) return fileFailure("RESOURCE_ID_REQUIRED")
        if (looksLikeUrl(link.resource.resourceId)) return fileFailure("RESOURCE_ID_NOT_URL")
        if (FilePurposePolicy.isSensitive(asset.purpose)) {
            if (!sensitiveResourceCompatible(asset.purpose, link.resource.type)) {
                return fileFailure("SENSITIVE_RESOURCE_MISMATCH")
            }
        }
        val ownerOrg = FileOwnershipRules.organizationIdOrNull(asset.owner)
        if (ownerOrg != null &&
            link.resource.type == FileResourceType.ORGANIZATION &&
            link.resource.resourceId != ownerOrg
        ) {
            return fileFailure("ORG_RESOURCE_MISMATCH")
        }
        if (link.isPrimary && existingPrimaryForRelation) {
            return fileFailure("PRIMARY_ALREADY_EXISTS")
        }
        return Result.success(link)
    }

    fun unlinkDoesNotDeletePhysical(): Boolean = true

    private fun looksLikeUrl(value: String): Boolean {
        val v = value.trim().lowercase()
        return v.startsWith("http://") || v.startsWith("https://") ||
            v.startsWith("content://") || v.startsWith("file://") ||
            v.startsWith("data:")
    }

    private fun sensitiveResourceCompatible(
        purpose: FileAssetPurpose,
        resourceType: FileResourceType
    ): Boolean = when (purpose) {
        FileAssetPurpose.MODERATION_EVIDENCE ->
            resourceType == FileResourceType.MODERATION_CASE
        FileAssetPurpose.ORGANIZATION_VERIFICATION_DOCUMENT ->
            resourceType == FileResourceType.ORGANIZATION_VERIFICATION ||
                resourceType == FileResourceType.ORGANIZATION
        FileAssetPurpose.SUPPORT_ATTACHMENT ->
            resourceType == FileResourceType.SUPPORT_TICKET
        else -> true
    }
}
