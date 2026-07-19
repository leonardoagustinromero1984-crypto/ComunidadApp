package com.comunidapp.app.domain.authorization

import com.comunidapp.app.domain.moderation.authorization.AdministrativePermissionCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M04PermissionMatrixTest {

    @Test
    fun permissionCode_includes_m04_codes() {
        val codes = PermissionCode.entries.map { it.code }.toSet()
        assertTrue("moderation.manage_cases" in codes)
        assertTrue("moderation.apply_actions" in codes)
        assertTrue("moderation.view_sensitive" in codes)
        assertTrue("moderation.review_appeals" in codes)
        assertTrue("organizations.review_verification" in codes)
        assertTrue("organizations.revoke_verification" in codes)
        assertTrue("support.view" in codes)
        assertTrue("support.manage" in codes)
        assertTrue("support.view_sensitive" in codes)
    }

    @Test
    fun moderator_matrix_matches_etapa3() {
        val perms = RolePermissionMatrix.permissionsFor(setOf(PlatformRoleCode.MODERATOR))
        assertTrue(PermissionCode.MODERATION_VIEW in perms)
        assertTrue(PermissionCode.MODERATION_MANAGE_REPORTS in perms)
        assertTrue(PermissionCode.MODERATION_MANAGE_CASES in perms)
        assertTrue(PermissionCode.MODERATION_REVIEW_APPEALS in perms)
    }

    @Test
    fun admin_matrix_includes_support_and_verification_review() {
        val perms = RolePermissionMatrix.permissionsFor(setOf(PlatformRoleCode.ADMIN))
        assertTrue(PermissionCode.MODERATION_APPLY_ACTIONS in perms)
        assertTrue(PermissionCode.MODERATION_VIEW_SENSITIVE in perms)
        assertTrue(PermissionCode.ORGANIZATIONS_REVIEW_VERIFICATION in perms)
        assertTrue(PermissionCode.SUPPORT_VIEW in perms)
        assertTrue(PermissionCode.SUPPORT_MANAGE in perms)
        assertTrue(PermissionCode.SUPPORT_VIEW_SENSITIVE in perms)
        assertTrue(PermissionCode.AUDIT_VIEW in perms)
    }

    @Test
    fun superadmin_includes_revoke_verification() {
        val perms = RolePermissionMatrix.permissionsFor(setOf(PlatformRoleCode.SUPERADMIN))
        assertTrue(PermissionCode.ORGANIZATIONS_REVOKE_VERIFICATION in perms)
        val admin = RolePermissionMatrix.permissionsFor(setOf(PlatformRoleCode.ADMIN))
        assertTrue(admin.all { it in perms })
    }

    @Test
    fun fromM02_maps_new_administrative_codes() {
        assertEquals(
            AdministrativePermissionCode.MODERATION_MANAGE_CASES,
            AdministrativePermissionCode.fromM02(PermissionCode.MODERATION_MANAGE_CASES)
        )
        assertEquals(
            AdministrativePermissionCode.SUPPORT_VIEW_SENSITIVE,
            AdministrativePermissionCode.fromM02(PermissionCode.SUPPORT_VIEW_SENSITIVE)
        )
        assertEquals(
            AdministrativePermissionCode.ORGANIZATIONS_REVOKE_VERIFICATION,
            AdministrativePermissionCode.fromM02(PermissionCode.ORGANIZATIONS_REVOKE_VERIFICATION)
        )
        assertNotNull(AdministrativePermissionCode.fromM02(PermissionCode.AUDIT_VIEW))
    }
}
