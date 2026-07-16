package com.comunidapp.app.data.repository

import com.comunidapp.app.domain.organization.CreateOrganizationInvitationCommand
import com.comunidapp.app.domain.organization.Organization
import com.comunidapp.app.domain.organization.OrganizationId
import com.comunidapp.app.domain.organization.OrganizationInvitation
import com.comunidapp.app.domain.organization.OrganizationInvitationRules
import com.comunidapp.app.domain.organization.OrganizationInvitationStatus
import com.comunidapp.app.domain.organization.OrganizationInvitationToken
import com.comunidapp.app.domain.organization.ValidatedOrganizationDraft
import com.comunidapp.app.domain.organization.authorization.OrganizationAuthorizationContext
import com.comunidapp.app.domain.organization.authorization.OrganizationMembership
import com.comunidapp.app.domain.organization.authorization.OrganizationMembershipStatus
import com.comunidapp.app.domain.organization.authorization.OrganizationPermissionCode
import com.comunidapp.app.domain.organization.authorization.OrganizationRoleCode
import com.comunidapp.app.domain.organization.authorization.OrganizationRolePermissionMatrix
import com.comunidapp.app.domain.organization.OrganizationStatus
import com.comunidapp.app.domain.organization.OrganizationVerificationStatus
import com.comunidapp.app.domain.user.AccountStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

interface OrganizationRepository {
    suspend fun getById(id: OrganizationId): Organization?
    suspend fun createDraft(
        draft: ValidatedOrganizationDraft,
        createdByUserId: String
    ): Result<Organization>
}

interface OrganizationMembershipRepository {
    suspend fun getActiveMembership(
        organizationId: OrganizationId,
        userId: String
    ): OrganizationMembership?

    suspend fun listActiveByOrganization(
        organizationId: OrganizationId
    ): List<OrganizationMembership>

    suspend fun countActiveOwners(organizationId: OrganizationId): Int

    suspend fun addMembership(membership: OrganizationMembership): Result<Unit>
}

interface OrganizationInvitationRepository {
    suspend fun create(command: CreateOrganizationInvitationCommand): Result<OrganizationInvitation>
    suspend fun getById(id: String): OrganizationInvitation?
    suspend fun accept(
        invitationId: String,
        actorUserId: String,
        token: OrganizationInvitationToken?,
        nowEpochMs: Long
    ): Result<OrganizationInvitation>
    suspend fun revoke(
        invitationId: String,
        actorUserId: String,
        nowEpochMs: Long
    ): Result<OrganizationInvitation>
}

interface OrganizationPermissionRepository {
    suspend fun getAuthorizationContext(
        organizationId: OrganizationId,
        userId: String,
        accountStatus: AccountStatus
    ): OrganizationAuthorizationContext

    suspend fun hasPermission(
        organizationId: OrganizationId,
        userId: String,
        accountStatus: AccountStatus,
        permission: OrganizationPermissionCode
    ): Boolean
}

class MockOrganizationRepository : OrganizationRepository {
    private val orgs = MutableStateFlow<Map<String, Organization>>(emptyMap())

    fun resetForTests() {
        orgs.value = emptyMap()
    }

    override suspend fun getById(id: OrganizationId): Organization? = orgs.value[id.value]

    override suspend fun createDraft(
        draft: ValidatedOrganizationDraft,
        createdByUserId: String
    ): Result<Organization> {
        if (createdByUserId.isBlank()) {
            return Result.failure(IllegalArgumentException("creator required"))
        }
        val id = OrganizationId(UUID.randomUUID().toString())
        val org = Organization(
            id = id,
            legalName = draft.legalName,
            publicName = draft.publicName,
            slug = draft.slug,
            type = draft.type,
            typeDescription = draft.typeDescription,
            description = draft.description,
            status = OrganizationStatus.DRAFT,
            verificationStatus = OrganizationVerificationStatus.NOT_REQUESTED,
            countryCode = draft.countryCode,
            createdByUserId = createdByUserId,
            createdAtEpochMs = System.currentTimeMillis()
        )
        orgs.update { it + (id.value to org) }
        return Result.success(org)
    }
}

class MockOrganizationMembershipRepository : OrganizationMembershipRepository {
    private val memberships =
        MutableStateFlow<Map<String, OrganizationMembership>>(emptyMap())

    fun resetForTests() {
        memberships.value = emptyMap()
    }

    override suspend fun getActiveMembership(
        organizationId: OrganizationId,
        userId: String
    ): OrganizationMembership? =
        memberships.value.values.find {
            it.organizationId == organizationId &&
                it.userId == userId &&
                it.status == OrganizationMembershipStatus.ACTIVE
        }

    override suspend fun listActiveByOrganization(
        organizationId: OrganizationId
    ): List<OrganizationMembership> =
        memberships.value.values.filter {
            it.organizationId == organizationId &&
                it.status == OrganizationMembershipStatus.ACTIVE
        }

    override suspend fun countActiveOwners(organizationId: OrganizationId): Int =
        listActiveByOrganization(organizationId).count { it.role == OrganizationRoleCode.OWNER }

    override suspend fun addMembership(membership: OrganizationMembership): Result<Unit> {
        memberships.update { it + (membership.id to membership) }
        return Result.success(Unit)
    }
}

class MockOrganizationInvitationRepository(
    private val membershipRepository: OrganizationMembershipRepository =
        MockOrganizationMembershipRepository()
) : OrganizationInvitationRepository {

    private val invites = MutableStateFlow<Map<String, OrganizationInvitation>>(emptyMap())

    fun resetForTests() {
        invites.value = emptyMap()
    }

    override suspend fun create(
        command: CreateOrganizationInvitationCommand
    ): Result<OrganizationInvitation> {
        if (!OrganizationInvitationRules.canInviteRole(command.invitedRole)) {
            return Result.failure(IllegalArgumentException("ROLE_NOT_INVITABLE"))
        }
        val id = UUID.randomUUID().toString()
        val invitation = OrganizationInvitation(
            id = id,
            organizationId = command.organizationId,
            invitedRole = command.invitedRole,
            status = OrganizationInvitationStatus.PENDING,
            invitedByUserId = command.invitedByUserId,
            targetUserId = command.targetUserId,
            targetEmailHint = command.targetEmailHint,
            expiresAtEpochMs = command.expiresAtEpochMs,
            token = command.token
        )
        invites.update { it + (id to invitation) }
        return Result.success(invitation)
    }

    override suspend fun getById(id: String): OrganizationInvitation? = invites.value[id]

    override suspend fun accept(
        invitationId: String,
        actorUserId: String,
        token: OrganizationInvitationToken?,
        nowEpochMs: Long
    ): Result<OrganizationInvitation> {
        val current = invites.value[invitationId]
            ?: return Result.failure(IllegalStateException("NOT_FOUND"))
        OrganizationInvitationRules.canAccept(current, actorUserId, token, nowEpochMs)
            .getOrElse { return Result.failure(it) }
        val accepted = OrganizationInvitationRules.markAccepted(current, nowEpochMs)
        invites.update { it + (invitationId to accepted) }
        membershipRepository.addMembership(
            OrganizationMembership(
                id = UUID.randomUUID().toString(),
                organizationId = accepted.organizationId,
                userId = actorUserId,
                role = accepted.invitedRole,
                status = OrganizationMembershipStatus.ACTIVE,
                invitedByUserId = accepted.invitedByUserId,
                joinedAtEpochMs = nowEpochMs
            )
        )
        return Result.success(accepted)
    }

    override suspend fun revoke(
        invitationId: String,
        actorUserId: String,
        nowEpochMs: Long
    ): Result<OrganizationInvitation> {
        val current = invites.value[invitationId]
            ?: return Result.failure(IllegalStateException("NOT_FOUND"))
        if (current.status != OrganizationInvitationStatus.PENDING) {
            return Result.failure(IllegalStateException("NOT_PENDING"))
        }
        val revoked = OrganizationInvitationRules.markRevoked(current, nowEpochMs)
        invites.update { it + (invitationId to revoked) }
        return Result.success(revoked)
    }
}

class MockOrganizationPermissionRepository(
    private val organizationRepository: OrganizationRepository = MockOrganizationRepository(),
    private val membershipRepository: OrganizationMembershipRepository =
        MockOrganizationMembershipRepository()
) : OrganizationPermissionRepository {

    override suspend fun getAuthorizationContext(
        organizationId: OrganizationId,
        userId: String,
        accountStatus: AccountStatus
    ): OrganizationAuthorizationContext {
        val org = organizationRepository.getById(organizationId)
        val membership = membershipRepository.getActiveMembership(organizationId, userId)
        return OrganizationAuthorizationContext(
            userId = userId,
            organizationId = organizationId,
            accountStatus = accountStatus,
            organizationStatus = org?.status ?: OrganizationStatus.DRAFT,
            membership = membership,
            permissions = membership?.let {
                OrganizationRolePermissionMatrix.permissionsFor(it.role)
            }.orEmpty()
        )
    }

    override suspend fun hasPermission(
        organizationId: OrganizationId,
        userId: String,
        accountStatus: AccountStatus,
        permission: OrganizationPermissionCode
    ): Boolean {
        val ctx = getAuthorizationContext(organizationId, userId, accountStatus)
        return com.comunidapp.app.domain.organization.authorization.OrganizationAuthorizationService
            .hasPermission(ctx, permission)
    }
}
