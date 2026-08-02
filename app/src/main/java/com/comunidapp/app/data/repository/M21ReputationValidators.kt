package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.M21LicenseCredential
import com.comunidapp.app.data.model.M21ReviewDisputeReason
import com.comunidapp.app.data.model.M21ReviewTargetType
import com.comunidapp.app.data.model.M21VerificationType
import com.comunidapp.app.domain.m21.M21ReviewEligibilityService

object M21ReputationValidators {

    fun validateReviewContent(content: String, rating: Int): String? = when {
        rating !in 1..5 -> "M21_INVALID_RATING"
        content.trim().isEmpty() -> "M21_INVALID_REVIEW"
        content.length > 2000 -> "M21_INVALID_REVIEW"
        containsUnsafeMarkup(content) -> "M21_INVALID_REVIEW"
        containsPii(content) -> "M21_INVALID_REVIEW"
        else -> null
    }

    fun validateReviewTitle(title: String?): String? {
        if (title == null) return null
        return when {
            title.trim().isEmpty() -> "M21_INVALID_REVIEW"
            title.length > 120 -> "M21_INVALID_REVIEW"
            containsUnsafeMarkup(title) -> "M21_INVALID_REVIEW"
            else -> null
        }
    }

    fun validateEditReview(
        actorUserId: String,
        reviewerUserId: String,
        rating: Int?,
        content: String?,
        title: String?
    ): String? {
        if (actorUserId != reviewerUserId) return "M21_PERMISSION_DENIED"
        rating?.let { if (it !in 1..5) return "M21_INVALID_RATING" }
        content?.let {
            if (it.trim().isEmpty() || it.length > 2000 || containsUnsafeMarkup(it) || containsPii(it)) {
                return "M21_INVALID_REVIEW"
            }
        }
        validateReviewTitle(title)?.let { return it }
        return null
    }

    fun validateSelfReview(actorUserId: String, targetType: M21ReviewTargetType, targetId: String): String? =
        if (M21ReviewEligibilityService.isSelfReview(
                actorUserId,
                com.comunidapp.app.data.model.M21ReviewSubjectReference(targetType, targetId, "")
            )
        ) {
            "M21_SELF_REVIEW"
        } else {
            null
        }

    fun validateReviewResponse(content: String): String? = when {
        content.trim().length < 5 -> "M21_INVALID_RESPONSE"
        content.length > 2000 -> "M21_INVALID_RESPONSE"
        containsUnsafeMarkup(content) -> "M21_INVALID_RESPONSE"
        containsPii(content) -> "M21_INVALID_RESPONSE"
        else -> null
    }

    fun validateDispute(details: String): String? = when {
        details.trim().length < 10 -> "M21_INVALID_DISPUTE"
        details.length > 2000 -> "M21_INVALID_DISPUTE"
        containsUnsafeMarkup(details) -> "M21_INVALID_DISPUTE"
        else -> null
    }

    fun validateDisputeReason(reason: M21ReviewDisputeReason): String? = null

    fun validateAppealReason(reason: String): String? = when {
        reason.trim().length < 10 -> "M21_INVALID_APPEAL"
        reason.length > 1000 -> "M21_INVALID_APPEAL"
        else -> null
    }

    fun validateVerificationInput(
        type: M21VerificationType,
        displayLabel: String,
        license: M21LicenseCredential?
    ): String? = when {
        displayLabel.trim().isEmpty() -> "M21_INVALID_VERIFICATION"
        displayLabel.length > 120 -> "M21_INVALID_VERIFICATION"
        type == M21VerificationType.PROFESSIONAL_LICENSE && license == null -> "M21_LICENSE_REQUIRED"
        license != null && license.licenseNumber.trim().isEmpty() -> "M21_LICENSE_REQUIRED"
        else -> null
    }

    private fun containsUnsafeMarkup(text: String): Boolean =
        Regex("(?i)<script|javascript:|on\\w+\\s*=|<iframe").containsMatchIn(text)

    private fun containsPii(text: String): Boolean =
        Regex("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}").containsMatchIn(text) ||
            Regex("(?i)(dni|cuil|cuit)\\s*[:#]?\\s*\\d").containsMatchIn(text)
}
