package com.comunidapp.app.viewmodel.support

import com.comunidapp.app.data.mock.MockAuthDatabase
import com.comunidapp.app.data.mock.MockData
import com.comunidapp.app.data.repository.MockAuthRepository
import com.comunidapp.app.data.repository.MockSupportRepository
import com.comunidapp.app.domain.support.SupportCategory
import com.comunidapp.app.domain.support.SupportMessageVisibility
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
class SupportTicketViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var auth: MockAuthRepository
    private lateinit var support: MockSupportRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        auth = MockAuthRepository()
        auth.resetForTests()
        support = MockSupportRepository()
        support.resetForTests()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        MockAuthDatabase.resetToFixtures()
    }

    @Test
    fun create_and_double_submit_blocked() = runTest(dispatcher) {
        auth.login(MockData.currentUser.email, MockAuthDatabase.DEMO_PASSWORD)
        val vm = CreateSupportTicketViewModel(support, auth) { 10L }
        vm.setCategory(SupportCategory.PRIVACY)
        assertTrue(vm.uiState.value.showSensitiveWarning)
        vm.setSubject("Ayuda acceso")
        vm.setDescription("Necesito ayuda con mi cuenta en LeoVer")
        vm.submit()
        vm.submit()
        advanceUntilIdle()
        assertEquals("Ticket creado", vm.uiState.value.message)
        assertTrue(vm.uiState.value.createdTicketId != null)
    }

    @Test
    fun detail_hides_internal_messages() = runTest(dispatcher) {
        auth.login(MockData.currentUser.email, MockAuthDatabase.DEMO_PASSWORD)
        val created = support.createTicket(
            MockData.currentUser.id,
            SupportCategory.OTHER,
            "Asunto",
            "Descripción del ticket de soporte",
            1L
        )
        val ticketId = (created as com.comunidapp.app.core.result.AppResult.Success).data.id
        support.addRequesterMessage(ticketId, MockData.currentUser.id, "hola visible", 2L)
        support.addInternalMessage(ticketId, "staff", "nota interna secreta", 3L)
        val vm = SupportTicketDetailViewModel(ticketId, support, auth) { 10L }
        advanceUntilIdle()
        assertEquals(AdministrativeScreenPhase.Content, vm.uiState.value.phase)
        assertEquals(1, vm.uiState.value.messages.size)
        assertEquals(SupportMessageVisibility.REQUESTER_VISIBLE, vm.uiState.value.messages.first().visibility)
    }
}
