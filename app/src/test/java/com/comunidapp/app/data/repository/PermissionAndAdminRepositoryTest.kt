package com.comunidapp.app.data.repository

import com.comunidapp.app.data.mock.MockData
import com.comunidapp.app.domain.authorization.PermissionCode
import com.comunidapp.app.domain.authorization.PlatformRoleCode
import com.comunidapp.app.domain.user.AccountStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PermissionAndAdminRepositoryTest {

    private lateinit var permissions: MockPermissionRepository
    private lateinit var admin: MockPlatformAdministrationRepository

    @Before
    fun setUp() {
        permissions = MockPermissionRepository()
        permissions.resetForTests()
        admin = MockPlatformAdministrationRepository(permissions)
    }

    @Test
    fun user_denied_moderation() = runTest {
        val id = MockData.currentUser.id
        assertFalse(permissions.hasPermission(id, PermissionCode.MODERATION_VIEW))
    }

    @Test
    fun moderator_allowed_moderation() = runTest {
        val id = MockData.currentUser.id
        permissions.setRolesForTests(id, setOf(PlatformRoleCode.MODERATOR))
        assertTrue(permissions.hasPermission(id, PermissionCode.MODERATION_VIEW))
        assertFalse(permissions.hasPermission(id, PermissionCode.USERS_CHANGE_STATUS))
    }

    @Test
    fun admin_cannot_self_assign() = runTest {
        val id = MockData.currentUser.id
        permissions.setRolesForTests(id, setOf(PlatformRoleCode.ADMIN))
        admin.seedRolesForTests(id, setOf(PlatformRoleCode.ADMIN))
        val result = admin.assignRole(
            id,
            PlatformRoleCode.SUPERADMIN,
            "manual_admin"
        )
        assertTrue(result.isFailure)
        assertEquals("SELF_ASSIGNMENT_FORBIDDEN", result.exceptionOrNull()?.message)
    }

    @Test
    fun admin_cannot_assign_superadmin() = runTest {
        val id = MockData.currentUser.id
        permissions.setRolesForTests(id, setOf(PlatformRoleCode.ADMIN))
        admin.seedRolesForTests(id, setOf(PlatformRoleCode.ADMIN))
        val target = "user_2"
        val result = admin.assignRole(target, PlatformRoleCode.SUPERADMIN, "manual_admin")
        assertTrue(result.isFailure)
        assertEquals("HIERARCHY_FORBIDDEN", result.exceptionOrNull()?.message)
    }

    @Test
    fun superadmin_can_assign_moderator() = runTest {
        val id = MockData.currentUser.id
        permissions.setRolesForTests(id, setOf(PlatformRoleCode.SUPERADMIN))
        admin.seedRolesForTests(id, setOf(PlatformRoleCode.SUPERADMIN))
        val target = "user_3"
        val result = admin.assignRole(target, PlatformRoleCode.MODERATOR, "staff_onboarding")
        assertTrue(result.isSuccess)
    }

    @Test
    fun change_status_creates_history() = runTest {
        val id = MockData.currentUser.id
        permissions.setRolesForTests(id, setOf(PlatformRoleCode.ADMIN))
        admin.seedRolesForTests(id, setOf(PlatformRoleCode.ADMIN))
        val target = "user_2"
        admin.changeAccountStatus(target, AccountStatus.SUSPENDED, "abuse").getOrThrow()
        val history = admin.getStatusHistory(target).getOrThrow()
        assertTrue(history.isNotEmpty())
        assertEquals(AccountStatus.SUSPENDED.name, history.first().newStatus)
    }

    @Test
    fun invalidate_clears_cache() = runTest {
        val id = MockData.currentUser.id
        permissions.setRolesForTests(id, setOf(PlatformRoleCode.MODERATOR))
        assertTrue(permissions.hasPermission(id, PermissionCode.MODERATION_VIEW))
        permissions.invalidate()
        permissions.resetForTests()
        assertFalse(permissions.hasPermission(id, PermissionCode.MODERATION_VIEW))
    }

    @Test
    fun blank_user_denied() = runTest {
        assertFalse(permissions.hasPermission("", PermissionCode.PROFILE_READ_OWN))
    }

    @Test
    fun private_email_hidden_without_permission() = runTest {
        val id = MockData.currentUser.id
        permissions.setRolesForTests(id, setOf(PlatformRoleCode.MODERATOR))
        admin.seedRolesForTests(id, setOf(PlatformRoleCode.MODERATOR))
        val results = admin.searchUsers("maria").getOrThrow()
        assertTrue(results.all { it.email == null })
    }
}
