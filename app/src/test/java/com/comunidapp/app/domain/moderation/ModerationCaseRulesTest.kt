package com.comunidapp.app.domain.moderation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModerationCaseRulesTest {

    private val now = 1_700_000_000_000L

    @Test
    fun create_requires_title_and_creator() {
        assertTrue(
            ModerationCaseRules.validateCreate("ab", "u1", now).isFailure
        )
        assertTrue(
            ModerationCaseRules.validateCreate("Caso válido", "", now).isFailure
        )
        val case = ModerationCaseRules.validateCreate("Caso válido", "u1", now).getOrThrow()
        assertEquals(ModerationCaseStatus.OPEN, case.status)
    }

    @Test
    fun report_cannot_belong_to_two_active_cases() {
        val report = ModerationReport(
            id = "r1",
            reporterId = "u1",
            target = ModerationTargetRef(ModerationTargetType.POST, "p1"),
            reasonCode = "spam",
            status = ModerationReportStatus.OPEN,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
            caseId = "case-a"
        )
        assertTrue(ModerationCaseRules.canAttachReport(report, "case-b").isFailure)
        assertEquals(
            "REPORT_ALREADY_IN_CASE",
            ModerationCaseRules.canAttachReport(report, "case-b").exceptionOrNull()?.message
        )
        assertTrue(ModerationCaseRules.canAttachReport(report, "case-a").isSuccess)
    }

    @Test
    fun closed_report_can_reattach() {
        val report = ModerationReport(
            id = "r1",
            reporterId = "u1",
            target = ModerationTargetRef(ModerationTargetType.POST, "p1"),
            reasonCode = "spam",
            status = ModerationReportStatus.CLOSED,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
            caseId = "case-a"
        )
        assertTrue(ModerationCaseRules.canAttachReport(report, "case-b").isSuccess)
    }

    @Test
    fun close_requires_reason_unless_resolved() {
        val open = ModerationCase(
            id = "c1",
            title = "Caso",
            status = ModerationCaseStatus.OPEN,
            createdByUserId = "u1",
            createdAtEpochMs = now,
            updatedAtEpochMs = now
        )
        assertTrue(ModerationCaseRules.canClose(open, null).isFailure)
        assertTrue(ModerationCaseRules.canClose(open, "ops_done").isSuccess)

        val resolved = open.copy(status = ModerationCaseStatus.RESOLVED)
        assertTrue(ModerationCaseRules.canClose(resolved, null).isSuccess)
    }

    @Test
    fun note_limits_enforced() {
        assertTrue(ModerationCaseRules.validateNote("").isFailure)
        assertTrue(ModerationCaseRules.validateNote("ok").isSuccess)
        assertTrue(
            ModerationCaseRules.validateNote("x".repeat(ModerationCaseRules.NOTE_MAX + 1)).isFailure
        )
    }
}
