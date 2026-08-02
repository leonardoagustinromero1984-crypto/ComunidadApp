package com.comunidapp.app.data.repository

import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.domain.moderation.ModerationReasonCodes
import com.comunidapp.app.domain.moderation.ModerationReportRules
import com.comunidapp.app.domain.moderation.ModerationTargetRef
import com.comunidapp.app.domain.moderation.ModerationTargetType

object M21ReputationModerationAdapter {

    suspend fun reportReview(
        reviewId: String,
        reason: String,
        details: String? = null,
        reporterId: String
    ): Result<Unit> = submit(
        targetId = reviewId,
        otherDescription = "M21_REVIEW",
        reason = reason,
        details = details,
        reporterId = reporterId
    )

    suspend fun reportReviewResponse(
        responseId: String,
        reason: String,
        details: String? = null,
        reporterId: String
    ): Result<Unit> = submit(
        targetId = responseId,
        otherDescription = "M21_REVIEW_RESPONSE",
        reason = reason,
        details = details,
        reporterId = reporterId
    )

    private suspend fun submit(
        targetId: String,
        otherDescription: String,
        reason: String,
        details: String?,
        reporterId: String
    ): Result<Unit> {
        val normalizedReason = reason.trim().lowercase().ifBlank { "other" }
        val reasonCode = if (normalizedReason in ModerationReasonCodes.REPORT) normalizedReason else "other"
        val target = ModerationTargetRef(
            type = ModerationTargetType.OTHER,
            targetId = targetId,
            otherDescription = otherDescription
        )
        val validated = ModerationReportRules.validateNewReport(
            reporterId = reporterId,
            target = target,
            reasonCode = reasonCode,
            description = details,
            nowEpochMs = System.currentTimeMillis()
        )
        validated.getOrElse { return Result.failure(it) }
        return when (
            val result = DataProvider.moderationRepository.createReport(
                reporterId = reporterId,
                target = target,
                reasonCode = reasonCode,
                description = details,
                nowEpochMs = System.currentTimeMillis()
            )
        ) {
            is AppResult.Success -> Result.success(Unit)
            is AppResult.Failure -> Result.failure(IllegalStateException(result.error.userMessage))
        }
    }
}
