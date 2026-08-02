package com.comunidapp.app.data.repository

import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.domain.moderation.ModerationReasonCodes
import com.comunidapp.app.domain.moderation.ModerationReportRules
import com.comunidapp.app.domain.moderation.ModerationTargetRef
import com.comunidapp.app.domain.moderation.ModerationTargetType

object M17CampaignModerationAdapter {

    suspend fun reportCampaign(
        campaignId: String,
        reason: String,
        details: String? = null,
        reporterId: String
    ): Result<Unit> = submit(
        targetId = campaignId,
        otherDescription = "M17_DONATION_CAMPAIGN",
        reason = reason,
        details = details,
        reporterId = reporterId
    )

    suspend fun reportCampaignUpdate(
        updateId: String,
        reason: String,
        details: String? = null,
        reporterId: String
    ): Result<Unit> = submit(
        targetId = updateId,
        otherDescription = "M17_CAMPAIGN_UPDATE",
        reason = reason,
        details = details,
        reporterId = reporterId
    )

    suspend fun reportCampaignImage(
        imageRef: String,
        reason: String,
        details: String? = null,
        reporterId: String
    ): Result<Unit> = submit(
        targetId = imageRef,
        otherDescription = "M17_CAMPAIGN_IMAGE",
        reason = reason,
        details = details,
        reporterId = reporterId
    )

    suspend fun reportInKindNeed(
        needId: String,
        reason: String,
        details: String? = null,
        reporterId: String
    ): Result<Unit> = submit(
        targetId = needId,
        otherDescription = "M17_IN_KIND_NEED",
        reason = reason,
        details = details,
        reporterId = reporterId
    )

    suspend fun reportVolunteerOpportunity(
        opportunityId: String,
        reason: String,
        details: String? = null,
        reporterId: String
    ): Result<Unit> = submit(
        targetId = opportunityId,
        otherDescription = "M17_VOLUNTEER_OPPORTUNITY",
        reason = reason,
        details = details,
        reporterId = reporterId
    )

    suspend fun reportTransparencyReport(
        reportId: String,
        reason: String,
        details: String? = null,
        reporterId: String
    ): Result<Unit> = submit(
        targetId = reportId,
        otherDescription = "M17_TRANSPARENCY_REPORT",
        reason = reason,
        details = details,
        reporterId = reporterId
    )

    suspend fun reportInKindNeedImage(
        imageRef: String,
        reason: String,
        details: String? = null,
        reporterId: String
    ): Result<Unit> = submit(
        targetId = imageRef,
        otherDescription = "M17_IN_KIND_NEED_IMAGE",
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
