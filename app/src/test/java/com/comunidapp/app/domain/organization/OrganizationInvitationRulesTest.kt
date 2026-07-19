package com.comunidapp.app.domain.organization

import com.comunidapp.app.data.repository.MockOrganizationInvitationRepository
import com.comunidapp.app.data.repository.MockOrganizationMembershipRepository
import com.comunidapp.app.domain.organization.authorization.OrganizationRoleCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OrganizationInvitationRulesTest {

    private lateinit var memberships: MockOrganizationMembershipRepository
    private lateinit var invites: MockOrganizationInvitationRepository

    private val orgId = OrganizationId("org-inv")
    private val token = OrganizationInvitationToken.fromSecureRandom("abcdefghijklmnop-token-secure")
    private val now = 1_000_000L
    private val expires = now + 86_400_000L

    @Before
    fun setUp() {
        memberships = MockOrganizationMembershipRepository()
        memberships.resetForTests()
        invites = MockOrganizationInvitationRepository(memberships)
        invites.resetForTests()
    }

    @Test
    fun cannot_invite_owner_role() {
        assertFalse(OrganizationInvitationRules.canInviteRole(OrganizationRoleCode.OWNER))
        assertTrue(OrganizationInvitationRules.canInviteRole(OrganizationRoleCode.MEMBER))
    }

    @Test
    fun expires_when_past_deadline() = runTest {
        val created = invites.create(
            CreateOrganizationInvitationCommand(
                organizationId = orgId,
                invitedRole = OrganizationRoleCode.MEMBER,
                invitedByUserId = "owner",
                expiresAtEpochMs = now - 1,
                token = token
            )
        ).getOrThrow()
        assertTrue(OrganizationInvitationRules.isExpired(created, now))
        assertTrue(
            OrganizationInvitationRules.canAccept(created, "u2", token, now).isFailure
        )
    }

    @Test
    fun accept_is_single_use_and_creates_membership() = runTest {
        val created = invites.create(
            CreateOrganizationInvitationCommand(
                organizationId = orgId,
                invitedRole = OrganizationRoleCode.MANAGER,
                invitedByUserId = "owner",
                targetUserId = "invitee",
                expiresAtEpochMs = expires,
                token = token
            )
        ).getOrThrow()
        assertEquals(OrganizationInvitationStatus.PENDING, created.status)
        assertNull(memberships.getActiveMembership(orgId, "invitee"))

        val accepted = invites.accept(created.id, "invitee", token, now).getOrThrow()
        assertEquals(OrganizationInvitationStatus.ACCEPTED, accepted.status)
        assertNull(accepted.token)
        assertEquals(
            OrganizationRoleCode.MANAGER,
            memberships.getActiveMembership(orgId, "invitee")!!.role
        )

        val second = invites.accept(created.id, "invitee", token, now)
        assertTrue(second.isFailure)
    }

    @Test
    fun revoke_blocks_accept() = runTest {
        val created = invites.create(
            CreateOrganizationInvitationCommand(
                organizationId = orgId,
                invitedRole = OrganizationRoleCode.VIEWER,
                invitedByUserId = "owner",
                expiresAtEpochMs = expires,
                token = token
            )
        ).getOrThrow()
        invites.revoke(created.id, "owner", now).getOrThrow()
        assertTrue(invites.accept(created.id, "anyone", token, now).isFailure)
        assertNull(memberships.getActiveMembership(orgId, "anyone"))
    }

    @Test
    fun invalid_token_rejected() = runTest {
        val created = invites.create(
            CreateOrganizationInvitationCommand(
                organizationId = orgId,
                invitedRole = OrganizationRoleCode.MEMBER,
                invitedByUserId = "owner",
                expiresAtEpochMs = expires,
                token = token
            )
        ).getOrThrow()
        val wrong = OrganizationInvitationToken.fromSecureRandom("zzzzzzzzzzzzzzzz-wrong-token")
        assertTrue(invites.accept(created.id, "u9", wrong, now).isFailure)
    }

    @Test
    fun token_toString_does_not_leak_raw() {
        val t = OrganizationInvitationToken.fromSecureRandom("supersecrettoken12")
        assertFalse(t.toString().contains("supersecrettoken12"))
    }
}
