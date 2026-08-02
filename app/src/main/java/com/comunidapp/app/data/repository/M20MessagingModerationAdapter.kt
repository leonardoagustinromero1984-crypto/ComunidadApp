package com.comunidapp.app.data.repository

import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.domain.moderation.ModerationReasonCodes
import com.comunidapp.app.domain.moderation.ModerationReportRules
import com.comunidapp.app.domain.moderation.ModerationTargetRef
import com.comunidapp.app.domain.moderation.ModerationTargetType

object M20MessagingModerationAdapter {

    suspend fun reportMessage(
        messageId: String,
        reason: String,
        details: String? = null,
        reporterId: String
    ): Result<Unit> = submit(
        targetId = messageId,
        otherDescription = "M20_MESSAGE",
        reason = reason,
        details = details,
        reporterId = reporterId
    )

    suspend fun reportConversation(
        conversationId: String,
        reason: String,
        details: String? = null,
        reporterId: String
    ): Result<Unit> = submit(
        targetId = conversationId,
        otherDescription = "M20_CONVERSATION",
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
