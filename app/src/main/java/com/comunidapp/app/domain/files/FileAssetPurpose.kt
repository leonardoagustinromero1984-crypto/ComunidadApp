package com.comunidapp.app.domain.files

/**
 * Propósitos tipados M05. Las reglas viven en [FilePurposePolicy], no en ViewModels.
 */
enum class FileAssetPurpose {
    USER_AVATAR,
    USER_COVER,
    PET_AVATAR,
    PET_GALLERY,
    ORGANIZATION_LOGO,
    ORGANIZATION_COVER,
    ORGANIZATION_DOCUMENT,
    ORGANIZATION_VERIFICATION_DOCUMENT,
    POST_MEDIA,
    ADOPTION_MEDIA,
    LOST_FOUND_MEDIA,
    SERVICE_PROFILE_MEDIA,
    SUPPORT_ATTACHMENT,
    MODERATION_EVIDENCE,
    MESSAGE_ATTACHMENT,
    EVENT_MEDIA,
    PRODUCT_MEDIA,
    OTHER
}

enum class FileSensitivityClass {
    PUBLIC_ELIGIBLE,
    PRIVATE,
    SENSITIVE
}

/**
 * Bucket lógico de dominio — no es el nombre físico definitivo (Etapa 3).
 */
enum class FileLogicalBucket {
    PROFILE_AVATARS,
    ORGANIZATION_MEDIA,
    PUBLIC_MEDIA,
    ORGANIZATION_DOCUMENTS,
    MODERATION_EVIDENCE,
    SUPPORT_ATTACHMENTS,
    /** Solo lectura de URLs/paths legacy; no acepta upload M05. */
    LEGACY_LEOVER_READ_ONLY
}

enum class FileOwnerKind {
    USER,
    ORGANIZATION,
    PLATFORM
}

data class FilePurposeSpec(
    val purpose: FileAssetPurpose,
    val sensitivity: FileSensitivityClass,
    val allowedExtensions: Set<String>,
    val allowedMimeTypes: Set<String>,
    val maxSizeBytes: Long,
    val maxCountPerResource: Int,
    val allowedVisibilities: Set<FileAssetVisibility>,
    val allowedOwnerKinds: Set<FileOwnerKind>,
    val requiresProcessing: Boolean,
    val allowsPublicUrl: Boolean,
    val requiresRetention: Boolean,
    val logicalBucket: FileLogicalBucket,
    val pathTemplate: String
)

object FilePurposePolicy {

    private const val MIB = 1024L * 1024L

    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp")
    private val IMAGE_MIME = setOf("image/jpeg", "image/png", "image/webp")
    private val DOC_EXT = setOf("pdf", "jpg", "jpeg", "png", "webp")
    private val DOC_MIME = setOf(
        "application/pdf",
        "image/jpeg",
        "image/png",
        "image/webp"
    )

    private val PUBLICISH = setOf(
        FileAssetVisibility.PUBLIC,
        FileAssetVisibility.OWNER_ONLY,
        FileAssetVisibility.SIGNED_LINK_ONLY
    )
    private val PRIVATE_VIS = setOf(
        FileAssetVisibility.OWNER_ONLY,
        FileAssetVisibility.ORGANIZATION_PRIVATE,
        FileAssetVisibility.SIGNED_LINK_ONLY
    )
    private val SENSITIVE_VIS = setOf(
        FileAssetVisibility.AUTHORIZED_STAFF,
        FileAssetVisibility.ORGANIZATION_PRIVATE,
        FileAssetVisibility.SIGNED_LINK_ONLY,
        FileAssetVisibility.OWNER_ONLY,
        FileAssetVisibility.RESOURCE_PARTICIPANTS
    )

    val specs: Map<FileAssetPurpose, FilePurposeSpec> = mapOf(
        FileAssetPurpose.USER_AVATAR to FilePurposeSpec(
            purpose = FileAssetPurpose.USER_AVATAR,
            sensitivity = FileSensitivityClass.PUBLIC_ELIGIBLE,
            allowedExtensions = IMAGE_EXT,
            allowedMimeTypes = IMAGE_MIME,
            maxSizeBytes = 5 * MIB,
            maxCountPerResource = 1,
            allowedVisibilities = PUBLICISH,
            allowedOwnerKinds = setOf(FileOwnerKind.USER),
            requiresProcessing = true,
            allowsPublicUrl = true,
            requiresRetention = false,
            logicalBucket = FileLogicalBucket.PROFILE_AVATARS,
            pathTemplate = "users/{userId}/avatars/{assetId}/{safeFilename}"
        ),
        FileAssetPurpose.USER_COVER to FilePurposeSpec(
            purpose = FileAssetPurpose.USER_COVER,
            sensitivity = FileSensitivityClass.PUBLIC_ELIGIBLE,
            allowedExtensions = IMAGE_EXT,
            allowedMimeTypes = IMAGE_MIME,
            maxSizeBytes = 5 * MIB,
            maxCountPerResource = 1,
            allowedVisibilities = PUBLICISH,
            allowedOwnerKinds = setOf(FileOwnerKind.USER),
            requiresProcessing = true,
            allowsPublicUrl = true,
            requiresRetention = false,
            logicalBucket = FileLogicalBucket.PROFILE_AVATARS,
            pathTemplate = "users/{userId}/covers/{assetId}/{safeFilename}"
        ),
        FileAssetPurpose.PET_AVATAR to FilePurposeSpec(
            purpose = FileAssetPurpose.PET_AVATAR,
            sensitivity = FileSensitivityClass.PUBLIC_ELIGIBLE,
            allowedExtensions = IMAGE_EXT,
            allowedMimeTypes = IMAGE_MIME,
            maxSizeBytes = 8 * MIB,
            maxCountPerResource = 1,
            allowedVisibilities = PUBLICISH,
            allowedOwnerKinds = setOf(FileOwnerKind.USER),
            requiresProcessing = true,
            allowsPublicUrl = true,
            requiresRetention = false,
            logicalBucket = FileLogicalBucket.PUBLIC_MEDIA,
            pathTemplate = "users/{userId}/pets/{petId}/{assetId}/{safeFilename}"
        ),
        FileAssetPurpose.PET_GALLERY to FilePurposeSpec(
            purpose = FileAssetPurpose.PET_GALLERY,
            sensitivity = FileSensitivityClass.PUBLIC_ELIGIBLE,
            allowedExtensions = IMAGE_EXT,
            allowedMimeTypes = IMAGE_MIME,
            maxSizeBytes = 8 * MIB,
            maxCountPerResource = 12,
            allowedVisibilities = PUBLICISH,
            allowedOwnerKinds = setOf(FileOwnerKind.USER),
            requiresProcessing = true,
            allowsPublicUrl = true,
            requiresRetention = false,
            logicalBucket = FileLogicalBucket.PUBLIC_MEDIA,
            pathTemplate = "users/{userId}/pets/{petId}/gallery/{assetId}/{safeFilename}"
        ),
        FileAssetPurpose.ORGANIZATION_LOGO to FilePurposeSpec(
            purpose = FileAssetPurpose.ORGANIZATION_LOGO,
            sensitivity = FileSensitivityClass.PUBLIC_ELIGIBLE,
            allowedExtensions = IMAGE_EXT,
            allowedMimeTypes = IMAGE_MIME,
            maxSizeBytes = 5 * MIB,
            maxCountPerResource = 1,
            allowedVisibilities = PUBLICISH + FileAssetVisibility.ORGANIZATION_PRIVATE,
            allowedOwnerKinds = setOf(FileOwnerKind.ORGANIZATION),
            requiresProcessing = true,
            allowsPublicUrl = true,
            requiresRetention = false,
            logicalBucket = FileLogicalBucket.ORGANIZATION_MEDIA,
            pathTemplate = "organizations/{organizationId}/logo/{assetId}/{safeFilename}"
        ),
        FileAssetPurpose.ORGANIZATION_COVER to FilePurposeSpec(
            purpose = FileAssetPurpose.ORGANIZATION_COVER,
            sensitivity = FileSensitivityClass.PUBLIC_ELIGIBLE,
            allowedExtensions = IMAGE_EXT,
            allowedMimeTypes = IMAGE_MIME,
            maxSizeBytes = 5 * MIB,
            maxCountPerResource = 1,
            allowedVisibilities = PUBLICISH + FileAssetVisibility.ORGANIZATION_PRIVATE,
            allowedOwnerKinds = setOf(FileOwnerKind.ORGANIZATION),
            requiresProcessing = true,
            allowsPublicUrl = true,
            requiresRetention = false,
            logicalBucket = FileLogicalBucket.ORGANIZATION_MEDIA,
            pathTemplate = "organizations/{organizationId}/cover/{assetId}/{safeFilename}"
        ),
        FileAssetPurpose.ORGANIZATION_DOCUMENT to FilePurposeSpec(
            purpose = FileAssetPurpose.ORGANIZATION_DOCUMENT,
            sensitivity = FileSensitivityClass.PRIVATE,
            allowedExtensions = DOC_EXT,
            allowedMimeTypes = DOC_MIME,
            maxSizeBytes = 15 * MIB,
            maxCountPerResource = 20,
            allowedVisibilities = PRIVATE_VIS,
            allowedOwnerKinds = setOf(FileOwnerKind.ORGANIZATION),
            requiresProcessing = false,
            allowsPublicUrl = false,
            requiresRetention = false,
            logicalBucket = FileLogicalBucket.ORGANIZATION_DOCUMENTS,
            pathTemplate = "organizations/{organizationId}/documents/{assetId}/{safeFilename}"
        ),
        FileAssetPurpose.ORGANIZATION_VERIFICATION_DOCUMENT to FilePurposeSpec(
            purpose = FileAssetPurpose.ORGANIZATION_VERIFICATION_DOCUMENT,
            sensitivity = FileSensitivityClass.SENSITIVE,
            allowedExtensions = DOC_EXT,
            allowedMimeTypes = DOC_MIME,
            maxSizeBytes = 15 * MIB,
            maxCountPerResource = 10,
            allowedVisibilities = SENSITIVE_VIS,
            allowedOwnerKinds = setOf(FileOwnerKind.ORGANIZATION, FileOwnerKind.PLATFORM),
            requiresProcessing = false,
            allowsPublicUrl = false,
            requiresRetention = true,
            logicalBucket = FileLogicalBucket.ORGANIZATION_DOCUMENTS,
            pathTemplate = "organizations/{organizationId}/verification/{assetId}/{safeFilename}"
        ),
        FileAssetPurpose.POST_MEDIA to FilePurposeSpec(
            purpose = FileAssetPurpose.POST_MEDIA,
            sensitivity = FileSensitivityClass.PUBLIC_ELIGIBLE,
            allowedExtensions = IMAGE_EXT,
            allowedMimeTypes = IMAGE_MIME,
            maxSizeBytes = 8 * MIB,
            maxCountPerResource = 4,
            allowedVisibilities = PUBLICISH,
            allowedOwnerKinds = setOf(FileOwnerKind.USER, FileOwnerKind.ORGANIZATION),
            requiresProcessing = true,
            allowsPublicUrl = true,
            requiresRetention = false,
            logicalBucket = FileLogicalBucket.PUBLIC_MEDIA,
            pathTemplate = "posts/{postId}/{assetId}/{safeFilename}"
        ),
        FileAssetPurpose.ADOPTION_MEDIA to FilePurposeSpec(
            purpose = FileAssetPurpose.ADOPTION_MEDIA,
            sensitivity = FileSensitivityClass.PUBLIC_ELIGIBLE,
            allowedExtensions = IMAGE_EXT,
            allowedMimeTypes = IMAGE_MIME,
            maxSizeBytes = 8 * MIB,
            maxCountPerResource = 6,
            allowedVisibilities = PUBLICISH,
            allowedOwnerKinds = setOf(FileOwnerKind.USER, FileOwnerKind.ORGANIZATION),
            requiresProcessing = true,
            allowsPublicUrl = true,
            requiresRetention = false,
            logicalBucket = FileLogicalBucket.PUBLIC_MEDIA,
            pathTemplate = "adoptions/{adoptionId}/{assetId}/{safeFilename}"
        ),
        FileAssetPurpose.LOST_FOUND_MEDIA to FilePurposeSpec(
            purpose = FileAssetPurpose.LOST_FOUND_MEDIA,
            sensitivity = FileSensitivityClass.PUBLIC_ELIGIBLE,
            allowedExtensions = IMAGE_EXT,
            allowedMimeTypes = IMAGE_MIME,
            maxSizeBytes = 8 * MIB,
            maxCountPerResource = 6,
            allowedVisibilities = PUBLICISH,
            allowedOwnerKinds = setOf(FileOwnerKind.USER, FileOwnerKind.ORGANIZATION),
            requiresProcessing = true,
            allowsPublicUrl = true,
            requiresRetention = false,
            logicalBucket = FileLogicalBucket.PUBLIC_MEDIA,
            pathTemplate = "lost_found/{caseId}/{assetId}/{safeFilename}"
        ),
        FileAssetPurpose.SERVICE_PROFILE_MEDIA to FilePurposeSpec(
            purpose = FileAssetPurpose.SERVICE_PROFILE_MEDIA,
            sensitivity = FileSensitivityClass.PUBLIC_ELIGIBLE,
            allowedExtensions = IMAGE_EXT,
            allowedMimeTypes = IMAGE_MIME,
            maxSizeBytes = 8 * MIB,
            maxCountPerResource = 8,
            allowedVisibilities = PUBLICISH,
            allowedOwnerKinds = setOf(FileOwnerKind.USER, FileOwnerKind.ORGANIZATION),
            requiresProcessing = true,
            allowsPublicUrl = true,
            requiresRetention = false,
            logicalBucket = FileLogicalBucket.PUBLIC_MEDIA,
            pathTemplate = "services/{serviceId}/{assetId}/{safeFilename}"
        ),
        FileAssetPurpose.SUPPORT_ATTACHMENT to FilePurposeSpec(
            purpose = FileAssetPurpose.SUPPORT_ATTACHMENT,
            sensitivity = FileSensitivityClass.SENSITIVE,
            allowedExtensions = DOC_EXT,
            allowedMimeTypes = DOC_MIME,
            maxSizeBytes = 15 * MIB,
            maxCountPerResource = 10,
            allowedVisibilities = SENSITIVE_VIS,
            allowedOwnerKinds = setOf(FileOwnerKind.USER, FileOwnerKind.PLATFORM),
            requiresProcessing = false,
            allowsPublicUrl = false,
            requiresRetention = true,
            logicalBucket = FileLogicalBucket.SUPPORT_ATTACHMENTS,
            pathTemplate = "support/tickets/{ticketId}/{assetId}/{safeFilename}"
        ),
        FileAssetPurpose.MODERATION_EVIDENCE to FilePurposeSpec(
            purpose = FileAssetPurpose.MODERATION_EVIDENCE,
            sensitivity = FileSensitivityClass.SENSITIVE,
            allowedExtensions = DOC_EXT,
            allowedMimeTypes = DOC_MIME,
            maxSizeBytes = 10 * MIB,
            maxCountPerResource = 20,
            allowedVisibilities = SENSITIVE_VIS,
            allowedOwnerKinds = setOf(FileOwnerKind.PLATFORM),
            requiresProcessing = false,
            allowsPublicUrl = false,
            requiresRetention = true,
            logicalBucket = FileLogicalBucket.MODERATION_EVIDENCE,
            pathTemplate = "moderation/cases/{caseId}/evidence/{assetId}/{safeFilename}"
        ),
        FileAssetPurpose.MESSAGE_ATTACHMENT to FilePurposeSpec(
            purpose = FileAssetPurpose.MESSAGE_ATTACHMENT,
            sensitivity = FileSensitivityClass.PRIVATE,
            allowedExtensions = IMAGE_EXT + setOf("pdf"),
            allowedMimeTypes = IMAGE_MIME + setOf("application/pdf"),
            maxSizeBytes = 8 * MIB,
            maxCountPerResource = 5,
            allowedVisibilities = setOf(
                FileAssetVisibility.RESOURCE_PARTICIPANTS,
                FileAssetVisibility.OWNER_ONLY,
                FileAssetVisibility.SIGNED_LINK_ONLY
            ),
            allowedOwnerKinds = setOf(FileOwnerKind.USER),
            requiresProcessing = false,
            allowsPublicUrl = false,
            requiresRetention = false,
            logicalBucket = FileLogicalBucket.SUPPORT_ATTACHMENTS,
            pathTemplate = "messages/{messageId}/{assetId}/{safeFilename}"
        ),
        FileAssetPurpose.EVENT_MEDIA to FilePurposeSpec(
            purpose = FileAssetPurpose.EVENT_MEDIA,
            sensitivity = FileSensitivityClass.PUBLIC_ELIGIBLE,
            allowedExtensions = IMAGE_EXT,
            allowedMimeTypes = IMAGE_MIME,
            maxSizeBytes = 8 * MIB,
            maxCountPerResource = 4,
            allowedVisibilities = PUBLICISH,
            allowedOwnerKinds = setOf(FileOwnerKind.USER, FileOwnerKind.ORGANIZATION),
            requiresProcessing = true,
            allowsPublicUrl = true,
            requiresRetention = false,
            logicalBucket = FileLogicalBucket.PUBLIC_MEDIA,
            pathTemplate = "events/{eventId}/{assetId}/{safeFilename}"
        ),
        FileAssetPurpose.PRODUCT_MEDIA to FilePurposeSpec(
            purpose = FileAssetPurpose.PRODUCT_MEDIA,
            sensitivity = FileSensitivityClass.PUBLIC_ELIGIBLE,
            allowedExtensions = IMAGE_EXT,
            allowedMimeTypes = IMAGE_MIME,
            maxSizeBytes = 8 * MIB,
            maxCountPerResource = 8,
            allowedVisibilities = PUBLICISH,
            allowedOwnerKinds = setOf(FileOwnerKind.USER, FileOwnerKind.ORGANIZATION),
            requiresProcessing = true,
            allowsPublicUrl = true,
            requiresRetention = false,
            logicalBucket = FileLogicalBucket.PUBLIC_MEDIA,
            pathTemplate = "products/{productId}/{assetId}/{safeFilename}"
        ),
        FileAssetPurpose.OTHER to FilePurposeSpec(
            purpose = FileAssetPurpose.OTHER,
            sensitivity = FileSensitivityClass.PRIVATE,
            allowedExtensions = emptySet(),
            allowedMimeTypes = emptySet(),
            maxSizeBytes = 0,
            maxCountPerResource = 0,
            allowedVisibilities = setOf(FileAssetVisibility.OWNER_ONLY),
            allowedOwnerKinds = setOf(FileOwnerKind.USER, FileOwnerKind.ORGANIZATION, FileOwnerKind.PLATFORM),
            requiresProcessing = false,
            allowsPublicUrl = false,
            requiresRetention = false,
            logicalBucket = FileLogicalBucket.LEGACY_LEOVER_READ_ONLY,
            pathTemplate = "other/{assetId}/{safeFilename}"
        )
    )

    fun spec(purpose: FileAssetPurpose): FilePurposeSpec =
        specs[purpose] ?: error("PURPOSE_UNKNOWN")

    fun resolveLogicalBucket(purpose: FileAssetPurpose): FileLogicalBucket =
        spec(purpose).logicalBucket

    fun acceptsNewUpload(purpose: FileAssetPurpose): Boolean {
        val bucket = resolveLogicalBucket(purpose)
        return bucket != FileLogicalBucket.LEGACY_LEOVER_READ_ONLY &&
            purpose != FileAssetPurpose.OTHER &&
            spec(purpose).maxSizeBytes > 0
    }

    fun isSensitive(purpose: FileAssetPurpose): Boolean =
        spec(purpose).sensitivity == FileSensitivityClass.SENSITIVE

    fun allowsVisibility(purpose: FileAssetPurpose, visibility: FileAssetVisibility): Boolean {
        val s = spec(purpose)
        if (visibility == FileAssetVisibility.PUBLIC &&
            s.sensitivity == FileSensitivityClass.SENSITIVE
        ) {
            return false
        }
        if (visibility == FileAssetVisibility.PUBLIC && !s.allowsPublicUrl) {
            return false
        }
        return visibility in s.allowedVisibilities
    }
}
