package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.ReportStatus
import com.comunidapp.app.data.remote.supabase.ContentReportRow
import com.comunidapp.app.data.remote.supabase.parseContentReport
import com.comunidapp.app.domain.authorization.PermissionCode
import com.comunidapp.app.domain.authorization.PlatformRoleCode
import com.comunidapp.app.domain.authorization.RolePermissionMatrix
import com.comunidapp.app.domain.moderation.ModerationReportRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModerationLegacyAccessAdapterTest {

    @Test
    fun triage_accepts_actioned_status_name() {
        assertEquals("ACTIONED", ModerationLegacyTriageStatus.toRpcStatus("ACTIONED"))
        assertEquals("DISMISSED", ModerationLegacyTriageStatus.toRpcStatus("dismissed"))
        assertEquals("REVIEWED", ModerationLegacyTriageStatus.toRpcStatus("REVIEWED"))
        assertFalse(ModerationLegacyTriageStatus.actionedMeansRealMeasure())
        assertFalse(ModerationReportRules.legacyActionedMeansRealMeasure())
        assertEquals(ReportStatus.ACTIONED, ReportStatus.fromString("ACTIONED"))
    }

    @Test
    fun parseContentReport_nullReporterId_becomesEmpty() {
        val row = ContentReportRow(
            id = "r1",
            reporterId = null,
            targetType = "POST",
            targetId = "p1",
            reasonCode = "spam",
            status = ReportStatus.OPEN.name
        )
        val parsed = parseContentReport(row)
        assertEquals("", parsed.reporterId)
        assertEquals("spam", parsed.reason)
    }

    @Test
    fun parseContentReport_prefersReasonCodeOverReason() {
        val row = ContentReportRow(
            id = "r2",
            reporterId = "u1",
            targetType = "USER",
            targetId = "u2",
            reason = "legacy",
            reasonCode = "harassment",
            status = ReportStatus.OPEN.name
        )
        assertEquals("harassment", parseContentReport(row).reason)
        assertEquals("u1", parseContentReport(row).reporterId)
    }

    @Test
    fun roleMatrix_includes_m04_codes_for_staff_roles() {
        val mod = RolePermissionMatrix.permissionsFor(setOf(PlatformRoleCode.MODERATOR))
        assertTrue(PermissionCode.MODERATION_MANAGE_CASES in mod)
        assertTrue(PermissionCode.MODERATION_REVIEW_APPEALS in mod)
        assertFalse(PermissionCode.MODERATION_APPLY_ACTIONS in mod)

        val admin = RolePermissionMatrix.permissionsFor(setOf(PlatformRoleCode.ADMIN))
        assertTrue(PermissionCode.MODERATION_APPLY_ACTIONS in admin)
        assertTrue(PermissionCode.MODERATION_VIEW_SENSITIVE in admin)
        assertTrue(PermissionCode.ORGANIZATIONS_REVIEW_VERIFICATION in admin)
        assertTrue(PermissionCode.SUPPORT_VIEW in admin)
        assertTrue(PermissionCode.SUPPORT_MANAGE in admin)
        assertTrue(PermissionCode.SUPPORT_VIEW_SENSITIVE in admin)
        assertFalse(PermissionCode.ORGANIZATIONS_REVOKE_VERIFICATION in admin)

        val superadmin = RolePermissionMatrix.permissionsFor(setOf(PlatformRoleCode.SUPERADMIN))
        assertTrue(PermissionCode.ORGANIZATIONS_REVOKE_VERIFICATION in superadmin)
        assertTrue(admin.all { it in superadmin })
    }
}
