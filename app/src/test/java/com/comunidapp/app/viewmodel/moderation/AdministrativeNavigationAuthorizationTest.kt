package com.comunidapp.app.viewmodel.moderation

import com.comunidapp.app.data.mock.MockAuthDatabase
import com.comunidapp.app.data.mock.MockData
import com.comunidapp.app.data.repository.MockAuthRepository
import com.comunidapp.app.data.repository.MockPermissionRepository
import com.comunidapp.app.domain.authorization.PermissionCode
import com.comunidapp.app.domain.authorization.PlatformRoleCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdministrativeNavigationAuthorizationTest {

    private lateinit var auth: MockAuthRepository
    private lateinit var permissions: MockPermissionRepository

    @Before
    fun setUp() {
        auth = MockAuthRepository()
        auth.resetForTests()
        permissions = MockPermissionRepository()
        permissions.resetForTests()
    }

    @After
    fun tearDown() {
        MockAuthDatabase.resetToFixtures()
    }

    @Test
    fun direct_route_denied_without_session() = runTest {
        val decision = AdministrativeAccessGate.evaluate(
            auth, permissions, PermissionCode.MODERATION_VIEW
        )
        assertFalse(decision.allowed)
    }

    @Test
    fun direct_route_denied_for_user_role() = runTest {
        auth.login(MockData.currentUser.email, MockAuthDatabase.DEMO_PASSWORD)
        permissions.setRolesForTests(MockData.currentUser.id, setOf(PlatformRoleCode.USER))
        val decision = AdministrativeAccessGate.evaluate(
            auth, permissions, PermissionCode.AUDIT_VIEW
        )
        assertFalse(decision.allowed)
    }

    @Test
    fun admin_allowed_for_audit() = runTest {
        auth.login(MockData.currentUser.email, MockAuthDatabase.DEMO_PASSWORD)
        permissions.setRolesForTests(MockData.currentUser.id, setOf(PlatformRoleCode.ADMIN))
        val decision = AdministrativeAccessGate.evaluate(
            auth, permissions, PermissionCode.AUDIT_VIEW
        )
        assertTrue(decision.allowed)
    }
}
