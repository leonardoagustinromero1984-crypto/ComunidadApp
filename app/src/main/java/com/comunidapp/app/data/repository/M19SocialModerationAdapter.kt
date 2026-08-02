package com.comunidapp.app.data.repository

import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.domain.moderation.ModerationReasonCodes
import com.comunidapp.app.domain.moderation.ModerationReportRules
import com.comunidapp.app.domain.moderation.ModerationTargetRef
import com.comunidapp.app.domain.moderation.ModerationTargetType

object M19SocialModerationAdapter {

    suspend fun reportPost(
        postId: String,
        reason: String,
        details: String? = null,
        reporterId: String
    ): Result<Unit> = submit(
        targetId = postId,
        otherDescription = "M19_SOCIAL_POST",
        reason = reason,
        details = details,
        reporterId = reporterId
    )

    suspend fun reportComment(
        commentId: String,
        reason: String,
        details: String? = null,
        reporterId: String
    ): Result<Unit> = submit(
        targetId = commentId,
        otherDescription = "M19_SOCIAL_COMMENT",
        reason = reason,
        details = details,
        reporterId = reporterId
    )

    suspend fun reportPostImage(
        imageRef: String,
        reason: String,
        details: String? = null,
        reporterId: String
    ): Result<Unit> = submit(
        targetId = imageRef,
        otherDescription = "M19_SOCIAL_POST_IMAGE",
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
