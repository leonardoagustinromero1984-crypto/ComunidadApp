package com.comunidapp.app.data.repository

import com.comunidapp.app.domain.organization.CreateOrganizationBranchCommand
import com.comunidapp.app.domain.organization.CreateOrganizationInvitationCommand
import com.comunidapp.app.domain.organization.Organization
import com.comunidapp.app.domain.organization.OrganizationBranch
import com.comunidapp.app.domain.organization.OrganizationBranchStatus
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
import com.comunidapp.app.domain.organization.UpdateOrganizationBranchCommand
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

    suspend fun listBranches(
        organizationId: OrganizationId,
        includePrivate: Boolean = true
    ): Result<List<OrganizationBranch>>

    suspend fun createBranch(command: CreateOrganizationBranchCommand): Result<OrganizationBranch>

    suspend fun updateBranch(command: UpdateOrganizationBranchCommand): Result<OrganizationBranch>

    suspend fun setBranchStatus(
        branchId: String,
        status: OrganizationBranchStatus
    ): Result<OrganizationBranch>

    suspend fun closeOrganization(
        organizationId: OrganizationId,
        reasonCode: String = "org_closed"
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

    suspend fun listMyMemberships(userId: String): List<OrganizationMembership>

    suspend fun countActiveOwners(organizationId: OrganizationId): Int

    suspend fun addMembership(membership: OrganizationMembership): Result<Unit>

    suspend fun listMembers(organizationId: OrganizationId): Result<List<OrganizationMembership>>

    suspend fun changeMemberRole(
        organizationId: OrganizationId,
        targetUserId: String,
        role: OrganizationRoleCode,
        reasonCode: String = "member_role_changed"
    ): Result<OrganizationMembership>

    suspend fun suspendMember(
        organizationId: OrganizationId,
        targetUserId: String,
        reasonCode: String = "member_suspended"
    ): Result<OrganizationMembership>

    suspend fun removeMember(
        organizationId: OrganizationId,
        targetUserId: String,
        reasonCode: String = "member_removed"
    ): Result<Unit>

    suspend fun leaveOrganization(organizationId: OrganizationId): Result<Unit>

    suspend fun transferOwnership(
        organizationId: OrganizationId,
        targetUserId: String,
        actorNewRole: OrganizationRoleCode = OrganizationRoleCode.ADMIN,
        reasonCode: String = "ownership_transferred"
    ): Result<Unit>
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

    suspend fun listByOrganization(
        organizationId: OrganizationId
    ): Result<List<OrganizationInvitation>>

    suspend fun listMyPending(): Result<List<OrganizationInvitation>>

    suspend fun acceptByToken(token: OrganizationInvitationToken): Result<OrganizationMembership>

    suspend fun declineByToken(token: OrganizationInvitationToken): Result<Unit>
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
    private val branches = MutableStateFlow<Map<String, OrganizationBranch>>(emptyMap())

    /** Usuario “sesión” para createOrganization en mock / tests. */
    var actingUserId: String? = null

    fun resetForTests() {
        orgs.value = emptyMap()
        branches.value = emptyMap()
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

    override suspend fun listBranches(
        organizationId: OrganizationId,
        includePrivate: Boolean
    ): Result<List<OrganizationBranch>> {
        if (getById(organizationId) == null) {
            return Result.failure(IllegalStateException("ORGANIZATION_NOT_FOUND"))
        }
        val list = branches.value.values
            .filter { it.organizationId == organizationId && it.status != OrganizationBranchStatus.CLOSED }
            .sortedBy { it.name.lowercase() }
        return Result.success(list)
    }

    override suspend fun createBranch(
        command: CreateOrganizationBranchCommand
    ): Result<OrganizationBranch> {
        if (getById(command.organizationId) == null) {
            return Result.failure(IllegalStateException("ORGANIZATION_NOT_FOUND"))
        }
        val name = command.name.trim()
        if (name.length < 2) {
            return Result.failure(IllegalArgumentException("NAME_INVALID"))
        }
        val id = UUID.randomUUID().toString()
        val branch = OrganizationBranch(
            id = id,
            organizationId = command.organizationId,
            name = name,
            addressLine = command.addressLine?.trim()?.ifBlank { null },
            city = command.city?.trim()?.ifBlank { null },
            province = command.province?.trim()?.ifBlank { null },
            countryCode = command.countryCode?.trim()?.uppercase()?.ifBlank { null },
            postalCode = command.postalCode?.trim()?.ifBlank { null },
            phone = command.phone?.trim()?.ifBlank { null },
            phonePublic = command.phonePublic,
            openingHoursJson = command.openingHoursJson,
            status = OrganizationBranchStatus.ACTIVE
        )
        branches.update { it + (id to branch) }
        return Result.success(branch)
    }

    override suspend fun updateBranch(
        command: UpdateOrganizationBranchCommand
    ): Result<OrganizationBranch> {
        val current = branches.value[command.branchId]
            ?: return Result.failure(IllegalStateException("BRANCH_NOT_FOUND"))
        val updated = current.copy(
            name = command.name?.trim()?.ifBlank { null } ?: current.name,
            addressLine = command.addressLine?.trim()?.ifBlank { null } ?: current.addressLine,
            city = command.city?.trim()?.ifBlank { null } ?: current.city,
            province = command.province?.trim()?.ifBlank { null } ?: current.province,
            countryCode = command.countryCode?.trim()?.uppercase()?.ifBlank { null }
                ?: current.countryCode,
            postalCode = command.postalCode?.trim()?.ifBlank { null } ?: current.postalCode,
            phone = command.phone?.trim()?.ifBlank { null } ?: current.phone,
            phonePublic = command.phonePublic ?: current.phonePublic,
            openingHoursJson = command.openingHoursJson ?: current.openingHoursJson
        )
        branches.update { it + (updated.id to updated) }
        return Result.success(updated)
    }

    override suspend fun setBranchStatus(
        branchId: String,
        status: OrganizationBranchStatus
    ): Result<OrganizationBranch> {
        val current = branches.value[branchId]
            ?: return Result.failure(IllegalStateException("BRANCH_NOT_FOUND"))
        val updated = current.copy(status = status)
        branches.update { it + (branchId to updated) }
        return Result.success(updated)
    }

    override suspend fun closeOrganization(
        organizationId: OrganizationId,
        reasonCode: String
    ): Result<Organization> {
        val current = orgs.value[organizationId.value]
            ?: return Result.failure(IllegalStateException("ORGANIZATION_NOT_FOUND"))
        val updated = current.copy(
            status = OrganizationStatus.CLOSED,
            updatedAtEpochMs = System.currentTimeMillis()
        )
        orgs.update { it + (updated.id.value to updated) }
        return Result.success(updated)
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

    private fun updateMembership(membership: OrganizationMembership) {
        memberships.update { it + (membership.id to membership) }
    }

    override suspend fun listMembers(
        organizationId: OrganizationId
    ): Result<List<OrganizationMembership>> =
        Result.success(
            memberships.value.values.filter {
                it.organizationId == organizationId &&
                    it.status in setOf(
                        OrganizationMembershipStatus.ACTIVE,
                        OrganizationMembershipStatus.SUSPENDED,
                        OrganizationMembershipStatus.INVITED
                    )
            }
        )

    override suspend fun changeMemberRole(
        organizationId: OrganizationId,
        targetUserId: String,
        role: OrganizationRoleCode,
        reasonCode: String
    ): Result<OrganizationMembership> {
        val current = getActiveMembership(organizationId, targetUserId)
            ?: return Result.failure(IllegalStateException("MEMBER_NOT_FOUND"))
        if (role == OrganizationRoleCode.OWNER) {
            return Result.failure(IllegalStateException("USE_TRANSFER_OWNERSHIP"))
        }
        val updated = current.copy(role = role)
        updateMembership(updated)
        return Result.success(updated)
    }

    override suspend fun suspendMember(
        organizationId: OrganizationId,
        targetUserId: String,
        reasonCode: String
    ): Result<OrganizationMembership> {
        val current = getActiveMembership(organizationId, targetUserId)
            ?: return Result.failure(IllegalStateException("MEMBER_NOT_FOUND"))
        val updated = current.copy(status = OrganizationMembershipStatus.SUSPENDED)
        updateMembership(updated)
        return Result.success(updated)
    }

    override suspend fun removeMember(
        organizationId: OrganizationId,
        targetUserId: String,
        reasonCode: String
    ): Result<Unit> {
        val current = memberships.value.values.find {
            it.organizationId == organizationId &&
                it.userId == targetUserId &&
                it.status in setOf(
                    OrganizationMembershipStatus.ACTIVE,
                    OrganizationMembershipStatus.SUSPENDED
                )
        } ?: return Result.failure(IllegalStateException("MEMBER_NOT_FOUND"))
        updateMembership(current.copy(status = OrganizationMembershipStatus.REMOVED))
        return Result.success(Unit)
    }

    override suspend fun leaveOrganization(organizationId: OrganizationId): Result<Unit> {
        val actor = AuthProvider.repository.getCurrentUser()?.id
            ?: return Result.failure(IllegalStateException("NOT_AUTHENTICATED"))
        val current = getActiveMembership(organizationId, actor)
            ?: return Result.failure(IllegalStateException("NOT_A_MEMBER"))
        if (current.role == OrganizationRoleCode.OWNER && countActiveOwners(organizationId) <= 1) {
            return Result.failure(IllegalStateException("LAST_OWNER_PROTECTED"))
        }
        updateMembership(current.copy(status = OrganizationMembershipStatus.LEFT))
        return Result.success(Unit)
    }

    override suspend fun transferOwnership(
        organizationId: OrganizationId,
        targetUserId: String,
        actorNewRole: OrganizationRoleCode,
        reasonCode: String
    ): Result<Unit> {
        val actor = AuthProvider.repository.getCurrentUser()?.id
            ?: return Result.failure(IllegalStateException("NOT_AUTHENTICATED"))
        val actorMembership = getActiveMembership(organizationId, actor)
            ?: return Result.failure(IllegalStateException("FORBIDDEN"))
        if (actorMembership.role != OrganizationRoleCode.OWNER) {
            return Result.failure(IllegalStateException("FORBIDDEN"))
        }
        val target = getActiveMembership(organizationId, targetUserId)
            ?: return Result.failure(IllegalStateException("TARGET_NOT_MEMBER"))
        updateMembership(target.copy(role = OrganizationRoleCode.OWNER))
        updateMembership(actorMembership.copy(role = actorNewRole))
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

    override suspend fun listByOrganization(
        organizationId: OrganizationId
    ): Result<List<OrganizationInvitation>> =
        Result.success(
            invites.value.values.filter { it.organizationId == organizationId }
                .sortedByDescending { it.expiresAtEpochMs }
        )

    override suspend fun listMyPending(): Result<List<OrganizationInvitation>> {
        val actor = AuthProvider.repository.getCurrentUser()?.id
            ?: return Result.success(emptyList())
        val now = System.currentTimeMillis()
        return Result.success(
            invites.value.values.filter {
                it.status == OrganizationInvitationStatus.PENDING &&
                    !OrganizationInvitationRules.isExpired(it, now) &&
                    (it.targetUserId == null || it.targetUserId == actor)
            }
        )
    }

    override suspend fun acceptByToken(
        token: OrganizationInvitationToken
    ): Result<OrganizationMembership> {
        val actor = AuthProvider.repository.getCurrentUser()?.id
            ?: return Result.failure(IllegalStateException("NOT_AUTHENTICATED"))
        val invitation = invites.value.values.find {
            it.token?.rawToken == token.rawToken && it.status == OrganizationInvitationStatus.PENDING
        } ?: return Result.failure(IllegalStateException("INVITATION_INVALID"))
        val now = System.currentTimeMillis()
        accept(invitation.id, actor, token, now).getOrElse { return Result.failure(it) }
        val membership = membershipRepository.getActiveMembership(invitation.organizationId, actor)
            ?: return Result.failure(IllegalStateException("MEMBERSHIP_NOT_CREATED"))
        return Result.success(membership)
    }

    override suspend fun declineByToken(token: OrganizationInvitationToken): Result<Unit> {
        val invitation = invites.value.values.find {
            it.token?.rawToken == token.rawToken && it.status == OrganizationInvitationStatus.PENDING
        } ?: return Result.failure(IllegalStateException("INVITATION_INVALID"))
        val declined = OrganizationInvitationRules.markDeclined(invitation, System.currentTimeMillis())
        invites.update { it + (invitation.id to declined) }
        return Result.success(Unit)
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
