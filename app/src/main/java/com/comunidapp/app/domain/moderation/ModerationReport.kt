package com.comunidapp.app.domain.moderation

/**
 * Target genérico de moderación. No resuelve ni embebe el recurso.
 */
enum class ModerationTargetType {
    USER_PROFILE,
    ORGANIZATION,
    POST,
    COMMENT,
    MESSAGE,
    PET_PROFILE,
    ADOPTION_LISTING,
    LOST_FOUND_CASE,
    SERVICE_PROFILE,
    PRODUCT,
    EVENT,
    OTHER
}

data class ModerationTargetRef(
    val type: ModerationTargetType,
    val targetId: String,
    /** Solo cuando [type] == OTHER. */
    val otherDescription: String? = null
)

/**
 * Compatibilidad con content_reports legacy (POST | USER | COMMENT).
 */
object ModerationLegacyTargets {

    fun fromLegacyType(raw: String): ModerationTargetType = when (raw.trim().uppercase()) {
        "USER" -> ModerationTargetType.USER_PROFILE
        "POST" -> ModerationTargetType.POST
        "COMMENT" -> ModerationTargetType.COMMENT
        else -> runCatching {
            ModerationTargetType.valueOf(raw.trim().uppercase())
        }.getOrDefault(ModerationTargetType.OTHER)
    }

    fun toLegacyTypeOrNull(type: ModerationTargetType): String? = when (type) {
        ModerationTargetType.USER_PROFILE -> "USER"
        ModerationTargetType.POST -> "POST"
        ModerationTargetType.COMMENT -> "COMMENT"
        else -> null
    }

    fun isLegacyCompatible(type: ModerationTargetType): Boolean =
        toLegacyTypeOrNull(type) != null
}

enum class ModerationReportStatus {
    OPEN,
    TRIAGED,
    IN_REVIEW,
    ACTION_REQUIRED,
    RESOLVED,
    DISMISSED,
    DUPLICATE,
    CLOSED
}

enum class ModerationPriority {
    LOW,
    NORMAL,
    HIGH,
    URGENT
}

/**
 * Reason codes allowlisted (dominio). No secrets en mensajes.
 */
object ModerationReasonCodes {
    val REPORT = setOf(
        "spam", "harassment", "hate", "scam", "impersonation",
        "inappropriate", "violence", "privacy", "other"
    )
    val ACTION = setOf(
        "policy_violation", "safety", "spam", "legal", "ops_review",
        "appeal_accepted", "appeal_rejected", "other"
    )
}

data class ModerationReport(
    val id: String,
    val reporterId: String,
    val target: ModerationTargetRef,
    val reasonCode: String,
    val description: String? = null,
    val priority: ModerationPriority = ModerationPriority.NORMAL,
    val status: ModerationReportStatus = ModerationReportStatus.OPEN,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val caseId: String? = null,
    val duplicateOfReportId: String? = null
)

/** Proyección staff sin reporterId ni description sensible. */
data class PublicModerationReportSummary(
    val id: String,
    val target: ModerationTargetRef,
    val reasonCode: String,
    val priority: ModerationPriority,
    val status: ModerationReportStatus,
    val createdAtEpochMs: Long,
    val caseId: String? = null
)

object ModerationReportRules {

    const val TARGET_ID_MAX = 128
    const val DESCRIPTION_MAX = 2000
    const val OTHER_DESC_MAX = 200

    fun toPublicSummary(report: ModerationReport): PublicModerationReportSummary =
        PublicModerationReportSummary(
            id = report.id,
            target = report.target,
            reasonCode = report.reasonCode,
            priority = report.priority,
            status = report.status,
            createdAtEpochMs = report.createdAtEpochMs,
            caseId = report.caseId
        )

    fun validateNewReport(
        reporterId: String,
        target: ModerationTargetRef,
        reasonCode: String,
        description: String?,
        nowEpochMs: Long
    ): Result<ModerationReport> {
        if (reporterId.isBlank()) {
            return failure("REPORTER_REQUIRED")
        }
        validateTarget(target).getOrElse { return Result.failure(it) }
        val reason = reasonCode.trim().lowercase()
        if (reason !in ModerationReasonCodes.REPORT) {
            return failure("REASON_INVALID")
        }
        val desc = description?.trim()?.ifBlank { null }
        if (desc != null && desc.length > DESCRIPTION_MAX) {
            return failure("DESCRIPTION_TOO_LONG")
        }
        return Result.success(
            ModerationReport(
                id = "",
                reporterId = reporterId,
                target = target,
                reasonCode = reason,
                description = desc,
                priority = ModerationPriority.NORMAL,
                status = ModerationReportStatus.OPEN,
                createdAtEpochMs = nowEpochMs,
                updatedAtEpochMs = nowEpochMs
            )
        )
    }

    fun validateTarget(target: ModerationTargetRef): Result<Unit> {
        val id = target.targetId.trim()
        if (id.isEmpty() || id.length > TARGET_ID_MAX) {
            return failure("TARGET_ID_INVALID")
        }
        if (target.type == ModerationTargetType.OTHER) {
            val other = target.otherDescription?.trim().orEmpty()
            if (other.isEmpty() || other.length > OTHER_DESC_MAX) {
                return failure("OTHER_DESCRIPTION_REQUIRED")
            }
        }
        return Result.success(Unit)
    }

    fun canMarkDuplicate(report: ModerationReport, duplicateOfReportId: String): Result<Unit> {
        if (duplicateOfReportId.isBlank()) return failure("DUPLICATE_ID_REQUIRED")
        if (duplicateOfReportId == report.id) return failure("DUPLICATE_SELF")
        return Result.success(Unit)
    }

    fun mapLegacyStatus(raw: String): ModerationReportStatus = when (raw.trim().uppercase()) {
        "OPEN" -> ModerationReportStatus.OPEN
        "REVIEWED" -> ModerationReportStatus.IN_REVIEW
        "DISMISSED" -> ModerationReportStatus.DISMISSED
        "ACTIONED" -> ModerationReportStatus.ACTION_REQUIRED // cosmético legacy ≠ medida real
        else -> ModerationReportStatus.OPEN
    }

    /** ACTIONED legacy no implica sanción aplicada. */
    fun legacyActionedMeansRealMeasure(): Boolean = false

    private fun failure(code: String): Result<Nothing> =
        Result.failure(IllegalArgumentException(code))
}
