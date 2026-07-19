package com.comunidapp.app.domain.moderation

/**
 * Medidas tipadas. No duplican columnas de estado M02/M03:
 * la aplicación futura escribe vía RPCs de cuenta/org.
 */
enum class ModerationActionType {
    NO_ACTION,
    CONTENT_HIDDEN,
    CONTENT_REMOVED,
    WARNING,
    FEATURE_RESTRICTED,
    ACCOUNT_RESTRICTED,
    ACCOUNT_SUSPENDED,
    ACCOUNT_BANNED,
    ORGANIZATION_RESTRICTED,
    ORGANIZATION_SUSPENDED,
    VERIFICATION_REJECTED,
    VERIFICATION_REVOKED
}

data class ModerationAction(
    val id: String,
    val caseId: String,
    val target: ModerationTargetRef,
    val actionType: ModerationActionType,
    val reasonCode: String,
    val reasonDetail: String? = null,
    val appliedByUserId: String,
    val appliedAtEpochMs: Long,
    val expiresAtEpochMs: Long? = null,
    val reversedAtEpochMs: Long? = null,
    val reversedByUserId: String? = null
)

object ModerationActionRules {

    const val DETAIL_MAX = 500

    private val TEMPORARY = setOf(
        ModerationActionType.FEATURE_RESTRICTED,
        ModerationActionType.ACCOUNT_RESTRICTED,
        ModerationActionType.ACCOUNT_SUSPENDED,
        ModerationActionType.ORGANIZATION_RESTRICTED,
        ModerationActionType.ORGANIZATION_SUSPENDED,
        ModerationActionType.CONTENT_HIDDEN
    )

    private val PERMANENT = setOf(
        ModerationActionType.CONTENT_REMOVED,
        ModerationActionType.ACCOUNT_BANNED,
        ModerationActionType.VERIFICATION_REJECTED,
        ModerationActionType.VERIFICATION_REVOKED,
        ModerationActionType.WARNING,
        ModerationActionType.NO_ACTION
    )

    fun isTemporary(type: ModerationActionType): Boolean = type in TEMPORARY

    fun isPermanent(type: ModerationActionType): Boolean =
        type in PERMANENT || type == ModerationActionType.NO_ACTION

    fun mapsToAccountStatus(type: ModerationActionType): Boolean = when (type) {
        ModerationActionType.ACCOUNT_RESTRICTED,
        ModerationActionType.ACCOUNT_SUSPENDED,
        ModerationActionType.ACCOUNT_BANNED -> true
        else -> false
    }

    fun mapsToOrganizationStatus(type: ModerationActionType): Boolean = when (type) {
        ModerationActionType.ORGANIZATION_RESTRICTED,
        ModerationActionType.ORGANIZATION_SUSPENDED -> true
        else -> false
    }

    fun requiresVerificationPermission(type: ModerationActionType): Boolean =
        type == ModerationActionType.VERIFICATION_REJECTED ||
            type == ModerationActionType.VERIFICATION_REVOKED

    fun validateNew(
        caseId: String,
        target: ModerationTargetRef,
        actionType: ModerationActionType,
        reasonCode: String,
        reasonDetail: String?,
        appliedByUserId: String,
        nowEpochMs: Long,
        expiresAtEpochMs: Long?
    ): Result<ModerationAction> {
        if (caseId.isBlank()) return fail("CASE_REQUIRED")
        if (appliedByUserId.isBlank()) return fail("ACTOR_REQUIRED")
        ModerationReportRules.validateTarget(target).getOrElse { return Result.failure(it) }
        val reason = reasonCode.trim().lowercase()
        if (reason !in ModerationReasonCodes.ACTION) return fail("REASON_INVALID")
        val detail = reasonDetail?.trim()?.ifBlank { null }
        if (detail != null && detail.length > DETAIL_MAX) return fail("DETAIL_TOO_LONG")

        if (actionType == ModerationActionType.NO_ACTION) {
            if (expiresAtEpochMs != null) return fail("NO_ACTION_NO_EXPIRY")
        } else if (isTemporary(actionType)) {
            if (expiresAtEpochMs == null || expiresAtEpochMs <= nowEpochMs) {
                return fail("TEMPORARY_REQUIRES_EXPIRY")
            }
        } else if (isPermanent(actionType) && expiresAtEpochMs != null) {
            return fail("PERMANENT_NO_EXPIRY")
        }

        return Result.success(
            ModerationAction(
                id = "",
                caseId = caseId,
                target = target,
                actionType = actionType,
                reasonCode = reason,
                reasonDetail = detail,
                appliedByUserId = appliedByUserId,
                appliedAtEpochMs = nowEpochMs,
                expiresAtEpochMs = expiresAtEpochMs
            )
        )
    }

    fun modifiesResource(actionType: ModerationActionType): Boolean =
        actionType != ModerationActionType.NO_ACTION

    private fun fail(code: String): Result<Nothing> =
        Result.failure(IllegalArgumentException(code))
}
