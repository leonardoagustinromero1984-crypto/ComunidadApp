package com.comunidapp.app.domain.moderation

enum class ModerationCaseStatus {
    OPEN,
    TRIAGED,
    IN_REVIEW,
    ACTION_REQUIRED,
    RESOLVED,
    DISMISSED,
    CLOSED
}

data class ModerationCase(
    val id: String,
    val title: String,
    val status: ModerationCaseStatus = ModerationCaseStatus.OPEN,
    val priority: ModerationPriority = ModerationPriority.NORMAL,
    val assignedToUserId: String? = null,
    val createdByUserId: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val closedAtEpochMs: Long? = null,
    val closeReasonCode: String? = null
)

/** Nota interna: nunca en proyecciones públicas. */
data class ModerationCaseNote(
    val id: String,
    val caseId: String,
    val authorUserId: String,
    val body: String,
    val createdAtEpochMs: Long
)

data class ModerationAssignment(
    val id: String,
    val resourceType: AdministrativeResourceType,
    val resourceId: String,
    val assignedToUserId: String,
    val assignedByUserId: String,
    val assignedAtEpochMs: Long
)

enum class AdministrativeResourceType {
    MODERATION_CASE,
    VERIFICATION_REVIEW,
    SUPPORT_TICKET
}

object ModerationCaseRules {

    const val TITLE_MIN = 3
    const val TITLE_MAX = 160
    const val NOTE_MAX = 2000

    fun validateCreate(
        title: String,
        createdByUserId: String,
        nowEpochMs: Long
    ): Result<ModerationCase> {
        val t = title.trim()
        if (t.length < TITLE_MIN || t.length > TITLE_MAX) {
            return Result.failure(IllegalArgumentException("TITLE_INVALID"))
        }
        if (createdByUserId.isBlank()) {
            return Result.failure(IllegalArgumentException("CREATOR_REQUIRED"))
        }
        return Result.success(
            ModerationCase(
                id = "",
                title = t,
                createdByUserId = createdByUserId,
                createdAtEpochMs = nowEpochMs,
                updatedAtEpochMs = nowEpochMs
            )
        )
    }

    /**
     * Un reporte activo no puede pertenecer a dos casos.
     * Activo = caseId no nulo y status no CLOSED/DISMISSED/DUPLICATE del reporte.
     */
    fun canAttachReport(
        report: ModerationReport,
        caseId: String
    ): Result<Unit> {
        if (caseId.isBlank()) {
            return Result.failure(IllegalArgumentException("CASE_REQUIRED"))
        }
        val existing = report.caseId
        if (existing != null && existing != caseId &&
            report.status !in setOf(
                ModerationReportStatus.CLOSED,
                ModerationReportStatus.DISMISSED,
                ModerationReportStatus.DUPLICATE
            )
        ) {
            return Result.failure(IllegalArgumentException("REPORT_ALREADY_IN_CASE"))
        }
        return Result.success(Unit)
    }

    fun canClose(
        case: ModerationCase,
        closeReasonCode: String?
    ): Result<Unit> {
        if (case.status == ModerationCaseStatus.CLOSED) {
            return Result.failure(IllegalArgumentException("ALREADY_CLOSED"))
        }
        val resolved = case.status == ModerationCaseStatus.RESOLVED ||
            case.status == ModerationCaseStatus.DISMISSED
        if (!resolved && closeReasonCode.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("CLOSE_REASON_REQUIRED"))
        }
        return Result.success(Unit)
    }

    fun validateNote(body: String): Result<String> {
        val text = body.trim()
        if (text.isEmpty() || text.length > NOTE_MAX) {
            return Result.failure(IllegalArgumentException("NOTE_INVALID"))
        }
        return Result.success(text)
    }
}
