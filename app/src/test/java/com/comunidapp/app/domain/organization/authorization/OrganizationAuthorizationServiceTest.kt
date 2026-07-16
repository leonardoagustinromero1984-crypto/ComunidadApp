package com.comunidapp.app.domain.organization.authorization

import com.comunidapp.app.domain.organization.OrganizationId
import com.comunidapp.app.domain.organization.OrganizationStatus
import com.comunidapp.app.domain.user.AccountStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrganizationAuthorizationServiceTest {

    private val orgId = OrganizationId("org-1")

    private fun ctx(
        role: OrganizationRoleCode?,
        accountStatus: AccountStatus = AccountStatus.ACTIVE,
        orgStatus: OrganizationStatus = OrganizationStatus.ACTIVE,
        userId: String = "u1",
        membershipStatus: OrganizationMembershipStatus = OrganizationMembershipStatus.ACTIVE
    ): OrganizationAuthorizationContext {
        val membership = role?.let {
            OrganizationMembership(
                id = "m1",
                organizationId = orgId,
                userId = userId,
                role = it,
                status = membershipStatus
            )
        }
        return OrganizationAuthorizationContext(
            userId = userId,
            organizationId = orgId,
            accountStatus = accountStatus,
            organizationStatus = orgStatus,
            membership = membership,
            permissions = membership?.let {
                OrganizationRolePermissionMatrix.permissionsFor(it.role)
            }.orEmpty()
        )
    }

    @Test
    fun deny_by_default_without_membership() {
        val empty = OrganizationAuthorizationContext.empty("u1", orgId)
        assertFalse(
            OrganizationAuthorizationService.hasPermission(
                empty,
                OrganizationPermissionCode.ORGANIZATION_VIEW
            )
        )
    }

    @Test
    fun owner_has_close_and_publish() {
        val owner = ctx(OrganizationRoleCode.OWNER)
        assertTrue(
            OrganizationAuthorizationService.hasPermission(
                owner,
                OrganizationPermissionCode.ORGANIZATION_CLOSE
            )
        )
        assertTrue(OrganizationAuthorizationService.canPublish(owner))
    }

    @Test
    fun admin_limited_no_close() {
        val admin = ctx(OrganizationRoleCode.ADMIN)
        assertTrue(
            OrganizationAuthorizationService.hasPermission(
                admin,
                OrganizationPermissionCode.ORGANIZATION_MANAGE_MEMBERS
            )
        )
        assertFalse(
            OrganizationAuthorizationService.hasPermission(
                admin,
                OrganizationPermissionCode.ORGANIZATION_CLOSE
            )
        )
    }

    @Test
    fun manager_without_roles_permission() {
        val manager = ctx(OrganizationRoleCode.MANAGER)
        assertTrue(
            OrganizationAuthorizationService.hasPermission(
                manager,
                OrganizationPermissionCode.ORGANIZATION_PUBLISH
            )
        )
        assertFalse(
            OrganizationAuthorizationService.hasPermission(
                manager,
                OrganizationPermissionCode.ORGANIZATION_MANAGE_ROLES
            )
        )
    }

    @Test
    fun member_without_member_admin() {
        val member = ctx(OrganizationRoleCode.MEMBER)
        assertTrue(
            OrganizationAuthorizationService.hasPermission(
                member,
                OrganizationPermissionCode.ORGANIZATION_VIEW_PRIVATE
            )
        )
        assertFalse(
            OrganizationAuthorizationService.hasPermission(
                member,
                OrganizationPermissionCode.ORGANIZATION_INVITE_MEMBERS
            )
        )
    }

    @Test
    fun viewer_read_only() {
        val viewer = ctx(OrganizationRoleCode.VIEWER)
        assertTrue(
            OrganizationAuthorizationService.hasPermission(
                viewer,
                OrganizationPermissionCode.ORGANIZATION_VIEW
            )
        )
        assertFalse(
            OrganizationAuthorizationService.hasPermission(
                viewer,
                OrganizationPermissionCode.ORGANIZATION_VIEW_PRIVATE
            )
        )
        assertFalse(
            OrganizationAuthorizationService.hasPermission(
                viewer,
                OrganizationPermissionCode.ORGANIZATION_UPDATE
            )
        )
    }

    @Test
    fun last_owner_protected_from_remove_and_demote() {
        val actor = ctx(OrganizationRoleCode.OWNER, userId = "owner1")
        val target = OrganizationMembership(
            id = "m-owner",
            organizationId = orgId,
            userId = "owner1",
            role = OrganizationRoleCode.OWNER
        )
        val remove = OrganizationAuthorizationService.canRemoveMember(actor, target, ownerCount = 1)
        assertFalse(remove.isAllowed)
        val demote = OrganizationAuthorizationService.canAssignRole(
            actor = actor,
            newRole = OrganizationRoleCode.ADMIN,
            targetCurrentRole = OrganizationRoleCode.OWNER,
            ownerCount = 1
        )
        assertFalse(demote.isAllowed)
    }

    @Test
    fun admin_cannot_remove_or_assign_owner() {
        val admin = ctx(OrganizationRoleCode.ADMIN)
        val ownerMembership = OrganizationMembership(
            id = "m2",
            organizationId = orgId,
            userId = "owner",
            role = OrganizationRoleCode.OWNER
        )
        assertFalse(
            OrganizationAuthorizationService.canRemoveMember(
                admin,
                ownerMembership,
                ownerCount = 2
            ).isAllowed
        )
        assertFalse(
            OrganizationAuthorizationService.canAssignRole(
                admin,
                OrganizationRoleCode.OWNER,
                OrganizationRoleCode.ADMIN,
                ownerCount = 1
            ).isAllowed
        )
    }

    @Test
    fun platform_suspended_denied() {
        val suspended = ctx(OrganizationRoleCode.OWNER, accountStatus = AccountStatus.SUSPENDED)
        assertFalse(
            OrganizationAuthorizationService.hasPermission(
                suspended,
                OrganizationPermissionCode.ORGANIZATION_UPDATE
            )
        )
    }

    @Test
    fun organization_suspended_cannot_publish() {
        val owner = ctx(
            OrganizationRoleCode.OWNER,
            orgStatus = OrganizationStatus.SUSPENDED
        )
        assertFalse(OrganizationAuthorizationService.canPublish(owner))
        assertFalse(
            OrganizationAuthorizationService.hasPermission(
                owner,
                OrganizationPermissionCode.ORGANIZATION_PUBLISH
            )
        )
    }

    @Test
    fun account_type_modules_and_platform_roles_do_not_grant() {
        assertFalse(
            OrganizationAuthorizationService.grantsFromAccountTypeOrModulesOrPlatformRoles()
        )
    }

    @Test
    fun inactive_membership_denied() {
        val invited = ctx(
            OrganizationRoleCode.MEMBER,
            membershipStatus = OrganizationMembershipStatus.INVITED
        )
        assertFalse(
            OrganizationAuthorizationService.hasPermission(
                invited,
                OrganizationPermissionCode.ORGANIZATION_VIEW
            )
        )
    }
}
