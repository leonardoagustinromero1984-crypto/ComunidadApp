package com.comunidapp.app.domain.moderation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModerationReportRulesTest {

    private val now = 1_700_000_000_000L

    @Test
    fun new_report_starts_open() {
        val report = ModerationReportRules.validateNewReport(
            reporterId = "u1",
            target = ModerationTargetRef(ModerationTargetType.POST, "post-1"),
            reasonCode = "spam",
            description = "promo",
            nowEpochMs = now
        ).getOrThrow()
        assertEquals(ModerationReportStatus.OPEN, report.status)
        assertEquals("spam", report.reasonCode)
    }

    @Test
    fun blank_reporter_rejected() {
        val result = ModerationReportRules.validateNewReport(
            "",
            ModerationTargetRef(ModerationTargetType.COMMENT, "c1"),
            "spam",
            null,
            now
        )
        assertTrue(result.isFailure)
        assertEquals("REPORTER_REQUIRED", result.exceptionOrNull()?.message)
    }

    @Test
    fun invalid_reason_rejected() {
        val result = ModerationReportRules.validateNewReport(
            "u1",
            ModerationTargetRef(ModerationTargetType.POST, "p1"),
            "not_a_reason",
            null,
            now
        )
        assertTrue(result.isFailure)
        assertEquals("REASON_INVALID", result.exceptionOrNull()?.message)
    }

    @Test
    fun other_requires_description() {
        val bad = ModerationReportRules.validateTarget(
            ModerationTargetRef(ModerationTargetType.OTHER, "x", otherDescription = null)
        )
        assertTrue(bad.isFailure)
        val ok = ModerationReportRules.validateTarget(
            ModerationTargetRef(ModerationTargetType.OTHER, "x", otherDescription = "misc")
        )
        assertTrue(ok.isSuccess)
    }

    @Test
    fun description_too_long_rejected() {
        val result = ModerationReportRules.validateNewReport(
            "u1",
            ModerationTargetRef(ModerationTargetType.POST, "p1"),
            "spam",
            "x".repeat(ModerationReportRules.DESCRIPTION_MAX + 1),
            now
        )
        assertTrue(result.isFailure)
        assertEquals("DESCRIPTION_TOO_LONG", result.exceptionOrNull()?.message)
    }

    @Test
    fun public_summary_omits_reporter() {
        val report = ModerationReport(
            id = "r1",
            reporterId = "secret-reporter",
            target = ModerationTargetRef(ModerationTargetType.USER_PROFILE, "u2"),
            reasonCode = "harassment",
            description = "private note",
            createdAtEpochMs = now,
            updatedAtEpochMs = now
        )
        val summary = ModerationReportRules.toPublicSummary(report)
        assertEquals("r1", summary.id)
        assertFalse(summary.toString().contains("secret-reporter"))
    }

    @Test
    fun duplicate_self_rejected() {
        val report = ModerationReport(
            id = "r1",
            reporterId = "u1",
            target = ModerationTargetRef(ModerationTargetType.POST, "p1"),
            reasonCode = "spam",
            createdAtEpochMs = now,
            updatedAtEpochMs = now
        )
        assertTrue(ModerationReportRules.canMarkDuplicate(report, "r1").isFailure)
        assertTrue(ModerationReportRules.canMarkDuplicate(report, "r2").isSuccess)
    }
}
