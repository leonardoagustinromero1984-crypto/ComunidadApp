package com.comunidapp.app.viewmodel

import com.comunidapp.app.data.mock.MockAuthDatabase
import com.comunidapp.app.data.mock.MockData
import com.comunidapp.app.data.repository.MockAuthRepository
import com.comunidapp.app.domain.pets.PetCapability
import com.comunidapp.app.domain.pets.PetLinkStatus
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
class PetAuthorizationsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var authRepo: MockAuthRepository
    private lateinit var petRepo: FakeStage5PetRepository
    private lateinit var authzRepo: FakeStage5AuthorizationRepository
    private lateinit var userRepo: FakeStage5UserRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        authRepo = MockAuthRepository()
        authRepo.resetForTests()
        petRepo = FakeStage5PetRepository()
        authzRepo = FakeStage5AuthorizationRepository()
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

    private fun viewModel(canManage: Boolean = true): PetAuthorizationsViewModel {
        petRepo.accessResult = Result.success(
            stage5AccessContext(canManageAuthorizations = canManage)
        )
        return PetAuthorizationsViewModel(
            petId = "pet-1",
            authorizationRepository = authzRepo,
            petRepository = petRepo,
            userRepository = userRepo,
            authRepository = authRepo,
            nowEpochMs = { 10_000L },
            searchDebounceMs = 0L
        )
    }

    @Test
    fun load_lists_authorizations() = runTest(dispatcher) {
        login()
        authzRepo.items += stage5Authorization()
        val vm = viewModel()
        advanceUntilIdle()
        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.loadErrorMessage)
        assertEquals(1, state.authorizations.size)
        assertEquals(
            PetAuthorizationDisplayStatus.ACTIVE,
            vm.displayStatusOf(state.authorizations.first())
        )
    }

    @Test
    fun load_empty_state() = runTest(dispatcher) {
        login()
        val vm = viewModel(canManage = false)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isEmpty)
    }

    @Test
    fun load_unavailable_when_repository_null() = runTest(dispatcher) {
        login()
        val vm = PetAuthorizationsViewModel(
            petId = "pet-1",
            authorizationRepository = null,
            petRepository = petRepo,
            userRepository = userRepo,
            authRepository = authRepo
        )
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.loadErrorMessage)
    }

    @Test
    fun grant_multiple_valid_capabilities_success() = runTest(dispatcher) {
        login()
        val vm = viewModel()
        advanceUntilIdle()
        vm.grant(
            personId = "user_2",
            capabilities = setOf(
                PetCapability.READ,
                PetCapability.MANAGE_HEALTH,
                PetCapability.MANAGE_MEDIA
            ),
            validUntilEpochMs = null
        )
        advanceUntilIdle()
        assertEquals(1, authzRepo.createCalls)
        assertEquals(3, authzRepo.lastCreated?.capabilities?.size)
        assertEquals(1, vm.uiState.value.authorizations.size)
    }

    @Test
    fun grant_invalid_capability_rejected_locally() = runTest(dispatcher) {
        login()
        val vm = viewModel()
        advanceUntilIdle()
        listOf(
            PetCapability.INITIATE_TRANSFER,
            PetCapability.MARK_DECEASED,
            PetCapability.ARCHIVE,
            PetCapability.MANAGE_RESPONSIBILITIES
        ).forEach { forbidden ->
            vm.grant(
                personId = "user_2",
                capabilities = setOf(PetCapability.READ, forbidden),
                validUntilEpochMs = null
            )
            advanceUntilIdle()
        }
        assertEquals(0, authzRepo.createCalls)
        assertNotNull(vm.uiState.value.actionMessage)
    }

    @Test
    fun grant_empty_capabilities_rejected() = runTest(dispatcher) {
        login()
        val vm = viewModel()
        advanceUntilIdle()
        vm.grant(personId = "user_2", capabilities = emptySet(), validUntilEpochMs = null)
        advanceUntilIdle()
        assertEquals(0, authzRepo.createCalls)
    }

    @Test
    fun grant_blocked_without_capability() = runTest(dispatcher) {
        login()
        val vm = viewModel(canManage = false)
        advanceUntilIdle()
        vm.grant(
            personId = "user_2",
            capabilities = setOf(PetCapability.READ),
            validUntilEpochMs = null
        )
        advanceUntilIdle()
        assertEquals(0, authzRepo.createCalls)
        assertNotNull(vm.uiState.value.actionMessage)
    }

    @Test
    fun grant_past_validity_rejected() = runTest(dispatcher) {
        login()
        val vm = viewModel()
        advanceUntilIdle()
        vm.grant(
            personId = "user_2",
            capabilities = setOf(PetCapability.READ),
            validUntilEpochMs = 5_000L
        )
        advanceUntilIdle()
        assertEquals(0, authzRepo.createCalls)
    }

    @Test
    fun revoke_success_reloads() = runTest(dispatcher) {
        login()
        authzRepo.items += stage5Authorization(id = "auth-1")
        val vm = viewModel()
        advanceUntilIdle()
        vm.revoke("auth-1")
        advanceUntilIdle()
        assertEquals(1, authzRepo.revokeCalls)
        assertEquals(
            PetAuthorizationDisplayStatus.REVOKED,
            vm.displayStatusOf(vm.uiState.value.authorizations.first())
        )
    }

    @Test
    fun expired_authorization_display_status() = runTest(dispatcher) {
        login()
        authzRepo.items += stage5Authorization(id = "auth-old", validToEpochMs = 9_000L)
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(
            PetAuthorizationDisplayStatus.EXPIRED,
            vm.displayStatusOf(vm.uiState.value.authorizations.first())
        )
    }

    @Test
    fun backend_forbidden_maps_to_user_safe_message() = runTest(dispatcher) {
        login()
        authzRepo.mutationFailure =
            Exception("ERROR: permission denied for table pet_authorizations FORBIDDEN")
        val vm = viewModel()
        advanceUntilIdle()
        vm.grant(
            personId = "user_2",
            capabilities = setOf(PetCapability.READ),
            validUntilEpochMs = null
        )
        advanceUntilIdle()
        val message = vm.uiState.value.actionMessage
        assertNotNull(message)
        assertFalse(message!!.contains("permission denied"))
        assertFalse(message.contains("pet_authorizations"))
    }

    @Test
    fun double_submit_only_one_repository_call() = runTest(dispatcher) {
        login()
        val vm = viewModel()
        advanceUntilIdle()
        authzRepo.createGate = CompletableDeferred()
        vm.grant(
            personId = "user_2",
            capabilities = setOf(PetCapability.READ),
            validUntilEpochMs = null
        )
        vm.grant(
            personId = "user_3",
            capabilities = setOf(PetCapability.READ),
            validUntilEpochMs = null
        )
        authzRepo.createGate?.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, authzRepo.createCalls)
    }

    @Test
    fun revoked_link_status_stays_visible_in_list() = runTest(dispatcher) {
        login()
        authzRepo.items += stage5Authorization(id = "auth-rev", status = PetLinkStatus.REVOKED)
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.authorizations.size)
        assertEquals(
            PetAuthorizationDisplayStatus.REVOKED,
            vm.displayStatusOf(vm.uiState.value.authorizations.first())
        )
    }
}
