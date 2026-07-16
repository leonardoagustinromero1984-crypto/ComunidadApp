package com.comunidapp.app.domain.organization

import com.comunidapp.app.domain.organization.authorization.OrganizationRoleCode

enum class OrganizationInvitationStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    REVOKED,
    EXPIRED
}

/**
 * Handle opaco del token. Nunca loguear [rawToken].
 * Persistencia futura debe guardar solo hash.
 */
@JvmInline
value class OrganizationInvitationToken private constructor(val rawToken: String) {
    init {
        require(rawToken.length >= 16) { "token too short" }
    }

    override fun toString(): String = "OrganizationInvitationToken(***)"

    fun fingerprint(): String =
        rawToken.take(4) + "…" + rawToken.takeLast(4)

    companion object {
        fun fromSecureRandom(raw: String): OrganizationInvitationToken =
            OrganizationInvitationToken(raw)
    }
}

data class OrganizationInvitation(
    val id: String,
    val organizationId: OrganizationId,
    val invitedRole: OrganizationRoleCode,
    val status: OrganizationInvitationStatus = OrganizationInvitationStatus.PENDING,
    val invitedByUserId: String,
    val targetUserId: String? = null,
    val targetEmailHint: String? = null,
    val expiresAtEpochMs: Long,
    val acceptedAtEpochMs: Long? = null,
    val revokedAtEpochMs: Long? = null,
    /** Solo en memoria/mock; no persistir plano. */
    val token: OrganizationInvitationToken? = null
)

data class CreateOrganizationInvitationCommand(
    val organizationId: OrganizationId,
    val invitedRole: OrganizationRoleCode,
    val invitedByUserId: String,
    val targetUserId: String? = null,
    val targetEmailHint: String? = null,
    val expiresAtEpochMs: Long,
    val token: OrganizationInvitationToken
)

object OrganizationInvitationRules {

    /** Roles que se pueden invitar (nunca OWNER por invitación directa). */
    fun canInviteRole(role: OrganizationRoleCode): Boolean =
        role != OrganizationRoleCode.OWNER

    fun isExpired(invitation: OrganizationInvitation, nowEpochMs: Long): Boolean =
        invitation.status == OrganizationInvitationStatus.EXPIRED ||
            (invitation.status == OrganizationInvitationStatus.PENDING &&
                nowEpochMs >= invitation.expiresAtEpochMs)

    fun canAccept(
        invitation: OrganizationInvitation,
        actorUserId: String,
        token: OrganizationInvitationToken?,
        nowEpochMs: Long
    ): Result<Unit> {
        if (invitation.status == OrganizationInvitationStatus.REVOKED) {
            return Result.failure(IllegalStateException("REVOKED"))
        }
        if (invitation.status == OrganizationInvitationStatus.ACCEPTED) {
            return Result.failure(IllegalStateException("ALREADY_ACCEPTED"))
        }
        if (invitation.status == OrganizationInvitationStatus.DECLINED) {
            return Result.failure(IllegalStateException("DECLINED"))
        }
        if (isExpired(invitation, nowEpochMs)) {
            return Result.failure(IllegalStateException("EXPIRED"))
        }
        if (invitation.status != OrganizationInvitationStatus.PENDING) {
            return Result.failure(IllegalStateException("NOT_PENDING"))
        }
        invitation.targetUserId?.let { target ->
            if (target != actorUserId) {
                return Result.failure(IllegalStateException("TARGET_MISMATCH"))
            }
        }
        val expected = invitation.token
        if (expected != null && (token == null || token.rawToken != expected.rawToken)) {
            return Result.failure(IllegalStateException("TOKEN_INVALID"))
        }
        if (!canInviteRole(invitation.invitedRole)) {
            return Result.failure(IllegalStateException("ROLE_NOT_INVITABLE"))
        }
        return Result.success(Unit)
    }

    fun markAccepted(invitation: OrganizationInvitation, nowEpochMs: Long): OrganizationInvitation =
        invitation.copy(
            status = OrganizationInvitationStatus.ACCEPTED,
            acceptedAtEpochMs = nowEpochMs,
            token = null
        )

    fun markRevoked(invitation: OrganizationInvitation, nowEpochMs: Long): OrganizationInvitation =
        invitation.copy(
            status = OrganizationInvitationStatus.REVOKED,
            revokedAtEpochMs = nowEpochMs,
            token = null
        )
}
