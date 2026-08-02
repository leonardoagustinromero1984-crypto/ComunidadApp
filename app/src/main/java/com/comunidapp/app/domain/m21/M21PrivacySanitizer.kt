package com.comunidapp.app.domain.m21

import com.comunidapp.app.data.model.M21PublicReputationSummary
import com.comunidapp.app.data.model.M21PublicReview
import com.comunidapp.app.data.model.M21PublicVerification
import com.comunidapp.app.data.model.M21ReputationSummary
import com.comunidapp.app.data.model.M21Review
import com.comunidapp.app.data.model.M21VerificationRequest

object M21PrivacySanitizer {

    private val emailPattern = Regex("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}")
    private val phonePattern = Regex("(?i)(\\+?\\d[\\d\\s().-]{6,}\\d)")

    fun scrubPublicText(text: String): String =
        text.replace(emailPattern, "[redactado]")
            .replace(phonePattern, "[redactado]")
            .trim()

    fun toPublicReview(review: M21Review, isOwnReview: Boolean = false): M21PublicReview =
        M21PublicReview(
            id = review.id,
            targetType = review.targetType,
            targetDisplayLabel = scrubPublicText(review.targetDisplayLabel),
            reviewerDisplayName = scrubPublicText(review.reviewerDisplayName),
            rating = review.rating.coerceIn(1, 5),
            content = scrubPublicText(review.content),
            status = review.status,
            createdAt = review.createdAt,
            isOwnReview = isOwnReview
        )

    fun toPublicVerification(request: M21VerificationRequest, isOwn: Boolean): M21PublicVerification =
        M21PublicVerification(
            id = request.id,
            verificationType = request.verificationType,
            status = request.status,
            displayLabel = scrubPublicText(request.displayLabel),
            licenseSummary = request.licenseCredential?.let {
                "${scrubPublicText(it.issuingAuthority)} · ${scrubPublicText(it.jurisdiction)}"
            },
            submittedAt = request.submittedAt,
            isOwnRequest = isOwn
        )

    fun toPublicSummary(summary: M21ReputationSummary): M21PublicReputationSummary =
        M21PublicReputationSummary(
            reputationScore = summary.reputationScore.coerceAtLeast(0),
            publishedReviewCount = summary.publishedReviewCount.coerceAtLeast(0),
            averageRating = summary.averageRating,
            badges = summary.badges,
            identityVerified = summary.identityVerified,
            licenseVerified = summary.licenseVerified
        )
}
