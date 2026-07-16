package com.comunidapp.app.domain.moderation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModerationAppealRulesTest {

    private val now = 1_700_000_000_000L

    private fun action(appliedBy: String = "mod-1"): ModerationAction =
        ModerationAction(
            id = "act-1",
            caseId = "case-1",
            target = ModerationTargetRef(ModerationTargetType.USER_PROFILE, "u2"),
            actionType = ModerationActionType.WARNING,
            reasonCode = "policy_violation",
            appliedByUserId = appliedBy,
            appliedAtEpochMs = now
        )

    @Test
    fun submit_within_window() {
        val appeal = ModerationAppealRules.validateSubmit(
            action(),
            submittedByUserId = "u2",
            statement = "Esto es una apelación válida.",
            nowEpochMs = now + 1_000L,
            existingActiveAppeal = false
        ).getOrThrow()
        assertEquals(ModerationAppealStatus.SUBMITTED, appeal.status)
    }

    @Test
    fun expired_window_rejected() {
        val result = ModerationAppealRules.validateSubmit(
            action(),
            "u2",
            "Esto es una apelación válida.",
            nowEpochMs = now + ModerationAppealPolicy.DEFAULT_WINDOW_MS + 1,
            existingActiveAppeal = false
        )
        assertTrue(result.isFailure)
        assertEquals("APPEAL_WINDOW_EXPIRED", result.exceptionOrNull()?.message)
    }

    @Test
    fun duplicate_active_appeal_rejected() {
        val result = ModerationAppealRules.validateSubmit(
            action(),
            "u2",
            "Esto es una apelación válida.",
            now,
            existingActiveAppeal = true
        )
        assertTrue(result.isFailure)
        assertEquals("APPEAL_ALREADY_ACTIVE", result.exceptionOrNull()?.message)
    }

    @Test
    fun applier_cannot_review() {
        val appeal = ModerationAppeal(
            id = "ap1",
            actionId = "act-1",
            submittedByUserId = "u2",
            statement = "Esto es una apelación válida.",
            createdAtEpochMs = now
        )
        val conflict = ModerationAppealRules.validateReview(
            appeal,
            ModerationAppealStatus.UPHELD,
            "motivo",
            reviewerUserId = "mod-1",
            actionAppliedByUserId = "mod-1"
        )
        assertTrue(conflict.isFailure)
        assertEquals("CONFLICT_APPLIER_CANNOT_REVIEW", conflict.exceptionOrNull()?.message)
    }

    @Test
    fun decision_requires_reason() {
        val appeal = ModerationAppeal(
            id = "ap1",
            actionId = "act-1",
            submittedByUserId = "u2",
            statement = "Esto es una apelación válida.",
            createdAtEpochMs = now
        )
        assertTrue(
            ModerationAppealRules.validateReview(
                appeal,
                ModerationAppealStatus.OVERTURNED,
                "  ",
                "mod-2",
                "mod-1"
            ).isFailure
        )
    }

    @Test
    fun overturned_needs_follow_up_but_no_auto_restore() {
        assertTrue(ModerationAppealRules.requiresCorrectiveFollowUp(ModerationAppealStatus.OVERTURNED))
        assertFalse(ModerationAppealRules.autoRestoresResource(ModerationAppealStatus.OVERTURNED))
    }

    @Test
    fun evidence_rejects_permanent_url() {
        var threw = false
        try {
            ModerationEvidenceRef(
                id = "e1",
                caseId = "c1",
                storagePathHint = "https://cdn.example/file",
                createdByUserId = "mod-1",
                createdAtEpochMs = now
            )
        } catch (_: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }
}
