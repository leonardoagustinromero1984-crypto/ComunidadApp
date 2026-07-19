package com.comunidapp.app.domain.verification

import com.comunidapp.app.domain.organization.OrganizationVerificationStatus

enum class OrganizationVerificationDecision {
    APPROVE,
    REJECT,
    REQUEST_MORE_INFORMATION,
    REVOKE,
    MARK_EXPIRED
}

enum class OrganizationVerificationReviewStatus {
    PENDING_REVIEW,
    MORE_INFO_REQUESTED,
    APPROVED,
    REJECTED,
    REVOKED,
    EXPIRED
}

data class OrganizationVerificationDocumentRef(
    val id: String,
    val organizationId: String,
    val logicalName: String,
    /** Path lógico futuro M05; nunca URL permanente. */
    val storagePathHint: String? = null
) {
    init {
        require(!storagePathHint.orEmpty().startsWith("http", ignoreCase = true)) {
            "no permanent urls"
        }
    }
}

data class OrganizationVerificationReview(
    val id: String,
    val organizationId: String,
    val requestedByUserId: String,
    val assignedToUserId: String? = null,
    val status: OrganizationVerificationReviewStatus =
        OrganizationVerificationReviewStatus.PENDING_REVIEW,
    val decision: OrganizationVerificationDecision? = null,
    val reviewNote: String? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val decidedAtEpochMs: Long? = null,
    val decidedByUserId: String? = null
)

object VerificationValidators {

    const val NOTE_MAX = 2000

    /**
     * Solo PENDING organizacional puede APPROVE/REJECT.
     * REQUEST_MORE_INFORMATION no marca VERIFIED.
     */
    fun validateDecision(
        currentOrgVerification: OrganizationVerificationStatus,
        decision: OrganizationVerificationDecision,
        reviewNote: String?,
        actorUserId: String,
        actorIsOrgMember: Boolean
    ): Result<OrganizationVerificationStatus> {
        if (actorUserId.isBlank()) {
            return Result.failure(IllegalArgumentException("ACTOR_REQUIRED"))
        }
        if (actorIsOrgMember) {
            return Result.failure(IllegalArgumentException("CONFLICT_ORG_MEMBER"))
        }
        val note = reviewNote?.trim()?.ifBlank { null }
        if (note != null && note.length > NOTE_MAX) {
            return Result.failure(IllegalArgumentException("NOTE_TOO_LONG"))
        }

        return when (decision) {
            OrganizationVerificationDecision.APPROVE -> {
                if (currentOrgVerification != OrganizationVerificationStatus.PENDING) {
                    return Result.failure(IllegalArgumentException("NOT_PENDING"))
                }
                Result.success(OrganizationVerificationStatus.VERIFIED)
            }
            OrganizationVerificationDecision.REJECT -> {
                if (currentOrgVerification != OrganizationVerificationStatus.PENDING) {
                    return Result.failure(IllegalArgumentException("NOT_PENDING"))
                }
                Result.success(OrganizationVerificationStatus.REJECTED)
            }
            OrganizationVerificationDecision.REQUEST_MORE_INFORMATION -> {
                if (currentOrgVerification != OrganizationVerificationStatus.PENDING) {
                    return Result.failure(IllegalArgumentException("NOT_PENDING"))
                }
                // No cambia a VERIFIED; permanece PENDING a nivel org.
                Result.success(OrganizationVerificationStatus.PENDING)
            }
            OrganizationVerificationDecision.REVOKE -> {
                if (currentOrgVerification != OrganizationVerificationStatus.VERIFIED) {
                    return Result.failure(IllegalArgumentException("NOT_VERIFIED"))
                }
                Result.success(OrganizationVerificationStatus.EXPIRED)
            }
            OrganizationVerificationDecision.MARK_EXPIRED -> {
                if (currentOrgVerification != OrganizationVerificationStatus.VERIFIED &&
                    currentOrgVerification != OrganizationVerificationStatus.PENDING
                ) {
                    return Result.failure(IllegalArgumentException("CANNOT_EXPIRE"))
                }
                Result.success(OrganizationVerificationStatus.EXPIRED)
            }
        }
    }

    fun requestMoreInfoMarksVerified(): Boolean = false

    fun orgCanSelfReview(): Boolean = false
}
