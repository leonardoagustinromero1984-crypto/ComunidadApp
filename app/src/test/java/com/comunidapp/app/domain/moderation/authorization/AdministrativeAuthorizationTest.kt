package com.comunidapp.app.domain.moderation.authorization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdministrativeAuthorizationTest {

    private fun ctx(
        perms: Set<AdministrativePermissionCode>,
        lookupFailed: Boolean = false,
        actor: String = "staff-1"
    ) = AdministrativeAuthContext(
        actorUserId = actor,
        permissions = perms,
        permissionLookupFailed = lookupFailed
    )

    @Test
    fun deny_by_default_without_permission() {
        val decision = ModerationAuthorization.decide(
            ctx(emptySet()),
            AdministrativePermissionCode.MODERATION_VIEW
        )
        assertEquals(AdministrativeDecision.DENIED_MISSING_PERMISSION, decision)
    }

    @Test
    fun allow_when_permission_present() {
        assertTrue(
            ModerationAuthorization.isAllowed(
                ctx(setOf(AdministrativePermissionCode.MODERATION_VIEW)),
                AdministrativePermissionCode.MODERATION_VIEW
            )
        )
    }

    @Test
    fun unknown_or_error_denies() {
        assertEquals(
            AdministrativeDecision.DENIED_UNKNOWN,
            ModerationAuthorization.decide(
                ctx(setOf(AdministrativePermissionCode.MODERATION_VIEW), lookupFailed = true),
                AdministrativePermissionCode.MODERATION_VIEW
            )
        )
        assertEquals(
            AdministrativeDecision.DENIED_UNKNOWN,
            ModerationAuthorization.decide(
                ctx(setOf(AdministrativePermissionCode.MODERATION_VIEW), actor = ""),
                AdministrativePermissionCode.MODERATION_VIEW
            )
        )
    }

    @Test
    fun view_does_not_imply_mutation_or_cross_domain() {
        assertFalse(ModerationAuthorization.viewImpliesMutation())
        assertFalse(ModerationAuthorization.moderationImpliesSupport())
        assertFalse(ModerationAuthorization.supportImpliesVerification())
        assertFalse(ModerationAuthorization.grantsFromAccountTypeOrModulesOrOrgRoles())
    }

    @Test
    fun sensitive_requires_explicit_permission() {
        val viewOnly = ctx(setOf(AdministrativePermissionCode.MODERATION_VIEW))
        assertFalse(ModerationAuthorization.canViewSensitive(viewOnly))
        assertEquals(
            AdministrativeDecision.DENIED_SENSITIVE_ACCESS,
            AdministrativeConflictRules.canAccessReporterId(viewOnly)
        )
        val sensitive = ctx(
            setOf(
                AdministrativePermissionCode.MODERATION_VIEW,
                AdministrativePermissionCode.MODERATION_VIEW_SENSITIVE
            )
        )
        assertTrue(ModerationAuthorization.canViewSensitive(sensitive))
        assertEquals(
            AdministrativeDecision.ALLOWED,
            AdministrativeConflictRules.canAccessReporterId(sensitive)
        )
    }

    @Test
    fun permission_codes_match_m02_strings() {
        assertEquals("moderation.view", AdministrativePermissionCode.MODERATION_VIEW.code)
        assertEquals(
            "organizations.review_verification",
            AdministrativePermissionCode.ORGANIZATIONS_REVIEW_VERIFICATION.code
        )
        assertEquals("support.manage", AdministrativePermissionCode.SUPPORT_MANAGE.code)
    }
}
