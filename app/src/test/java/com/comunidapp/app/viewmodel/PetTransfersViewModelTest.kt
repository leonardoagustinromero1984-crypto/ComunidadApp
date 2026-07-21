package com.comunidapp.app.viewmodel

import com.comunidapp.app.data.mock.MockAuthDatabase
import com.comunidapp.app.data.mock.MockData
import com.comunidapp.app.data.repository.MockAuthRepository
import com.comunidapp.app.domain.pets.PetPrincipalHolder
import com.comunidapp.app.domain.pets.PetTransferStatus
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
class PetTransfersViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var authRepo: MockAuthRepository
    private lateinit var petRepo: FakeStage5PetRepository
    private lateinit var transferRepo: FakeStage5TransferRepository
    private lateinit var userRepo: FakeStage5UserRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        authRepo = MockAuthRepository()
        authRepo.resetForTests()
        petRepo = FakeStage5PetRepository()
        transferRepo = FakeStage5TransferRepository()
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

    private fun viewModel(
        canInitiate: Boolean = true,
        canAccept: Boolean = true,
        canCancel: Boolean = true,
        principalPersonId: String? = "user_1",
        principalOrganizationId: String? = null
    ): PetTransfersViewModel {
        petRepo.accessResult = Result.success(
            stage5AccessContext(
                canInitiateTransfer = canInitiate,
                canAcceptTransfer = canAccept,
                canCancelTransfer = canCancel,
                principalPersonId = principalPersonId,
                principalOrganizationId = principalOrganizationId
            )
        )
        return PetTransfersViewModel(
            petId = "pet-1",
            transferRepository = transferRepo,
            petRepository = petRepo,
            userRepository = userRepo,
            authRepository = authRepo,
            nowEpochMs = { 10_000L },
            searchDebounceMs = 0L
        )
    }

    @Test
    fun load_partitions_pending_and_history() = runTest(dispatcher) {
        login()
        transferRepo.items += stage5Transfer(id = "x-pending")
        transferRepo.items += stage5Transfer(id = "x-old", status = PetTransferStatus.REJECTED)
        val vm = viewModel()
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals("x-pending", state.pendingTransfer?.id?.value)
        assertEquals(listOf("x-old"), state.history.map { it.id.value })
        assertFalse(state.isEmpty)
    }

    @Test
    fun load_empty_state() = runTest(dispatcher) {
        login()
        val vm = viewModel(canInitiate = false)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isEmpty)
        assertNull(vm.uiState.value.loadErrorMessage)
    }

    @Test
    fun load_unavailable_when_repository_null() = runTest(dispatcher) {
        login()
        val vm = PetTransfersViewModel(
            petId = "pet-1",
            transferRepository = null,
            petRepository = petRepo,
            userRepository = userRepo,
            authRepository = authRepo
        )
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.loadErrorMessage)
    }

    @Test
    fun initiate_to_person_success() = runTest(dispatcher) {
        login()
        val vm = viewModel()
        advanceUntilIdle()
        vm.initiate(toPersonId = "user_2", toOrganizationId = null)
        advanceUntilIdle()
        assertEquals(1, transferRepo.createCalls)
        val created = transferRepo.lastCreated
        assertTrue(created?.toPrincipal is PetPrincipalHolder.Person)
        assertEquals("xfer-1", vm.uiState.value.pendingTransfer?.id?.value)
    }

    @Test
    fun initiate_to_organization_success() = runTest(dispatcher) {
        login()
        val vm = viewModel()
        advanceUntilIdle()
        vm.initiate(toPersonId = null, toOrganizationId = "org-3")
        advanceUntilIdle()
        assertEquals(1, transferRepo.createCalls)
        assertTrue(transferRepo.lastCreated?.toPrincipal is PetPrincipalHolder.Organization)
    }

    @Test
    fun initiate_from_organization_principal() = runTest(dispatcher) {
        login()
        val vm = viewModel(principalPersonId = null, principalOrganizationId = "org-owner")
        advanceUntilIdle()
        vm.initiate(toPersonId = "user_2", toOrganizationId = null)
        advanceUntilIdle()
        assertEquals(1, transferRepo.createCalls)
        assertTrue(transferRepo.lastCreated?.fromPrincipal is PetPrincipalHolder.Organization)
    }

    @Test
    fun initiate_blocked_when_pending_exists() = runTest(dispatcher) {
        login()
        transferRepo.items += stage5Transfer(id = "x-pending")
        val vm = viewModel()
        advanceUntilIdle()
        vm.initiate(toPersonId = "user_2", toOrganizationId = null)
        advanceUntilIdle()
        assertEquals(0, transferRepo.createCalls)
        val message = vm.uiState.value.actionMessage
        assertNotNull(message)
        assertTrue(message!!.contains("pendiente"))
    }

    @Test
    fun initiate_blocked_without_capability() = runTest(dispatcher) {
        login()
        val vm = viewModel(canInitiate = false)
        advanceUntilIdle()
        vm.initiate(toPersonId = "user_2", toOrganizationId = null)
        advanceUntilIdle()
        assertEquals(0, transferRepo.createCalls)
    }

    @Test
    fun initiate_requires_destination_xor() = runTest(dispatcher) {
        login()
        val vm = viewModel()
        advanceUntilIdle()
        vm.initiate(toPersonId = null, toOrganizationId = null)
        vm.initiate(toPersonId = "user_2", toOrganizationId = "org-3")
        advanceUntilIdle()
        assertEquals(0, transferRepo.createCalls)
    }

    @Test
    fun initiate_blocked_when_pet_not_active() = runTest(dispatcher) {
        login()
        petRepo.pet = stage5Pet(status = "ARCHIVED")
        val vm = viewModel()
        advanceUntilIdle()
        vm.initiate(toPersonId = "user_2", toOrganizationId = null)
        advanceUntilIdle()
        assertEquals(0, transferRepo.createCalls)
        assertNotNull(vm.uiState.value.actionMessage)
    }

    @Test
    fun accept_success_refreshes_access_context() = runTest(dispatcher) {
        login()
        transferRepo.items += stage5Transfer(id = "x-1")
        val vm = viewModel()
        advanceUntilIdle()
        val callsAfterLoad = petRepo.accessCalls
        vm.accept("x-1")
        advanceUntilIdle()
        assertEquals(1, transferRepo.acceptCalls)
        assertTrue(petRepo.accessCalls > callsAfterLoad)
        assertNull(vm.uiState.value.pendingTransfer)
        assertEquals(
            PetTransferStatus.ACCEPTED,
            vm.uiState.value.history.first { it.id.value == "x-1" }.status
        )
    }

    @Test
    fun reject_success() = runTest(dispatcher) {
        login()
        transferRepo.items += stage5Transfer(id = "x-1")
        val vm = viewModel()
        advanceUntilIdle()
        vm.reject("x-1")
        advanceUntilIdle()
        assertEquals(1, transferRepo.rejectCalls)
        assertEquals(
            PetTransferStatus.REJECTED,
            vm.uiState.value.history.first { it.id.value == "x-1" }.status
        )
    }

    @Test
    fun cancel_passes_reason() = runTest(dispatcher) {
        login()
        transferRepo.items += stage5Transfer(id = "x-1")
        val vm = viewModel()
        advanceUntilIdle()
        vm.cancel("x-1", reason = "Cambio de planes")
        advanceUntilIdle()
        assertEquals(1, transferRepo.cancelCalls)
        assertEquals("Cambio de planes", transferRepo.lastCancelReason)
    }

    @Test
    fun terminal_transfer_not_editable() = runTest(dispatcher) {
        login()
        transferRepo.items += stage5Transfer(id = "x-done", status = PetTransferStatus.ACCEPTED)
        val vm = viewModel()
        advanceUntilIdle()
        vm.accept("x-done")
        vm.reject("x-done")
        vm.cancel("x-done")
        advanceUntilIdle()
        assertEquals(0, transferRepo.acceptCalls)
        assertEquals(0, transferRepo.rejectCalls)
        assertEquals(0, transferRepo.cancelCalls)
        assertNotNull(vm.uiState.value.actionMessage)
    }

    @Test
    fun accept_blocked_without_capability() = runTest(dispatcher) {
        login()
        transferRepo.items += stage5Transfer(id = "x-1")
        val vm = viewModel(canAccept = false)
        advanceUntilIdle()
        vm.accept("x-1")
        advanceUntilIdle()
        assertEquals(0, transferRepo.acceptCalls)
    }

    @Test
    fun cancel_blocked_without_capability() = runTest(dispatcher) {
        login()
        transferRepo.items += stage5Transfer(id = "x-1")
        val vm = viewModel(canCancel = false)
        advanceUntilIdle()
        vm.cancel("x-1")
        advanceUntilIdle()
        assertEquals(0, transferRepo.cancelCalls)
    }

    @Test
    fun backend_pet_not_active_maps_user_safe() = runTest(dispatcher) {
        login()
        transferRepo.mutationFailure = Exception("PET_NOT_ACTIVE")
        val vm = viewModel()
        advanceUntilIdle()
        vm.initiate(toPersonId = "user_2", toOrganizationId = null)
        advanceUntilIdle()
        val message = vm.uiState.value.actionMessage
        assertNotNull(message)
        assertFalse(message!!.contains("PET_NOT_ACTIVE"))
        assertTrue(message.contains("activa"))
    }

    @Test
    fun backend_forbidden_maps_user_safe() = runTest(dispatcher) {
        login()
        transferRepo.mutationFailure =
            Exception("ERROR: PET_TRANSFER_FORBIDDEN raised by plpgsql")
        transferRepo.items += stage5Transfer(id = "x-1")
        val vm = viewModel()
        advanceUntilIdle()
        vm.accept("x-1")
        advanceUntilIdle()
        val message = vm.uiState.value.actionMessage
        assertNotNull(message)
        assertFalse(message!!.contains("plpgsql"))
    }

    @Test
    fun double_submit_only_one_repository_call() = runTest(dispatcher) {
        login()
        val vm = viewModel()
        advanceUntilIdle()
        transferRepo.createGate = CompletableDeferred()
        vm.initiate(toPersonId = "user_2", toOrganizationId = null)
        vm.initiate(toPersonId = "user_3", toOrganizationId = null)
        transferRepo.createGate?.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, transferRepo.createCalls)
    }

    @Test
    fun transfer_detail_lookup_by_id() = runTest(dispatcher) {
        login()
        transferRepo.items += stage5Transfer(id = "x-1")
        transferRepo.items += stage5Transfer(id = "x-2", status = PetTransferStatus.CANCELLED)
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals("x-1", vm.uiState.value.transferById("x-1")?.id?.value)
        assertEquals(
            PetTransferStatus.CANCELLED,
            vm.uiState.value.transferById("x-2")?.status
        )
        assertNull(vm.uiState.value.transferById("missing"))
    }

    @Test
    fun default_expiry_matches_backend_seven_days() = runTest(dispatcher) {
        login()
        val vm = viewModel()
        advanceUntilIdle()
        vm.initiate(toPersonId = "user_2", toOrganizationId = null)
        advanceUntilIdle()
        val created = transferRepo.lastCreated
        assertNotNull(created)
        assertEquals(10_000L + PetTransfersViewModel.DEFAULT_EXPIRY_MS, created!!.expiresAtEpochMs)
    }
}
