package com.comunidapp.app.data.model

import com.comunidapp.app.domain.m21.M21PrivacySanitizer

/** LeoVer M21 — Reputación, verificaciones y reseñas (Bloques 1–2). */

enum class M21ReviewTargetType {
    ADOPTION,
    SERVICE,
    DONATION,
    ORGANIZATION,
    USER
}

enum class M21ReviewStatus {
    PENDING,
    PUBLISHED,
    HIDDEN,
    REMOVED,
    APPEALED
}

enum class M21VerificationType {
    IDENTITY,
    PROFESSIONAL_LICENSE
}

enum class M21VerificationStatus {
    NOT_SUBMITTED,
    PENDING,
    APPROVED,
    REJECTED,
    EXPIRED
}

enum class M21AppealStatus {
    OPEN,
    UNDER_REVIEW,
    RESOLVED,
    DISMISSED
}

data class M21LicenseCredential(
    val licenseNumber: String,
    val issuingAuthority: String,
    val jurisdiction: String,
    val expiresAt: Long? = null
)

data class M21Review(
    val id: String,
    val targetType: M21ReviewTargetType,
    val targetId: String,
    val targetDisplayLabel: String,
    val reviewerUserId: String,
    val reviewerDisplayName: String,
    val rating: Int,
    val content: String,
    val status: M21ReviewStatus,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toPublicReview(isOwnReview: Boolean = false): M21PublicReview =
        M21PrivacySanitizer.toPublicReview(this, isOwnReview)
}

data class M21PublicReview(
    val id: String,
    val targetType: M21ReviewTargetType,
    val targetDisplayLabel: String,
    val reviewerDisplayName: String,
    val rating: Int,
    val content: String,
    val status: M21ReviewStatus,
    val createdAt: Long,
    val isOwnReview: Boolean = false
)

data class M21VerificationRequest(
    val id: String,
    val userId: String,
    val verificationType: M21VerificationType,
    val status: M21VerificationStatus,
    val displayLabel: String,
    val licenseCredential: M21LicenseCredential? = null,
    val submittedAt: Long? = null,
    val reviewedAt: Long? = null,
    val rejectionReason: String? = null
) {
    fun toPublicVerification(isOwn: Boolean): M21PublicVerification =
        M21PrivacySanitizer.toPublicVerification(this, isOwn)
}

data class M21PublicVerification(
    val id: String,
    val verificationType: M21VerificationType,
    val status: M21VerificationStatus,
    val displayLabel: String,
    val licenseSummary: String? = null,
    val submittedAt: Long? = null,
    val isOwnRequest: Boolean = false
)

data class M21Appeal(
    val id: String,
    val reviewId: String,
    val appellantUserId: String,
    val reason: String,
    val status: M21AppealStatus,
    val createdAt: Long
)

data class M21ReputationSummary(
    val userId: String,
    val reputationScore: Int,
    val publishedReviewCount: Int,
    val averageRating: Double?,
    val badges: List<UserBadge>,
    val identityVerified: Boolean,
    val licenseVerified: Boolean
) {
    fun toPublicSummary(): M21PublicReputationSummary = M21PrivacySanitizer.toPublicSummary(this)
}

data class M21PublicReputationSummary(
    val reputationScore: Int,
    val publishedReviewCount: Int,
    val averageRating: Double?,
    val badges: List<UserBadge>,
    val identityVerified: Boolean,
    val licenseVerified: Boolean
)

data class SubmitM21ReviewInput(
    val targetType: M21ReviewTargetType,
    val targetId: String,
    val targetDisplayLabel: String,
    val rating: Int,
    val content: String
)

data class SubmitM21VerificationInput(
    val verificationType: M21VerificationType,
    val displayLabel: String,
    val licenseCredential: M21LicenseCredential? = null
)

data class SubmitM21AppealInput(
    val reviewId: String,
    val reason: String
)

object M21MockUsers {
    const val ADMIN = "mock_user_admin"
    const val REVIEWER = "mock_user_reviewer"
    const val ORG_MANAGER = "mock_user_org_manager"
    const val EMPTY_PROFILE = "mock_user_empty_reputation"
}

object M21MockTargetIds {
    const val ADOPTION = "mock_adoption_m21_1"
    const val SERVICE = "mock_service_m21_1"
    const val ORGANIZATION = "mock_org_m21_1"
}

object M21PermissionCodes {
    const val REPUTATION_VIEW = "reputation.view"
    const val REVIEW_SUBMIT = "reputation.review.submit"
    const val VERIFICATION_SUBMIT = "reputation.verification.submit"
}
