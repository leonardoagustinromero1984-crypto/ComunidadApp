package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.M21LicenseCredential
import com.comunidapp.app.data.model.M21VerificationType

object M21ReputationValidators {

    fun validateReviewContent(content: String, rating: Int): String? = when {
        rating !in 1..5 -> "M21_INVALID_RATING"
        content.trim().isEmpty() -> "M21_INVALID_REVIEW"
        content.length > 2000 -> "M21_INVALID_REVIEW"
        containsUnsafeMarkup(content) -> "M21_INVALID_REVIEW"
        else -> null
    }

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
}
