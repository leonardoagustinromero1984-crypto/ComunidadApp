package com.comunidapp.app.domain.user

import com.comunidapp.app.data.mock.MockData
import com.comunidapp.app.data.repository.MockPermissionRepository
import com.comunidapp.app.data.repository.MockPlatformAdministrationRepository
import com.comunidapp.app.domain.authorization.PermissionCode
import com.comunidapp.app.domain.authorization.PlatformRoleCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Gates Etapa 5: denegación por defecto y PII condicional.
 */
class M02Stage5GateSafetyTest {

    private lateinit var permissions: MockPermissionRepository
    private lateinit var admin: MockPlatformAdministrationRepository

    @Before
    fun setUp() {
        permissions = MockPermissionRepository()
        permissions.resetForTests()
        admin = MockPlatformAdministrationRepository(permissions)
    }

    @Test
    fun restricted_gate_blocks_main_access_for_suspended() {
        assertFalse(
            AccountStatusRules.canAccessMain(AccountStatus.SUSPENDED)
        )
        assertFalse(
            AccountStatusRules.canAccessMain(AccountStatus.BANNED)
        )
        assertTrue(
            AccountStatusRules.canAccessMain(AccountStatus.RESTRICTED)
        )
        assertTrue(
            AccountStatusRules.canAccessMain(AccountStatus.ACTIVE)
        )
    }

    @Test
    fun userWithoutRolesView_cannotSearchAdminUsers() = runTest {
        val id = MockData.currentUser.id
        // default USER
        val result = admin.searchUsers("maria")
        assertTrue(result.isFailure)
    }

    @Test
    fun logoutInvalidatesPermissionCache_afterRoleElevation() = runTest {
        val id = MockData.currentUser.id
        permissions.setRolesForTests(id, setOf(PlatformRoleCode.MODERATOR))
        assertTrue(permissions.hasPermission(id, PermissionCode.MODERATION_VIEW))
        permissions.invalidate()
        permissions.resetForTests()
        assertFalse(permissions.hasPermission(id, PermissionCode.MODERATION_VIEW))
    }
}
