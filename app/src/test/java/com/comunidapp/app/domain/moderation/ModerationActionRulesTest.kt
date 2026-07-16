package com.comunidapp.app.domain.moderation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModerationActionRulesTest {

    private val now = 1_700_000_000_000L
    private val target = ModerationTargetRef(ModerationTargetType.USER_PROFILE, "u2")

    @Test
    fun temporary_requires_future_expiry() {
        val missing = ModerationActionRules.validateNew(
            "case-1", target, ModerationActionType.ACCOUNT_SUSPENDED,
            "safety", null, "mod-1", now, expiresAtEpochMs = null
        )
        assertTrue(missing.isFailure)

        val past = ModerationActionRules.validateNew(
            "case-1", target, ModerationActionType.ACCOUNT_SUSPENDED,
            "safety", null, "mod-1", now, expiresAtEpochMs = now
        )
        assertTrue(past.isFailure)

        val ok = ModerationActionRules.validateNew(
            "case-1", target, ModerationActionType.ACCOUNT_SUSPENDED,
            "safety", null, "mod-1", now, expiresAtEpochMs = now + 86_400_000L
        )
        assertTrue(ok.isSuccess)
    }

    @Test
    fun permanent_rejects_expiry() {
        val bad = ModerationActionRules.validateNew(
            "case-1", target, ModerationActionType.ACCOUNT_BANNED,
            "policy_violation", null, "mod-1", now, expiresAtEpochMs = now + 1
        )
        assertTrue(bad.isFailure)

        val ok = ModerationActionRules.validateNew(
            "case-1", target, ModerationActionType.ACCOUNT_BANNED,
            "policy_violation", null, "mod-1", now, expiresAtEpochMs = null
        )
        assertTrue(ok.isSuccess)
    }

    @Test
    fun no_action_does_not_modify_and_no_expiry() {
        assertFalse(ModerationActionRules.modifiesResource(ModerationActionType.NO_ACTION))
        val withExpiry = ModerationActionRules.validateNew(
            "case-1", target, ModerationActionType.NO_ACTION,
            "ops_review", null, "mod-1", now, expiresAtEpochMs = now + 1
        )
        assertTrue(withExpiry.isFailure)
        assertTrue(
            ModerationActionRules.validateNew(
                "case-1", target, ModerationActionType.NO_ACTION,
                "ops_review", null, "mod-1", now, expiresAtEpochMs = null
            ).isSuccess
        )
    }

    @Test
    fun maps_to_m02_and_m03_without_duplicating_status_columns() {
        assertTrue(ModerationActionRules.mapsToAccountStatus(ModerationActionType.ACCOUNT_BANNED))
        assertTrue(ModerationActionRules.mapsToOrganizationStatus(ModerationActionType.ORGANIZATION_SUSPENDED))
        assertFalse(ModerationActionRules.mapsToAccountStatus(ModerationActionType.WARNING))
        assertTrue(
            ModerationActionRules.requiresVerificationPermission(
                ModerationActionType.VERIFICATION_REVOKED
            )
        )
    }

    @Test
    fun invalid_reason_rejected() {
        val result = ModerationActionRules.validateNew(
            "case-1", target, ModerationActionType.WARNING,
            "unknown_reason", null, "mod-1", now, null
        )
        assertTrue(result.isFailure)
    }
}
