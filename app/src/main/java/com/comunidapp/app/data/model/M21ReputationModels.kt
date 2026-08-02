package com.comunidapp.app.data.model

import com.comunidapp.app.domain.m21.M21PrivacySanitizer

/** LeoVer M21 — Reputación, verificaciones y reseñas (Bloques 1–3). */

enum class M21ReviewTargetType {
    ADOPTION,
    SERVICE,
    DONATION,
    ORGANIZATION,
    USER
}

enum class M21ReviewContextType {
    ADOPTION_COMPLETED,
    FOSTER_COMPLETED,
    SERVICE_COMPLETED,
    DONATION_COMPLETED,
    EVENT_ATTENDED,
    SHELTER_INTERACTION,
    SUPPORT_CONVERSATION
}

enum class M21ReviewEligibilityReason {
    COMPLETED_INTERACTION,
    ALREADY_REVIEWED,
    CONTEXT_CANCELLED,
    CONTEXT_REJECTED,
    SELF_REVIEW,
    NOT_ELIGIBLE,
    ELIGIBILITY_UNAVAILABLE,
    EXPIRED
}

enum class M21ReviewStatus {
    DRAFT,
    PENDING,
    PUBLISHED,
    EDITED,
    HIDDEN,
    ARCHIVED,
    DISPUTED,
    REMOVED,
    REMOVED_BY_MODERATION,
    APPEALED
}

enum class M21VerificationType {
    IDENTITY,
    PROFESSIONAL_LICENSE
}

enum class M21VerificationStatus {
    NOT_REQUESTED,
    NOT_SUBMITTED,
    PENDING,
    UNDER_REVIEW,
    APPROVED,
    REJECTED,
    EXPIRED,
    REVOKED
}

enum class M21AppealStatus {
    OPEN,
    UNDER_REVIEW,
    RESOLVED,
    DISMISSED
}

enum class M21ReviewResponseStatus {
    PUBLISHED,
    EDITED,
    HIDDEN,
    REMOVED_BY_MODERATION
}

enum class M21ReviewDisputeReason {
    FACTUAL_ERROR,
    CONFLICT_OF_INTEREST,
    HARASSMENT,
    SPAM,
    PERSONAL_DATA,
    OTHER
}

enum class M21ReviewDisputeStatus {
    OPEN,
    UNDER_REVIEW,
    RESOLVED,
    DISMISSED
}

enum class M21ReviewRiskReason {
    SELF_REVIEW,
    DUPLICATE_CONTEXT,
    INELIGIBLE_CONTEXT,
    RATING_OUT_OF_RANGE,
    BURST_ACTIVITY,
    PII_IN_CONTENT,
    SUBJECT_MANIPULATION
}

data class M21ReviewContextReference(
    val contextType: M21ReviewContextType,
    val contextId: String,
    val publicLabel: String
)

data class M21ReviewSubjectReference(
    val targetType: M21ReviewTargetType,
    val targetId: String,
    val displayLabel: String
)

data class M21ReviewEligibility(
    val eligible: Boolean,
    val reason: M21ReviewEligibilityReason,
    val subject: M21ReviewSubjectReference,
    val contextReference: M21ReviewContextReference? = null
)

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
    val updatedAt: Long,
    val title: String? = null,
    val contextReference: M21ReviewContextReference? = null,
    val editCount: Int = 0,
    val hasResponse: Boolean = false
) {
    fun toPublicReview(
        isOwnReview: Boolean = false,
        publicResponse: M21PublicReviewResponse? = null
    ): M21PublicReview =
        M21PrivacySanitizer.toPublicReview(this, isOwnReview, publicResponse)
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
    val isOwnReview: Boolean = false,
    val title: String? = null,
    val hasResponse: Boolean = false,
    val publicResponse: M21PublicReviewResponse? = null,
    val eligibleExperienceBadge: String? = null
)

data class M21ReviewResponse(
    val id: String,
    val reviewId: String,
    val responderUserId: String,
    val content: String,
    val status: M21ReviewResponseStatus,
    val createdAt: Long,
    val updatedAt: Long,
    val editCount: Int = 0
) {
    fun toPublicResponse(): M21PublicReviewResponse? =
        M21PrivacySanitizer.toPublicReviewResponse(this)
}

data class M21PublicReviewResponse(
    val id: String,
    val content: String,
    val status: M21ReviewResponseStatus,
    val createdAt: Long,
    val updatedAt: Long
)

/** Señal interna de antiabuso; no expuesta en API pública. */
data class M21ReviewRiskSignal(
    val reviewId: String,
    val reason: M21ReviewRiskReason,
    val detectedAt: Long,
    val explanation: String
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
    val rejectionReason: String? = null,
    val evidenceRef: String? = null
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

data class M21ReviewDispute(
    val id: String,
    val reviewId: String,
    val claimantUserId: String,
    val reason: M21ReviewDisputeReason,
    val details: String,
    val status: M21ReviewDisputeStatus,
    val createdAt: Long,
    val evidenceRef: String? = null
)

data class M21RatingDistribution(
    val oneStar: Int = 0,
    val twoStars: Int = 0,
    val threeStars: Int = 0,
    val fourStars: Int = 0,
    val fiveStars: Int = 0
) {
    val total: Int get() = oneStar + twoStars + threeStars + fourStars + fiveStars
}

data class M21ReputationBreakdown(
    val subject: M21ReviewSubjectReference,
    val averageRating: Double?,
    val publishedReviewCount: Int,
    val ratingDistribution: M21RatingDistribution,
    val reviewsWithResponseCount: Int,
    val lastReviewAt: Long?,
    val reviews: List<M21PublicReview>
)

data class M21ReputationSummary(
    val userId: String,
    val reputationScore: Int,
    val publishedReviewCount: Int,
    val averageRating: Double?,
    val badges: List<UserBadge>,
    val identityVerified: Boolean,
    val licenseVerified: Boolean,
    val ratingDistribution: M21RatingDistribution = M21RatingDistribution(),
    val reviewsWithResponseCount: Int = 0,
    val lastReviewAt: Long? = null
) {
    fun toPublicSummary(): M21PublicReputationSummary = M21PrivacySanitizer.toPublicSummary(this)
}

data class M21PublicReputationSummary(
    val reputationScore: Int,
    val publishedReviewCount: Int,
    val averageRating: Double?,
    val badges: List<UserBadge>,
    val identityVerified: Boolean,
    val licenseVerified: Boolean,
    val ratingDistribution: M21RatingDistribution = M21RatingDistribution(),
    val reviewsWithResponseCount: Int = 0,
    val lastReviewAt: Long? = null
)

data class SubmitM21ReviewInput(
    val targetType: M21ReviewTargetType,
    val targetId: String,
    val targetDisplayLabel: String,
    val rating: Int,
    val content: String,
    val title: String? = null,
    val contextReference: M21ReviewContextReference? = null
)

data class EditM21ReviewInput(
    val reviewId: String,
    val rating: Int? = null,
    val content: String? = null,
    val title: String? = null
)

data class SubmitM21ReviewResponseInput(
    val reviewId: String,
    val content: String
)

data class SubmitM21DisputeInput(
    val reviewId: String,
    val reason: M21ReviewDisputeReason,
    val details: String,
    val evidenceRef: String? = null
)

data class ReportM21ReviewInput(
    val reviewId: String,
    val reason: String,
    val details: String? = null,
    val reportResponse: Boolean = false
)

data class CheckM21EligibilityInput(
    val targetType: M21ReviewTargetType,
    val targetId: String,
    val targetDisplayLabel: String,
    val contextReference: M21ReviewContextReference? = null
)

data class SubmitM21VerificationInput(
    val verificationType: M21VerificationType,
    val displayLabel: String,
    val licenseCredential: M21LicenseCredential? = null,
    val evidenceRef: String? = null
)

data class SubmitM21AppealInput(
    val reviewId: String,
    val reason: String
)

/** Stub M06 — infraestructura de notificaciones no disponible en Bloque 3 local. */
data class M21NotificationHookState(
    val available: Boolean = false,
    val message: String = "M21_NOTIFICATIONS_UNAVAILABLE"
)

object M21MockUsers {
    const val ADMIN = "mock_user_admin"
    const val REVIEWER = "mock_user_reviewer"
    const val ORG_MANAGER = "mock_user_org_manager"
    const val EMPTY_PROFILE = "mock_user_empty_reputation"
    const val SERVICE_PROVIDER = "mock_user_service_provider"
}

object M21MockTargetIds {
    const val ADOPTION = "mock_adoption_m21_1"
    const val SERVICE = "mock_service_m21_1"
    const val ORGANIZATION = "mock_org_m21_1"
    const val DONATION = "mock_donation_m21_1"
}

object M21MockEligibilityIds {
    const val ADOPTION_COMPLETED = "mock_ctx_adoption_completed"
    const val SERVICE_COMPLETED = "mock_ctx_service_completed"
    const val DONATION_COMPLETED = "mock_ctx_donation_completed"
    const val EVENT_ATTENDED = "mock_ctx_event_attended"
    const val SHELTER_INTERACTION = "mock_ctx_shelter_interaction"
    const val CANCELLED_CONTEXT = "mock_ctx_cancelled"
    const val REJECTED_CONTEXT = "mock_ctx_rejected"
    const val EXPIRED_CONTEXT = "mock_ctx_expired"
    const val DUPLICATE_CONTEXT = "mock_ctx_duplicate"
    const val NOT_ELIGIBLE_VISIT = "mock_ctx_profile_visit"
}

object M21PermissionCodes {
    const val REPUTATION_VIEW = "reputation.view"
    const val REVIEW_SUBMIT = "reputation.review.submit"
    const val VERIFICATION_SUBMIT = "reputation.verification.submit"
    const val REVIEW_RESPOND = "reputation.review.respond"
    const val REVIEW_DISPUTE = "reputation.review.dispute"
}
