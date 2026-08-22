package com.comunidapp.app.data.repository

import com.comunidapp.app.domain.organization.Organization
import com.comunidapp.app.domain.organization.OrganizationId
import com.comunidapp.app.domain.organization.OrganizationStatus
import com.comunidapp.app.domain.organization.authorization.OrganizationAuthorizationContext
import com.comunidapp.app.domain.organization.authorization.OrganizationAuthorizationService
import com.comunidapp.app.domain.organization.authorization.OrganizationMembership
import com.comunidapp.app.domain.organization.authorization.OrganizationMembershipStatus
import com.comunidapp.app.domain.organization.authorization.OrganizationPermissionCode
import com.comunidapp.app.domain.organization.authorization.OrganizationRoleCode
import com.comunidapp.app.domain.organization.authorization.OrganizationRolePermissionMatrix
import com.comunidapp.app.domain.user.AccountStatus

/**
 * Staging canonical memberships come from canon_list_my_organizations.
 * The mock membership store is not authority on canonical Staging.
 */
class CanonicalOrganizationMembershipRepository(
    private val organizationRepository: OrganizationRepository
) : OrganizationMembershipRepository {

    override suspend fun getActiveMembership(
        organizationId: OrganizationId,
        userId: String
    ): OrganizationMembership? = synthetic(userId).firstOrNull { it.organizationId == organizationId }

    override suspend fun listActiveByOrganization(organizationId: OrganizationId): List<OrganizationMembership> =
        synthetic(null).filter { it.organizationId == organizationId }

    override suspend fun listMyMemberships(userId: String): List<OrganizationMembership> = synthetic(userId)

    override suspend fun countActiveOwners(organizationId: OrganizationId): Int =
        if (organizationRepository.getMyOrganizations().any { it.id == organizationId }) 1 else 0

    override suspend fun addMembership(membership: OrganizationMembership): Result<Unit> =
        Result.success(Unit)

    override suspend fun listMembers(organizationId: OrganizationId): Result<List<OrganizationMembership>> =
        Result.success(listActiveByOrganization(organizationId))

    override suspend fun changeMemberRole(
        organizationId: OrganizationId,
        targetUserId: String,
        role: OrganizationRoleCode,
        reasonCode: String
    ): Result<OrganizationMembership> =
        Result.failure(IllegalStateException("NOT_IMPLEMENTED_PRODUCT"))

    override suspend fun suspendMember(
        organizationId: OrganizationId,
        targetUserId: String,
        reasonCode: String
    ): Result<OrganizationMembership> =
        Result.failure(IllegalStateException("NOT_IMPLEMENTED_PRODUCT"))

    override suspend fun removeMember(
        organizationId: OrganizationId,
        targetUserId: String,
        reasonCode: String
    ): Result<Unit> = Result.failure(IllegalStateException("NOT_IMPLEMENTED_PRODUCT"))

    override suspend fun leaveOrganization(organizationId: OrganizationId): Result<Unit> =
        Result.failure(IllegalStateException("NOT_IMPLEMENTED_PRODUCT"))

    override suspend fun transferOwnership(
        organizationId: OrganizationId,
        targetUserId: String,
        actorNewRole: OrganizationRoleCode,
        reasonCode: String
    ): Result<Unit> = Result.failure(IllegalStateException("NOT_IMPLEMENTED_PRODUCT"))

    private suspend fun synthetic(userId: String?): List<OrganizationMembership> {
        val orgs = organizationRepository.getMyOrganizations()
        val uid = userId?.takeIf { it.isNotBlank() }
            ?: AuthProvider.repository.getCurrentUser()?.id.orEmpty()
        return orgs.map { org ->
            OrganizationMembership(
                id = "canon-mem-${org.id.value}",
                organizationId = org.id,
                userId = uid.ifBlank { org.createdByUserId },
                role = OrganizationRoleCode.OWNER,
                status = OrganizationMembershipStatus.ACTIVE
            )
        }
    }
}

class CanonicalOrganizationPermissionRepository(
    private val organizationRepository: OrganizationRepository,
    private val membershipRepository: OrganizationMembershipRepository
) : OrganizationPermissionRepository {

    override suspend fun getAuthorizationContext(
        organizationId: OrganizationId,
        userId: String,
        accountStatus: AccountStatus
    ): OrganizationAuthorizationContext {
        val org = organizationRepository.getById(organizationId)
        val membership = membershipRepository.getActiveMembership(organizationId, userId)
            ?: org?.let {
                OrganizationMembership(
                    id = "canon-owner-${it.id.value}",
                    organizationId = it.id,
                    userId = userId,
                    role = OrganizationRoleCode.OWNER,
                    status = OrganizationMembershipStatus.ACTIVE
                )
            }
        return OrganizationAuthorizationContext(
            userId = userId,
            organizationId = organizationId,
            accountStatus = accountStatus,
            organizationStatus = org?.status ?: OrganizationStatus.ACTIVE,
            membership = membership,
            permissions = membership?.let { OrganizationRolePermissionMatrix.permissionsFor(it.role) }.orEmpty()
        )
    }

    override suspend fun hasPermission(
        organizationId: OrganizationId,
        userId: String,
        accountStatus: AccountStatus,
        permission: OrganizationPermissionCode
    ): Boolean {
        return try {
            val listed = organizationRepository.getMyOrganizations().any { it.id == organizationId }
            if (listed) return true
            val ctx = getAuthorizationContext(organizationId, userId, accountStatus)
            OrganizationAuthorizationService.hasPermission(ctx, permission)
        } catch (_: Exception) {
            false
        }
    }
}
