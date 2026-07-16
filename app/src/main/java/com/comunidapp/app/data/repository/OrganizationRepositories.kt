package com.comunidapp.app.data.repository

import com.comunidapp.app.domain.organization.CreateOrganizationInvitationCommand
import com.comunidapp.app.domain.organization.Organization
import com.comunidapp.app.domain.organization.OrganizationContactVisibility
import com.comunidapp.app.domain.organization.OrganizationId
import com.comunidapp.app.domain.organization.OrganizationInvitation
import com.comunidapp.app.domain.organization.OrganizationInvitationRules
import com.comunidapp.app.domain.organization.OrganizationInvitationStatus
import com.comunidapp.app.domain.organization.OrganizationInvitationToken
import com.comunidapp.app.domain.organization.OrganizationResourceType
import com.comunidapp.app.domain.organization.OrganizationStatus
import com.comunidapp.app.domain.organization.OrganizationType
import com.comunidapp.app.domain.organization.OrganizationValidators
import com.comunidapp.app.domain.organization.OrganizationVerificationStatus
import com.comunidapp.app.domain.organization.PublicOrganization
import com.comunidapp.app.domain.organization.UpdateOrganizationCommand
import com.comunidapp.app.domain.organization.ValidatedOrganizationDraft
import com.comunidapp.app.domain.organization.authorization.OrganizationAuthorizationContext
import com.comunidapp.app.domain.organization.authorization.OrganizationAuthorizationService
import com.comunidapp.app.domain.organization.authorization.OrganizationMembership
import com.comunidapp.app.domain.organization.authorization.OrganizationMembershipStatus
import com.comunidapp.app.domain.organization.authorization.OrganizationPermissionCode
import com.comunidapp.app.domain.organization.authorization.OrganizationRoleCode
import com.comunidapp.app.domain.organization.authorization.OrganizationRolePermissionMatrix
import com.comunidapp.app.domain.user.AccountStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

interface OrganizationRepository {
    suspend fun getById(id: OrganizationId): Organization?

    /**
     * Crea borrador. Mock usa [createdByUserId]; remoto ignora el parámetro y usa auth.uid().
     */
    suspend fun createDraft(
        draft: ValidatedOrganizationDraft,
        createdByUserId: String
    ): Result<Organization>

    /** Preferido en remoto (RPC create_organization). */
    suspend fun createOrganization(draft: ValidatedOrganizationDraft): Result<Organization>

    suspend fun getMyOrganizations(): List<Organization>

    suspend fun updateMyOrganization(command: UpdateOrganizationCommand): Result<Organization>

    suspend fun getPublicBySlug(slug: String): Result<PublicOrganization?>

    suspend fun searchPublic(
        query: String,
        type: OrganizationType? = null,
        city: String? = null,
        limit: Int = 20
    ): Result<List<PublicOrganization>>

    suspend fun requestVerification(organizationId: OrganizationId): Result<Organization>

    suspend fun linkResource(
        organizationId: OrganizationId,
        resourceType: OrganizationResourceType,
        resourceId: String
    ): Result<Unit>

    suspend fun unlinkResource(
        organizationId: OrganizationId,
        resourceType: OrganizationResourceType,
        resourceId: String
    ): Result<Unit>
}

interface OrganizationMembershipRepository {
    suspend fun getActiveMembership(
        organizationId: OrganizationId,
        userId: String
    ): OrganizationMembership?

    suspend fun listActiveByOrganization(
        organizationId: OrganizationId
    ): List<OrganizationMembership>

    suspend fun listMyMemberships(userId: String): List<OrganizationMembership>

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

    /** Deny-by-default ante error. */
    suspend fun hasPermission(
        organizationId: OrganizationId,
        userId: String,
        accountStatus: AccountStatus,
        permission: OrganizationPermissionCode
    ): Boolean
}

class MockOrganizationRepository(
    private val membershipRepository: OrganizationMembershipRepository =
        MockOrganizationMembershipRepository()
) : OrganizationRepository {
    private val orgs = MutableStateFlow<Map<String, Organization>>(emptyMap())

    /** Usuario “sesión” para createOrganization en mock / tests. */
    var actingUserId: String? = null

    fun resetForTests() {
        orgs.value = emptyMap()
        actingUserId = null
    }

    fun seedForTests(organization: Organization) {
        orgs.update { it + (organization.id.value to organization) }
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
        val now = System.currentTimeMillis()
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
            province = draft.province,
            city = draft.city,
            createdByUserId = createdByUserId,
            createdAtEpochMs = now
        )
        orgs.update { it + (id.value to org) }
        membershipRepository.addMembership(
            OrganizationMembership(
                id = UUID.randomUUID().toString(),
                organizationId = id,
                userId = createdByUserId,
                role = OrganizationRoleCode.OWNER,
                status = OrganizationMembershipStatus.ACTIVE,
                joinedAtEpochMs = now
            )
        )
        return Result.success(org)
    }

    override suspend fun createOrganization(draft: ValidatedOrganizationDraft): Result<Organization> {
        val uid = resolveActingUserId()
            ?: return Result.failure(IllegalStateException("NOT_AUTHENTICATED"))
        return createDraft(draft, uid)
    }

    override suspend fun getMyOrganizations(): List<Organization> {
        val uid = resolveActingUserId() ?: return emptyList()
        val memberships = membershipRepository.listMyMemberships(uid)
        return memberships.mapNotNull { getById(it.organizationId) }
            .sortedBy { it.publicName.lowercase() }
    }

    override suspend fun updateMyOrganization(
        command: UpdateOrganizationCommand
    ): Result<Organization> {
        val current = orgs.value[command.organizationId.value]
            ?: return Result.failure(IllegalStateException("ORGANIZATION_NOT_FOUND"))
        val updated = current.copy(
            publicName = command.displayName?.trim()?.takeIf { it.isNotEmpty() } ?: current.publicName,
            legalName = when {
                command.legalName == null -> current.legalName
                else -> command.legalName.trim().ifBlank { current.publicName }
            },
            description = when {
                command.description == null -> current.description
                else -> command.description.trim().ifBlank { null }
            },
            countryCode = when {
                command.countryCode == null -> current.countryCode
                else -> command.countryCode.trim().uppercase().ifBlank { null }
            },
            province = when {
                command.province == null -> current.province
                else -> command.province.trim().ifBlank { null }
            },
            city = when {
                command.city == null -> current.city
                else -> command.city.trim().ifBlank { null }
            },
            institutionalEmail = when {
                command.contactEmail == null -> current.institutionalEmail
                else -> command.contactEmail.trim().ifBlank { null }
            },
            institutionalPhone = when {
                command.contactPhone == null -> current.institutionalPhone
                else -> command.contactPhone.trim().ifBlank { null }
            },
            contactVisibility = OrganizationContactVisibility(
                showEmail = command.contactEmailPublic ?: current.contactVisibility.showEmail,
                showPhone = command.contactPhonePublic ?: current.contactVisibility.showPhone
            ),
            logoPath = when {
                command.logoPath == null -> current.logoPath
                else -> command.logoPath.trim().ifBlank { null }
            },
            coverPath = when {
                command.coverPath == null -> current.coverPath
                else -> command.coverPath.trim().ifBlank { null }
            },
            updatedAtEpochMs = System.currentTimeMillis()
        )
        orgs.update { it + (updated.id.value to updated) }
        return Result.success(updated)
    }

    override suspend fun getPublicBySlug(slug: String): Result<PublicOrganization?> {
        val normalized = slug.trim().lowercase()
        if (normalized.length < 3) return Result.success(null)
        val org = orgs.value.values.find {
            it.slug.value == normalized &&
                (it.status == OrganizationStatus.ACTIVE || it.status == OrganizationStatus.RESTRICTED)
        }
        return Result.success(org?.let { OrganizationValidators.toPublic(it) })
    }

    override suspend fun searchPublic(
        query: String,
        type: OrganizationType?,
        city: String?,
        limit: Int
    ): Result<List<PublicOrganization>> {
        val q = query.trim().lowercase()
        if (q.length < 2) return Result.success(emptyList())
        val lim = limit.coerceIn(1, 50)
        val cityFilter = city?.trim()?.ifBlank { null }
        val results = orgs.value.values
            .filter {
                (it.status == OrganizationStatus.ACTIVE || it.status == OrganizationStatus.RESTRICTED) &&
                    (type == null || it.type == type) &&
                    (cityFilter == null || it.city.equals(cityFilter, ignoreCase = true)) &&
                    (
                        it.publicName.lowercase().contains(q) ||
                            it.slug.value.contains(q) ||
                            (it.description?.lowercase()?.contains(q) == true)
                        )
            }
            .sortedBy { it.publicName.lowercase() }
            .take(lim)
            .map { OrganizationValidators.toPublic(it) }
        return Result.success(results)
    }

    override suspend fun requestVerification(
        organizationId: OrganizationId
    ): Result<Organization> {
        val current = orgs.value[organizationId.value]
            ?: return Result.failure(IllegalStateException("ORGANIZATION_NOT_FOUND"))
        val allowed = current.verificationStatus == OrganizationVerificationStatus.NOT_REQUESTED ||
            current.verificationStatus == OrganizationVerificationStatus.REJECTED ||
            current.verificationStatus == OrganizationVerificationStatus.EXPIRED
        if (!allowed) {
            return Result.failure(IllegalStateException("VERIFICATION_NOT_REQUESTABLE"))
        }
        // Solo transición a PENDING; nunca VERIFIED desde cliente/mock de autoservicio.
        val updated = current.copy(
            verificationStatus = OrganizationVerificationStatus.PENDING,
            updatedAtEpochMs = System.currentTimeMillis()
        )
        orgs.update { it + (updated.id.value to updated) }
        return Result.success(updated)
    }

    override suspend fun linkResource(
        organizationId: OrganizationId,
        resourceType: OrganizationResourceType,
        resourceId: String
    ): Result<Unit> {
        if (getById(organizationId) == null) {
            return Result.failure(IllegalStateException("ORGANIZATION_NOT_FOUND"))
        }
        if (resourceId.isBlank()) {
            return Result.failure(IllegalArgumentException("RESOURCE_ID_REQUIRED"))
        }
        return Result.success(Unit)
    }

    override suspend fun unlinkResource(
        organizationId: OrganizationId,
        resourceType: OrganizationResourceType,
        resourceId: String
    ): Result<Unit> {
        if (getById(organizationId) == null) {
            return Result.failure(IllegalStateException("ORGANIZATION_NOT_FOUND"))
        }
        return Result.success(Unit)
    }

    private suspend fun resolveActingUserId(): String? =
        actingUserId?.takeIf { it.isNotBlank() }
            ?: AuthProvider.repository.getCurrentUser()?.id?.takeIf { it.isNotBlank() }
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

    override suspend fun listMyMemberships(userId: String): List<OrganizationMembership> =
        memberships.value.values.filter {
            it.userId == userId && it.status == OrganizationMembershipStatus.ACTIVE
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
        return try {
            val ctx = getAuthorizationContext(organizationId, userId, accountStatus)
            OrganizationAuthorizationService.hasPermission(ctx, permission)
        } catch (_: Exception) {
            false
        }
    }
}
