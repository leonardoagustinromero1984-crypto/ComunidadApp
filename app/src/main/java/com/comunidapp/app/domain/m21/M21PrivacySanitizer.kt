package com.comunidapp.app.domain.m21

import com.comunidapp.app.data.model.M21PublicReputationSummary
import com.comunidapp.app.data.model.M21PublicReview
import com.comunidapp.app.data.model.M21PublicReviewResponse
import com.comunidapp.app.data.model.M21PublicVerification
import com.comunidapp.app.data.model.M21ReputationSummary
import com.comunidapp.app.data.model.M21Review
import com.comunidapp.app.data.model.M21ReviewResponse
import com.comunidapp.app.data.model.M21ReviewResponseStatus
import com.comunidapp.app.data.model.M21ReviewStatus
import com.comunidapp.app.data.model.M21VerificationRequest
import com.comunidapp.app.data.model.M21VerificationStatus

object M21PrivacySanitizer {

    private val emailPattern = Regex("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}")
    private val phonePattern = Regex("(?i)(\\+?\\d[\\d\\s().-]{6,}\\d)")
    private val addressPattern = Regex("(?i)(calle|av\\.?|avenida|pasaje)\\s+[\\w\\s\\d]+")
    private val documentPattern = Regex("(?i)(dni|cuil|cuit|passport|documento)\\s*[:#]?\\s*[\\w\\d-]+")
    private val bankPattern = Regex("(?i)(cbu|cvu|iban|cuenta)\\s*[:#]?\\s*[\\w\\d-]+")
    private val tokenPattern = Regex("(?i)(bearer\\s+|token\\s*[:=]\\s*)[\\w.-]+")

    fun scrubPublicText(text: String): String =
        text.replace(emailPattern, "[redactado]")
            .replace(phonePattern, "[redactado]")
            .replace(addressPattern, "[redactado]")
            .replace(documentPattern, "[redactado]")
            .replace(bankPattern, "[redactado]")
            .replace(tokenPattern, "[redactado]")
            .trim()

    fun toPublicReview(
        review: M21Review,
        isOwnReview: Boolean = false,
        publicResponse: M21PublicReviewResponse? = null
    ): M21PublicReview {
        val badge = if (review.contextReference != null && review.status in publicStatuses) {
            "Experiencia verificada"
        } else {
            null
        }
        return M21PublicReview(
            id = review.id,
            targetType = review.targetType,
            targetDisplayLabel = scrubPublicText(review.targetDisplayLabel),
            reviewerDisplayName = scrubPublicText(review.reviewerDisplayName),
            rating = review.rating.coerceIn(1, 5),
            content = scrubPublicText(review.content),
            status = review.status,
            createdAt = review.createdAt,
            isOwnReview = isOwnReview,
            title = review.title?.let { scrubPublicText(it) },
            hasResponse = review.hasResponse && publicResponse != null,
            publicResponse = publicResponse?.takeIf {
                it.status == M21ReviewResponseStatus.PUBLISHED || it.status == M21ReviewResponseStatus.EDITED
            },
            eligibleExperienceBadge = badge
        )
    }

    fun toPublicReviewResponse(response: M21ReviewResponse): M21PublicReviewResponse? =
        when (response.status) {
            M21ReviewResponseStatus.HIDDEN,
            M21ReviewResponseStatus.REMOVED_BY_MODERATION -> null
            else -> M21PublicReviewResponse(
                id = response.id,
                content = scrubPublicText(response.content),
                status = response.status,
                createdAt = response.createdAt,
                updatedAt = response.updatedAt
            )
        }

    fun toPublicVerification(request: M21VerificationRequest, isOwn: Boolean): M21PublicVerification {
        val publicStatus = when (request.status) {
            M21VerificationStatus.APPROVED -> request.status
            M21VerificationStatus.PENDING,
            M21VerificationStatus.UNDER_REVIEW -> request.status
            else -> request.status
        }
        val showSignal = request.status == M21VerificationStatus.APPROVED
        return M21PublicVerification(
            id = request.id,
            verificationType = request.verificationType,
            status = if (showSignal) publicStatus else request.status,
            displayLabel = if (showSignal) scrubPublicText(request.displayLabel) else scrubPublicText(request.displayLabel),
            licenseSummary = request.licenseCredential?.let {
                if (showSignal) {
                    "${scrubPublicText(it.issuingAuthority)} · ${scrubPublicText(it.jurisdiction)}"
                } else {
                    null
                }
            },
            submittedAt = request.submittedAt,
            isOwnRequest = isOwn
        )
    }

    fun toPublicSummary(summary: M21ReputationSummary): M21PublicReputationSummary =
        M21PublicReputationSummary(
            reputationScore = summary.reputationScore.coerceAtLeast(0),
            publishedReviewCount = summary.publishedReviewCount.coerceAtLeast(0),
            averageRating = summary.averageRating,
            badges = summary.badges,
            identityVerified = summary.identityVerified,
            licenseVerified = summary.licenseVerified,
            ratingDistribution = summary.ratingDistribution,
            reviewsWithResponseCount = summary.reviewsWithResponseCount,
            lastReviewAt = summary.lastReviewAt
        )

    fun isPublicReviewStatus(status: M21ReviewStatus): Boolean = status in publicStatuses

    private val publicStatuses = setOf(
        M21ReviewStatus.PUBLISHED,
        M21ReviewStatus.EDITED,
        M21ReviewStatus.DISPUTED,
        M21ReviewStatus.APPEALED
    )
}
