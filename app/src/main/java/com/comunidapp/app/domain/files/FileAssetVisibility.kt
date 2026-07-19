package com.comunidapp.app.domain.files

enum class FileAssetVisibility {
    PUBLIC,
    OWNER_ONLY,
    ORGANIZATION_PRIVATE,
    AUTHORIZED_STAFF,
    RESOURCE_PARTICIPANTS,
    SIGNED_LINK_ONLY
}

enum class FileAssetStatus {
    DRAFT,
    UPLOADING,
    UPLOADED,
    PROCESSING,
    READY,
    REJECTED,
    QUARANTINED,
    DELETED,
    FAILED
}

enum class FileVersionStatus {
    PENDING,
    UPLOADED,
    PROCESSING,
    READY,
    REJECTED,
    FAILED,
    QUARANTINED
}

enum class FileProcessingStatus {
    NOT_REQUIRED,
    PENDING,
    PROCESSING,
    READY,
    REJECTED,
    FAILED,
    QUARANTINED
}

enum class FileUploadSessionState {
    CREATED,
    VALIDATING,
    READY_TO_UPLOAD,
    UPLOADING,
    UPLOADED,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED,
    EXPIRED
}

enum class FileSignedTtlClass {
    PUBLIC_RESOLUTION,
    STANDARD_PRIVATE,
    SENSITIVE_SHORT
}

enum class FileRetentionAction {
    UNLINK,
    SOFT_DELETE,
    PHYSICAL_DELETE,
    QUARANTINE,
    LEGAL_HOLD,
    RESTORE
}

enum class FileResourceType {
    USER,
    PET,
    ORGANIZATION,
    POST,
    ADOPTION,
    LOST_FOUND_CASE,
    SERVICE_PROFILE,
    MODERATION_CASE,
    ORGANIZATION_VERIFICATION,
    SUPPORT_TICKET,
    MESSAGE,
    EVENT,
    PRODUCT,
    OTHER
}

enum class FileRelationType {
    PRIMARY,
    GALLERY,
    ATTACHMENT,
    EVIDENCE,
    DOCUMENT,
    COVER,
    OTHER
}

object FileVisibilityRules {

    fun validate(
        purpose: FileAssetPurpose,
        visibility: FileAssetVisibility
    ): Result<Unit> {
        if (!FilePurposePolicy.allowsVisibility(purpose, visibility)) {
            return fileFailure("VISIBILITY_INCOMPATIBLE")
        }
        if (FilePurposePolicy.isSensitive(purpose) && visibility == FileAssetVisibility.PUBLIC) {
            return fileFailure("SENSITIVE_CANNOT_BE_PUBLIC")
        }
        return Result.success(Unit)
    }

    /**
     * SIGNED_LINK_ONLY no implica acceso sin autorización previa.
     */
    fun signedLinkImpliesAuthorization(): Boolean = false
}

internal fun fileFailure(code: String): Result<Nothing> =
    Result.failure(IllegalArgumentException(code))
