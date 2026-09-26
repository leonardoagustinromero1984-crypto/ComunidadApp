package com.comunidapp.app.domain.authorization

import com.comunidapp.app.domain.user.AccountStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminAccessPolicyTest {

    private fun ctx(vararg roles: PlatformRoleCode): AuthorizationContext {
        val set = roles.toSet()
        return AuthorizationContext(
            userId = "person-1",
            roles = set,
            permissions = RolePermissionMatrix.permissionsFor(set)
        )
    }

    @Test
    fun userDoesNotEnterAdministration() {
        val user = ctx(PlatformRoleCode.USER)
        assertFalse(AdminAccessPolicy.canEnterAdministration(user))
        assertFalse(AdminAccessPolicy.canSeeUsers(user))
        assertFalse(AdminAccessPolicy.canSeeModeration(user))
        assertNull(AdminAccessPolicy.displayRoleLabel(user.roles))
    }

    @Test
    fun emptyContextDenied() {
        assertFalse(AdminAccessPolicy.canEnterAdministration(AuthorizationContext.empty()))
        assertFalse(AdminAccessPolicy.canEnterAdministration(AuthorizationContext.empty("x")))
    }

    @Test
    fun moderatorSeesOnlyModeration() {
        val mod = ctx(PlatformRoleCode.MODERATOR)
        assertTrue(AdminAccessPolicy.canEnterAdministration(mod))
        assertTrue(AdminAccessPolicy.canSeeModeration(mod))
        assertFalse(AdminAccessPolicy.canSeeUsers(mod))
        assertFalse(AdminAccessPolicy.canSeeStaff(mod))
        assertFalse(AdminAccessPolicy.canSeeCatalogs(mod))
        assertEquals("Moderador", AdminAccessPolicy.displayRoleLabel(mod.roles))
    }

    @Test
    fun adminSeesUsersAndModeration() {
        val admin = ctx(PlatformRoleCode.ADMIN)
        assertTrue(AdminAccessPolicy.canEnterAdministration(admin))
        assertTrue(AdminAccessPolicy.canSeeUsers(admin))
        assertTrue(AdminAccessPolicy.canSeeModeration(admin))
        assertTrue(AdminAccessPolicy.canSeeCatalogs(admin))
        assertFalse(AdminAccessPolicy.canSeeStaff(admin))
        assertEquals("Administrador", AdminAccessPolicy.displayRoleLabel(admin.roles))
    }

    @Test
    fun superadminSeesBlock1Hub() {
        val sa = ctx(PlatformRoleCode.SUPERADMIN)
        assertTrue(AdminAccessPolicy.canEnterAdministration(sa))
        assertTrue(AdminAccessPolicy.canSeeUsers(sa))
        assertTrue(AdminAccessPolicy.canSeeModeration(sa))
        assertTrue(AdminAccessPolicy.canSeeStaff(sa))
        assertTrue(AdminAccessPolicy.canSeeCatalogs(sa))
        assertEquals("Superadministrador", AdminAccessPolicy.displayRoleLabel(sa.roles))
    }

    @Test
    fun orgOwnerWithoutPlatformRoleDoesNotEnter() {
        val orgOwner = AuthorizationContext.empty("org-owner")
        assertFalse(AdminAccessPolicy.canEnterAdministration(orgOwner))
    }

    @Test
    fun labelsAreHumanReadable() {
        assertEquals("Activa", AdminAccessPolicy.accountStatusLabel(AccountStatus.ACTIVE))
        assertEquals(
            "Soporte",
            AdminAccessPolicy.platformRoleLabel(PlatformRoleCode.SUPPORT)
        )
        assertEquals(
            "Superadministrador",
            AdminAccessPolicy.platformRoleLabel(PlatformRoleCode.SUPERADMIN)
        )
    }
}
