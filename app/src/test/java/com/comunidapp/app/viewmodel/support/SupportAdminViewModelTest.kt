package com.comunidapp.app.viewmodel.support

import com.comunidapp.app.data.mock.MockAuthDatabase
import com.comunidapp.app.data.mock.MockData
import com.comunidapp.app.data.repository.MockAuthRepository
import com.comunidapp.app.data.repository.MockPermissionRepository
import com.comunidapp.app.data.repository.MockSupportRepository
import com.comunidapp.app.data.repository.PermissionRepository
import com.comunidapp.app.domain.authorization.AuthorizationContext
import com.comunidapp.app.domain.authorization.PermissionCode
import com.comunidapp.app.domain.authorization.PlatformRoleCode
import com.comunidapp.app.domain.support.SupportCategory
import com.comunidapp.app.domain.support.SupportTicketStatus
import com.comunidapp.app.viewmodel.moderation.AdministrativeScreenPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SupportAdminViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var auth: MockAuthRepository
    private lateinit var permissions: MockPermissionRepository
    private lateinit var support: MockSupportRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        auth = MockAuthRepository()
        auth.resetForTests()
        permissions = MockPermissionRepository()
        permissions.resetForTests()
        support = MockSupportRepository()
        support.resetForTests()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        MockAuthDatabase.resetToFixtures()
    }

    @Test
    fun queue_denied_without_support_view() = runTest(dispatcher) {
        auth.login(MockData.currentUser.email, MockAuthDatabase.DEMO_PASSWORD)
        permissions.setRolesForTests(MockData.currentUser.id, setOf(PlatformRoleCode.MODERATOR))
        val vm = SupportAdminQueueViewModel(support, auth, permissions)
        advanceUntilIdle()
        assertEquals(AdministrativeScreenPhase.AccessDenied, vm.uiState.value.phase)
    }

    @Test
    fun close_requires_confirmation_then_succeeds_when_resolved() = runTest(dispatcher) {
        auth.login(MockData.currentUser.email, MockAuthDatabase.DEMO_PASSWORD)
        permissions.setRolesForTests(MockData.currentUser.id, setOf(PlatformRoleCode.ADMIN))
        val created = support.createTicket(
            "req",
            SupportCategory.OTHER,
            "Asunto staff",
            "Descripción suficientemente clara",
            1L
        )
        val ticketId = (created as com.comunidapp.app.core.result.AppResult.Success).data.id
        support.changeTicketStatus(ticketId, SupportTicketStatus.RESOLVED, null, 2L)
        val vm = SupportTicketAdminDetailViewModel(ticketId, support, auth, permissions) { 10L }
        advanceUntilIdle()
        vm.changeStatus(SupportTicketStatus.CLOSED)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.confirmClose)
        vm.confirmClose(true)
        vm.changeStatus(SupportTicketStatus.CLOSED, "resolved_ok")
        advanceUntilIdle()
        assertEquals(SupportTicketStatus.CLOSED, vm.uiState.value.ticket?.status)
    }

    @Test
    fun manage_without_view_sensitive_cannot_read_or_add_internal_notes() = runTest(dispatcher) {
        auth.login(MockData.currentUser.email, MockAuthDatabase.DEMO_PASSWORD)
        val created = support.createTicket(
            "req",
            SupportCategory.OTHER,
            "Asunto staff",
            "Descripción suficientemente clara",
            1L
        )
        val ticketId = (created as com.comunidapp.app.core.result.AppResult.Success).data.id
        support.addInternalMessage(ticketId, "staff", "dato interno", 2L)
        val permissionRepo = FixedPermissionRepository(
            AuthorizationContext(
                userId = MockData.currentUser.id,
                roles = setOf(PlatformRoleCode.SUPPORT),
                permissions = setOf(PermissionCode.SUPPORT_VIEW, PermissionCode.SUPPORT_MANAGE)
            )
        )

        val vm = SupportTicketAdminDetailViewModel(ticketId, support, auth, permissionRepo)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.canManage)
        assertTrue(vm.uiState.value.messages.none { it.body == "dato interno" })

        vm.onInternalDraftChange("otro dato")
        vm.sendInternalNote()
        advanceUntilIdle()
        assertEquals("No tenés permiso para notas internas.", vm.uiState.value.message)
    }

    private class FixedPermissionRepository(
        private val context: AuthorizationContext
    ) : PermissionRepository {
        override suspend fun getAuthorizationContext(userId: String): AuthorizationContext = context
        override fun observeAuthorizationContext(userId: String): Flow<AuthorizationContext> =
            flowOf(context)
        override suspend fun hasPermission(userId: String, permission: PermissionCode): Boolean =
            permission in context.permissions
        override suspend fun refresh(userId: String): AuthorizationContext = context
        override fun invalidate() = Unit
        override suspend fun setRolesForTests(
            userId: String,
            roles: Set<PlatformRoleCode>
        ) = Unit
    }
}
