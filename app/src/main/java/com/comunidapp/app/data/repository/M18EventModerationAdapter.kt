package com.comunidapp.app.data.repository

import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.domain.moderation.ModerationReasonCodes
import com.comunidapp.app.domain.moderation.ModerationReportRules
import com.comunidapp.app.domain.moderation.ModerationTargetRef
import com.comunidapp.app.domain.moderation.ModerationTargetType

object M18EventModerationAdapter {

    suspend fun reportEvent(
        eventId: String,
        reason: String,
        details: String? = null,
        reporterId: String
    ): Result<Unit> = submit(
        targetId = eventId,
        otherDescription = "M18_COMMUNITY_EVENT",
        reason = reason,
        details = details,
        reporterId = reporterId
    )

    suspend fun reportEventImage(
        imageRef: String,
        reason: String,
        details: String? = null,
        reporterId: String
    ): Result<Unit> = submit(
        targetId = imageRef,
        otherDescription = "M18_EVENT_IMAGE",
        reason = reason,
        details = details,
        reporterId = reporterId
    )

    suspend fun reportOrganizerContent(
        eventId: String,
        reason: String,
        details: String? = null,
        reporterId: String
    ): Result<Unit> = submit(
        targetId = eventId,
        otherDescription = "M18_EVENT_ORGANIZER_CONTENT",
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
