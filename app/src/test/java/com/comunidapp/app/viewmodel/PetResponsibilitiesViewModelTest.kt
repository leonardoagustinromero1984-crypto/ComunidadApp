package com.comunidapp.app.viewmodel

import com.comunidapp.app.data.mock.MockAuthDatabase
import com.comunidapp.app.data.mock.MockData
import com.comunidapp.app.data.repository.MockAuthRepository
import com.comunidapp.app.domain.pets.PetLinkStatus
import com.comunidapp.app.domain.pets.PetPrincipalHolder
import com.comunidapp.app.domain.pets.PetResponsibilityRole
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PetResponsibilitiesViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var authRepo: MockAuthRepository
    private lateinit var petRepo: FakeStage5PetRepository
    private lateinit var respRepo: FakeStage5ResponsibilityRepository
    private lateinit var userRepo: FakeStage5UserRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        authRepo = MockAuthRepository()
        authRepo.resetForTests()
        petRepo = FakeStage5PetRepository()
        respRepo = FakeStage5ResponsibilityRepository()
        userRepo = FakeStage5UserRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        MockAuthDatabase.resetToFixtures()
    }

    private suspend fun login() {
        authRepo.login(MockData.currentUser.email, MockAuthDatabase.DEMO_PASSWORD)
    }

    private fun viewModel(canManage: Boolean = true): PetResponsibilitiesViewModel {
        petRepo.accessResult = Result.success(
            stage5AccessContext(canManageResponsibilities = canManage)
        )
        return PetResponsibilitiesViewModel(
            petId = "pet-1",
            responsibilityRepository = respRepo,
            petRepository = petRepo,
            userRepository = userRepo,
            authRepository = authRepo,
            nowEpochMs = { 10_000L },
            searchDebounceMs = 0L
        )
    }

    @Test
    fun load_partitions_principal_person_co_and_custodians() = runTest(dispatcher) {
        login()
        respRepo.items += stage5Responsibility(
            id = "resp-p",
            role = PetResponsibilityRole.PRINCIPAL,
            holder = PetPrincipalHolder.Person("user_1")
        )
        respRepo.items += stage5Responsibility(id = "resp-c")
        respRepo.items += stage5Responsibility(
            id = "resp-t",
            role = PetResponsibilityRole.TEMPORARY_CUSTODIAN,
            holder = PetPrincipalHolder.Person("user_3")
        )
        val vm = viewModel()
        advanceUntilIdle()
        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.loadErrorMessage)
        assertEquals("resp-p", state.principal?.id?.value)
        assertEquals(listOf("resp-c"), state.coResponsibles.map { it.id.value })
        assertEquals(listOf("resp-t"), state.custodians.map { it.id.value })
        assertFalse(state.isEmpty)
    }

    @Test
    fun load_renders_organization_principal() = runTest(dispatcher) {
        login()
        respRepo.items += stage5Responsibility(
            id = "resp-org",
            role = PetResponsibilityRole.PRINCIPAL,
            holder = stage5OrgHolder("org-9")
        )
        val vm = viewModel()
        advanceUntilIdle()
        val principal = vm.uiState.value.principal
        assertNotNull(principal)
        assertTrue(principal!!.holder is PetPrincipalHolder.Organization)
    }

    @Test
    fun load_empty_state() = runTest(dispatcher) {
        login()
        val vm = viewModel(canManage = false)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isEmpty)
    }

    @Test
    fun load_denied_when_cannot_read() = runTest(dispatcher) {
        login()
        petRepo.accessResult = Result.success(stage5AccessContext(canRead = false))
        val vm = PetResponsibilitiesViewModel(
            petId = "pet-1",
            responsibilityRepository = respRepo,
            petRepository = petRepo,
            userRepository = userRepo,
            authRepository = authRepo,
            nowEpochMs = { 10_000L },
            searchDebounceMs = 0L
        )
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.loadErrorMessage)
    }

    @Test
    fun load_unavailable_when_repository_null() = runTest(dispatcher) {
        login()
        val vm = PetResponsibilitiesViewModel(
            petId = "pet-1",
            responsibilityRepository = null,
            petRepository = petRepo,
            userRepository = userRepo,
            authRepository = authRepo
        )
        advanceUntilIdle()
        val message = vm.uiState.value.loadErrorMessage
        assertNotNull(message)
        assertTrue(message!!.contains("no está disponible"))
    }

    @Test
    fun load_error_is_user_safe_not_postgres() = runTest(dispatcher) {
        login()
        respRepo.listFailure = Exception("ERROR: relation \"pet_responsibilities\" FORBIDDEN by RLS")
        val vm = viewModel()
        advanceUntilIdle()
        val message = vm.uiState.value.loadErrorMessage
        assertNotNull(message)
        assertFalse(message!!.contains("relation"))
        assertFalse(message.contains("RLS"))
    }

    @Test
    fun addCoResponsible_person_success_reloads() = runTest(dispatcher) {
        login()
        val vm = viewModel()
        advanceUntilIdle()
        vm.addCoResponsible(personId = "user_2", organizationId = null)
        advanceUntilIdle()
        assertEquals(1, respRepo.assignCalls)
        assertEquals(PetResponsibilityRole.CO_RESPONSIBLE, respRepo.lastAssignedRole)
        assertEquals(1, vm.uiState.value.coResponsibles.size)
        assertFalse(vm.uiState.value.isSubmitting)
    }

    @Test
    fun addCoResponsible_organization_destination() = runTest(dispatcher) {
        login()
        val vm = viewModel()
        advanceUntilIdle()
        vm.addCoResponsible(personId = null, organizationId = "org-7")
        advanceUntilIdle()
        assertEquals(1, respRepo.assignCalls)
        assertTrue(respRepo.lastAssigned?.holder is PetPrincipalHolder.Organization)
    }

    @Test
    fun addCoResponsible_blocked_without_capability() = runTest(dispatcher) {
        login()
        val vm = viewModel(canManage = false)
        advanceUntilIdle()
        vm.addCoResponsible(personId = "user_2", organizationId = null)
        advanceUntilIdle()
        assertEquals(0, respRepo.assignCalls)
        assertNotNull(vm.uiState.value.actionMessage)
    }

    @Test
    fun mutations_blocked_when_pet_archived() = runTest(dispatcher) {
        login()
        petRepo.pet = stage5Pet(status = "ARCHIVED")
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.mutationsLocked)
        vm.addCoResponsible(personId = "user_2", organizationId = null)
        advanceUntilIdle()
        assertEquals(0, respRepo.assignCalls)
    }

    @Test
    fun mutations_blocked_when_pet_deceased() = runTest(dispatcher) {
        login()
        petRepo.pet = stage5Pet(status = "DECEASED")
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.mutationsLocked)
        vm.revoke("resp-any")
        advanceUntilIdle()
        assertEquals(0, respRepo.revokeCalls)
    }

    @Test
    fun custody_requires_end_date() = runTest(dispatcher) {
        login()
        val vm = viewModel()
        advanceUntilIdle()
        vm.addTemporaryCustodian(personId = "user_2", organizationId = null, endsAtEpochMs = null)
        advanceUntilIdle()
        assertEquals(0, respRepo.assignCalls)
        assertNotNull(vm.uiState.value.actionMessage)
    }

    @Test
    fun custody_with_end_date_succeeds() = runTest(dispatcher) {
        login()
        val vm = viewModel()
        advanceUntilIdle()
        vm.addTemporaryCustodian(
            personId = "user_2",
            organizationId = null,
            endsAtEpochMs = 99_999L
        )
        advanceUntilIdle()
        assertEquals(1, respRepo.assignCalls)
        assertEquals(PetResponsibilityRole.TEMPORARY_CUSTODIAN, respRepo.lastAssignedRole)
        assertEquals(99_999L, respRepo.lastAssigned?.validToEpochMs)
    }

    @Test
    fun revoke_principal_never_calls_repository() = runTest(dispatcher) {
        login()
        respRepo.items += stage5Responsibility(
            id = "resp-p",
            role = PetResponsibilityRole.PRINCIPAL,
            holder = PetPrincipalHolder.Person("user_1")
        )
        val vm = viewModel()
        advanceUntilIdle()
        vm.revoke("resp-p")
        advanceUntilIdle()
        assertEquals(0, respRepo.revokeCalls)
        assertNotNull(vm.uiState.value.actionMessage)
        assertEquals(
            PetLinkStatus.ACTIVE,
            respRepo.items.first { it.id.value == "resp-p" }.status
        )
    }

    @Test
    fun revoke_coResponsible_success() = runTest(dispatcher) {
        login()
        respRepo.items += stage5Responsibility(id = "resp-c")
        val vm = viewModel()
        advanceUntilIdle()
        vm.revoke("resp-c")
        advanceUntilIdle()
        assertEquals(1, respRepo.revokeCalls)
        assertTrue(vm.uiState.value.coResponsibles.isEmpty())
        assertEquals(1, vm.uiState.value.inactiveLinks.size)
    }

    @Test
    fun double_submit_only_one_repository_call() = runTest(dispatcher) {
        login()
        val vm = viewModel()
        advanceUntilIdle()
        respRepo.assignGate = CompletableDeferred()
        vm.addCoResponsible(personId = "user_2", organizationId = null)
        vm.addCoResponsible(personId = "user_3", organizationId = null)
        respRepo.assignGate?.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, respRepo.assignCalls)
    }

    @Test
    fun search_uses_controlled_user_repository() = runTest(dispatcher) {
        login()
        userRepo.profiles = listOf(stage5Profile())
        val vm = viewModel()
        advanceUntilIdle()
        vm.updateSearchQuery("car")
        advanceUntilIdle()
        assertEquals(1, userRepo.searchCalls)
        assertEquals(listOf("user_2"), vm.uiState.value.searchResults.map { it.id })
    }

    @Test
    fun search_short_query_does_not_call_repository() = runTest(dispatcher) {
        login()
        val vm = viewModel()
        advanceUntilIdle()
        vm.updateSearchQuery("c")
        advanceUntilIdle()
        assertEquals(0, userRepo.searchCalls)
        assertTrue(vm.uiState.value.searchResults.isEmpty())
    }
}
