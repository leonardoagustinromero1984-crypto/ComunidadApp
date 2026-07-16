package com.comunidapp.app.domain.moderation

enum class ModerationAppealStatus {
    SUBMITTED,
    UNDER_REVIEW,
    UPHELD,
    OVERTURNED,
    PARTIALLY_OVERTURNED,
    REJECTED,
    CLOSED
}

data class ModerationAppeal(
    val id: String,
    val actionId: String,
    val submittedByUserId: String,
    val statement: String,
    val status: ModerationAppealStatus = ModerationAppealStatus.SUBMITTED,
    val reviewedByUserId: String? = null,
    val decisionReason: String? = null,
    val createdAtEpochMs: Long,
    val reviewedAtEpochMs: Long? = null
)

/**
 * Plazo configurable (contrato). Default 14 días en ms.
 */
object ModerationAppealPolicy {
    const val DEFAULT_WINDOW_MS: Long = 14L * 24L * 60L * 60L * 1000L
}

object ModerationAppealRules {

    const val STATEMENT_MIN = 10
    const val STATEMENT_MAX = 4000
    const val DECISION_REASON_MAX = 1000

    fun validateSubmit(
        action: ModerationAction,
        submittedByUserId: String,
        statement: String,
        nowEpochMs: Long,
        windowMs: Long = ModerationAppealPolicy.DEFAULT_WINDOW_MS,
        existingActiveAppeal: Boolean
    ): Result<ModerationAppeal> {
        if (submittedByUserId.isBlank()) return fail("SUBMITTER_REQUIRED")
        if (action.reversedAtEpochMs != null) return fail("ACTION_ALREADY_REVERSED")
        if (existingActiveAppeal) return fail("APPEAL_ALREADY_ACTIVE")
        if (nowEpochMs > action.appliedAtEpochMs + windowMs) return fail("APPEAL_WINDOW_EXPIRED")
        val text = statement.trim()
        if (text.length < STATEMENT_MIN || text.length > STATEMENT_MAX) {
            return fail("STATEMENT_INVALID")
        }
        return Result.success(
            ModerationAppeal(
                id = "",
                actionId = action.id,
                submittedByUserId = submittedByUserId,
                statement = text,
                createdAtEpochMs = nowEpochMs
            )
        )
    }

    fun isActive(status: ModerationAppealStatus): Boolean =
        status == ModerationAppealStatus.SUBMITTED ||
            status == ModerationAppealStatus.UNDER_REVIEW

    fun requiresCorrectiveFollowUp(status: ModerationAppealStatus): Boolean =
        status == ModerationAppealStatus.OVERTURNED ||
            status == ModerationAppealStatus.PARTIALLY_OVERTURNED

    /** No restaurar automáticamente desde el dominio. */
    fun autoRestoresResource(status: ModerationAppealStatus): Boolean = false

    fun validateReview(
        appeal: ModerationAppeal,
        decision: ModerationAppealStatus,
        decisionReason: String?,
        reviewerUserId: String,
        actionAppliedByUserId: String
    ): Result<Unit> {
        if (!isActive(appeal.status)) {
            return fail("APPEAL_NOT_REVIEWABLE")
        }
        if (reviewerUserId.isBlank()) return fail("REVIEWER_REQUIRED")
        if (reviewerUserId == actionAppliedByUserId) {
            return fail("CONFLICT_APPLIER_CANNOT_REVIEW")
        }
        if (decision !in setOf(
                ModerationAppealStatus.UPHELD,
                ModerationAppealStatus.OVERTURNED,
                ModerationAppealStatus.PARTIALLY_OVERTURNED,
                ModerationAppealStatus.REJECTED
            )
        ) {
            return fail("DECISION_INVALID")
        }
        val reason = decisionReason?.trim().orEmpty()
        if (reason.isEmpty() || reason.length > DECISION_REASON_MAX) {
            return fail("DECISION_REASON_REQUIRED")
        }
        return Result.success(Unit)
    }

    private fun fail(code: String): Result<Nothing> =
        Result.failure(IllegalArgumentException(code))
}

/**
 * Evidencia lógica. Sin binarios; paths/IDs para M05.
 */
data class ModerationEvidenceRef(
    val id: String,
    val caseId: String,
    val storagePathHint: String? = null,
    val contentType: String? = null,
    val createdByUserId: String,
    val createdAtEpochMs: Long
) {
    init {
        require(!storagePathHint.orEmpty().startsWith("http", ignoreCase = true)) {
            "no permanent urls"
        }
    }
}
