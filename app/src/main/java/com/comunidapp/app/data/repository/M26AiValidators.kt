package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.M26AssistanceSessionStatus
import com.comunidapp.app.data.model.M26DuplicateStatus
import com.comunidapp.app.data.model.M26RecommendationStatus
import com.comunidapp.app.data.model.M26VisualMatchStatus

object M26AiValidators {
    fun validateVisualMatch(sourceLabel: String, targetLabel: String): String? = when {
        !isSafeLabel(sourceLabel) || !isSafeLabel(targetLabel) -> "M26_INVALID_MATCH"
        sourceLabel.trim().equals(targetLabel.trim(), ignoreCase = true) -> "M26_INVALID_MATCH"
        else -> null
    }

    fun validateScore(score: Double): String? = when {
        score.isNaN() || score < 0.0 || score > 1.0 -> "M26_INVALID_SCORE"
        else -> null
    }

    fun validateAssistancePrompt(prompt: String): String? = when {
        prompt.trim().length !in 5..1_000 || unsafe(prompt) -> "M26_INVALID_ASSISTANCE"
        else -> null
    }

    fun validateRecommendation(title: String, rationale: String): String? = when {
        !isSafeTitle(title) || !isSafeRationale(rationale) -> "M26_INVALID_RECOMMENDATION"
        else -> null
    }

    fun validateMatchTransition(current: M26VisualMatchStatus, target: M26VisualMatchStatus): String? =
        when {
            current == target -> null
            current == M26VisualMatchStatus.EXPIRED -> "M26_PERMISSION_DENIED"
            target == M26VisualMatchStatus.PENDING && current != M26VisualMatchStatus.PENDING -> "M26_PERMISSION_DENIED"
            else -> null
        }

    fun validateDuplicateTransition(current: M26DuplicateStatus, target: M26DuplicateStatus): String? =
        when {
            current == target -> null
            current != M26DuplicateStatus.OPEN -> "M26_PERMISSION_DENIED"
            target !in setOf(M26DuplicateStatus.CONFIRMED, M26DuplicateStatus.DISMISSED) -> "M26_PERMISSION_DENIED"
            else -> null
        }

    fun validateSessionClose(status: M26AssistanceSessionStatus): String? =
        if (status != M26AssistanceSessionStatus.ACTIVE) "M26_SESSION_ALREADY_CLOSED" else null

    fun validateReviewTransition(current: M26RecommendationStatus, approved: Boolean): String? = when {
        current !in setOf(M26RecommendationStatus.DRAFT, M26RecommendationStatus.PENDING_REVIEW) -> "M26_PERMISSION_DENIED"
        !approved && current == M26RecommendationStatus.APPROVED -> "M26_PERMISSION_DENIED"
        else -> null
    }

    private fun isSafeLabel(value: String): Boolean =
        value.trim().length in 2..120 && !unsafe(value)

    private fun isSafeTitle(value: String): Boolean =
        value.trim().length in 3..160 && !unsafe(value)

    private fun isSafeRationale(value: String): Boolean =
        value.trim().length in 10..2_000 && !unsafe(value)

    private fun unsafe(value: String): Boolean =
        Regex("(?i)<script|javascript:|on\\w+\\s*=|<iframe").containsMatchIn(value)
}
