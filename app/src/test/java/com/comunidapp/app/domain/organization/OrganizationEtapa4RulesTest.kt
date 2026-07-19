package com.comunidapp.app.domain.organization

import com.comunidapp.app.data.repository.MockOrganizationInvitationRepository
import com.comunidapp.app.data.repository.MockOrganizationMembershipRepository
import com.comunidapp.app.data.repository.MockOrganizationRepository
import com.comunidapp.app.domain.organization.authorization.OrganizationAuthorizationContext
import com.comunidapp.app.domain.organization.authorization.OrganizationAuthorizationService
import com.comunidapp.app.domain.organization.authorization.OrganizationMembership
import com.comunidapp.app.domain.organization.authorization.OrganizationMembershipStatus
import com.comunidapp.app.domain.organization.authorization.OrganizationPermissionCode
import com.comunidapp.app.domain.organization.authorization.OrganizationRoleCode
import com.comunidapp.app.domain.organization.authorization.OrganizationRolePermissionMatrix
import com.comunidapp.app.domain.user.AccountStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OrganizationEtapa4RulesTest {

    private lateinit var memberships: MockOrganizationMembershipRepository
    private lateinit var orgs: MockOrganizationRepository
    private lateinit var invites: MockOrganizationInvitationRepository

    private var orgId: OrganizationId? = null
    private val ownerId = "owner-1"
    private val token = OrganizationInvitationToken.fromSecureRandom(
        "abcdefghijklmnop-secure-token-etapa4"
    )

    @Before
    fun setUp() = runTest {
        memberships = MockOrganizationMembershipRepository()
        memberships.resetForTests()
        orgs = MockOrganizationRepository(memberships)
        orgs.resetForTests()
        orgs.actingUserId = ownerId
        invites = MockOrganizationInvitationRepository(memberships)
        invites.resetForTests()
        val org = orgs.createOrganization(
            ValidatedOrganizationDraft(
                legalName = "Refugio Test",
                publicName = "Refugio Test",
                type = OrganizationType.SHELTER,
                typeDescription = null,
                slug = OrganizationSlugValidators.validate("refugio-test-etapa4").getOrThrow(),
                description = null,
                countryCode = "AR",
                province = "BA",
                city = "CABA"
            )
        ).getOrThrow()
        orgId = org.id
    }

    private fun requireOrgId(): OrganizationId = orgId!!

    @Test
    fun last_owner_protected_on_remove() = runTest {
        val id = requireOrgId()
        val ownerMembership = memberships.getActiveMembership(id, ownerId)!!
        val actorCtx = OrganizationAuthorizationContext(
            userId = ownerId,
            organizationId = id,
            accountStatus = AccountStatus.ACTIVE,
            organizationStatus = OrganizationStatus.DRAFT,
            membership = ownerMembership,
            permissions = OrganizationRolePermissionMatrix.permissionsFor(OrganizationRoleCode.OWNER)
        )
        val decision = OrganizationAuthorizationService.canRemoveMember(
            actor = actorCtx,
            target = ownerMembership,
            ownerCount = 1
        )
        assertFalse(decision.isAllowed)
    }

    @Test
    fun admin_cannot_assign_owner() {
        val id = requireOrgId()
        val adminMembership = OrganizationMembership(
            id = "admin-1",
            organizationId = id,
            userId = "admin-1",
            role = OrganizationRoleCode.ADMIN,
            status = OrganizationMembershipStatus.ACTIVE
        )
        val actorCtx = OrganizationAuthorizationContext(
            userId = "admin-1",
            organizationId = id,
            accountStatus = AccountStatus.ACTIVE,
            organizationStatus = OrganizationStatus.ACTIVE,
            membership = adminMembership,
            permissions = OrganizationRolePermissionMatrix.permissionsFor(OrganizationRoleCode.ADMIN)
        )
        val decision = OrganizationAuthorizationService.canAssignRole(
            actor = actorCtx,
            newRole = OrganizationRoleCode.OWNER,
            targetCurrentRole = OrganizationRoleCode.MEMBER,
            ownerCount = 1
        )
        assertFalse(decision.isAllowed)
    }

    @Test
    fun branch_contact_private_by_default() = runTest {
        val branch = orgs.createBranch(
            CreateOrganizationBranchCommand(
                organizationId = requireOrgId(),
                name = "Sede Central",
                phone = "+541100000000",
                phonePublic = false
            )
        ).getOrThrow()
        assertFalse(branch.phonePublic)
        assertEquals("+541100000000", branch.phone)
    }

    @Test
    fun context_clears_to_personal() {
        OrganizationContextProvider.selectPersonal()
        val state = OrganizationContextProvider.state.value
        assertEquals(OrganizationContextMode.PERSONAL, state.mode)
        assertNull(state.organizationId)
        OrganizationContextProvider.clear()
        assertEquals(OrganizationContextMode.PERSONAL, OrganizationContextProvider.state.value.mode)
    }

    @Test
    fun decline_invitation_does_not_create_membership() = runTest {
        val id = requireOrgId()
        val created = invites.create(
            CreateOrganizationInvitationCommand(
                organizationId = id,
                invitedRole = OrganizationRoleCode.VIEWER,
                invitedByUserId = ownerId,
                targetUserId = "guest-1",
                expiresAtEpochMs = System.currentTimeMillis() + 86_400_000L,
                token = token
            )
        ).getOrThrow()
        invites.declineByToken(token).getOrThrow()
        assertNull(memberships.getActiveMembership(id, "guest-1"))
        assertEquals(OrganizationInvitationStatus.DECLINED, invites.getById(created.id)!!.status)
    }

    @Test
    fun viewer_cannot_invite_by_permission_matrix() {
        val id = requireOrgId()
        val viewerCtx = OrganizationAuthorizationContext(
            userId = "viewer-1",
            organizationId = id,
            accountStatus = AccountStatus.ACTIVE,
            organizationStatus = OrganizationStatus.ACTIVE,
            membership = OrganizationMembership(
                id = "v1",
                organizationId = id,
                userId = "viewer-1",
                role = OrganizationRoleCode.VIEWER,
                status = OrganizationMembershipStatus.ACTIVE
            ),
            permissions = OrganizationRolePermissionMatrix.permissionsFor(OrganizationRoleCode.VIEWER)
        )
        assertFalse(
            OrganizationAuthorizationService.hasPermission(
                viewerCtx,
                OrganizationPermissionCode.ORGANIZATION_INVITE_MEMBERS
            )
        )
    }
}
