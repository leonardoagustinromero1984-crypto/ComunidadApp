package com.comunidapp.app.domain.moderation.authorization

import org.junit.Assert.assertEquals
import org.junit.Test

class AdministrativeConflictRulesTest {

    private fun ctx(vararg perms: AdministrativePermissionCode) =
        AdministrativeAuthContext("staff-1", perms.toSet())

    @Test
    fun cannot_review_own_organization() {
        assertEquals(
            AdministrativeDecision.DENIED_CONFLICT_OF_INTEREST,
            AdministrativeConflictRules.canReviewOwnOrganization("staff-1", true)
        )
        assertEquals(
            AdministrativeDecision.ALLOWED,
            AdministrativeConflictRules.canReviewOwnOrganization("staff-1", false)
        )
    }

    @Test
    fun applier_cannot_review_appeal() {
        val withPerm = ctx(AdministrativePermissionCode.MODERATION_REVIEW_APPEALS)
        assertEquals(
            AdministrativeDecision.DENIED_CONFLICT_OF_INTEREST,
            AdministrativeConflictRules.canReviewAppeal(withPerm, actionAppliedByUserId = "staff-1")
        )
        assertEquals(
            AdministrativeDecision.ALLOWED,
            AdministrativeConflictRules.canReviewAppeal(withPerm, actionAppliedByUserId = "other")
        )
    }

    @Test
    fun assign_requires_assignee_permission() {
        val manager = ctx(AdministrativePermissionCode.MODERATION_MANAGE_CASES)
        assertEquals(
            AdministrativeDecision.DENIED_MISSING_PERMISSION,
            AdministrativeConflictRules.canAssign(
                manager,
                AdministrativePermissionCode.MODERATION_MANAGE_CASES,
                assigneeUserId = "newbie",
                assigneeHasPermission = false,
                selfAssign = false
            )
        )
        assertEquals(
            AdministrativeDecision.ALLOWED,
            AdministrativeConflictRules.canAssign(
                manager,
                AdministrativePermissionCode.MODERATION_MANAGE_CASES,
                assigneeUserId = "staff-2",
                assigneeHasPermission = true,
                selfAssign = false
            )
        )
    }

    @Test
    fun self_assign_allowed_with_manage_permission() {
        val manager = ctx(AdministrativePermissionCode.SUPPORT_MANAGE)
        assertEquals(
            AdministrativeDecision.ALLOWED,
            AdministrativeConflictRules.canAssign(
                manager,
                AdministrativePermissionCode.SUPPORT_MANAGE,
                assigneeUserId = "staff-1",
                assigneeHasPermission = true,
                selfAssign = true
            )
        )
    }

    @Test
    fun blank_actor_unknown() {
        assertEquals(
            AdministrativeDecision.DENIED_UNKNOWN,
            AdministrativeConflictRules.canReviewOwnOrganization("", false)
        )
    }
}
