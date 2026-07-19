package com.comunidapp.app.viewmodel.support

import com.comunidapp.app.data.mock.MockAuthDatabase
import com.comunidapp.app.data.mock.MockData
import com.comunidapp.app.data.repository.MockAuthRepository
import com.comunidapp.app.data.repository.MockPermissionRepository
import com.comunidapp.app.data.repository.MockSupportRepository
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
}
