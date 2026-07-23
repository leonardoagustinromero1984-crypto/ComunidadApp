package com.comunidapp.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.comunidapp.app.data.mock.MockAuthDatabase
import com.comunidapp.app.data.mock.MockData
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.remote.supabase.m08.M08PetErrorMapper
import com.comunidapp.app.data.remote.supabase.m08.M08PetException
import com.comunidapp.app.data.remote.supabase.m08.PetDuplicateCandidateRow
import com.comunidapp.app.data.remote.supabase.m08.PetStatusHistoryM08Row
import com.comunidapp.app.data.repository.MockAuthRepository
import com.comunidapp.app.data.repository.MockPlatformRepository
import com.comunidapp.app.domain.pets.PetPrincipalHolder
import com.comunidapp.app.domain.pets.PetResponsibilityRole
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

/**
 * LeoVer M08 Etapa 7 — integración focalizada entre ViewModels / contratos
 * ya implementados (sin red, sin Supabase real).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class M08IntegrationRegressionTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var authRepo: MockAuthRepository
    private lateinit var petRepo: FakeStage5PetRepository
    private lateinit var respRepo: FakeStage5ResponsibilityRepository
    private lateinit var transferRepo: FakeStage5TransferRepository
    private lateinit var userRepo: FakeStage5UserRepository
    private lateinit var platformRepo: MockPlatformRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        authRepo = MockAuthRepository()
        authRepo.resetForTests()
        petRepo = FakeStage5PetRepository(pet = null)
        respRepo = FakeStage5ResponsibilityRepository()
        transferRepo = FakeStage5TransferRepository()
        userRepo = FakeStage5UserRepository()
        platformRepo = MockPlatformRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        MockAuthDatabase.resetToFixtures()
    }

    private suspend fun login() {
        authRepo.login(MockData.currentUser.email, MockAuthDatabase.DEMO_PASSWORD)
    }

    private fun detailVm(petId: String): PetDetailViewModel = PetDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("petId" to petId)),
        authRepository = authRepo,
        petRepository = petRepo,
        platformRepository = platformRepo
    )

    private fun formVm(
        editPetId: String? = null,
        debounceMs: Long = 0L
    ): PetFormViewModel = PetFormViewModel(
        editPetId = editPetId,
        authRepository = authRepo,
        petRepository = petRepo,
        duplicateDebounceMs = debounceMs
    )

    @Test
    fun createAndRetrievePet_throughFormAndDetail() = runTest {
        login()
        petRepo.accessResult = Result.success(
            stage5AccessContext(canRead = true).copy(canUpdate = true, canManageMedia = true)
        )
        val form = formVm()
        advanceUntilIdle()
        assertFalse(form.uiState.value.isLoading)
        assertFalse(form.uiState.value.isEditMode)

        form.onNameChange("Nueva Luna")
        form.onDescriptionChange("Creada en integración")
        form.onSizeChange(PetSize.SMALL)
        form.savePet()
        advanceUntilIdle()

        assertTrue(form.uiState.value.saveSuccess)
        assertEquals(1, petRepo.createCalls)
        val createdId = petRepo.lastCreated?.id
        assertNotNull(createdId)
        assertEquals("Nueva Luna", petRepo.getPetById(createdId!!)?.name)

        val detail = detailVm(createdId)
        advanceUntilIdle()
        assertFalse(detail.isPetLoading.value)
        assertNull(detail.petLoadError.value)
        assertEquals("Nueva Luna", detail.pet.value?.name)
        assertEquals("Creada en integración", detail.pet.value?.description)
    }

    @Test
    fun editExistingPet_persistsChanges() = runTest {
        login()
        petRepo.pet = stage5Pet(id = "pet-1", status = "ACTIVE").copy(description = "Antes")
        petRepo.accessResult = Result.success(
            stage5AccessContext(canRead = true).copy(canUpdate = true, canManageMedia = true)
        )
        val form = formVm(editPetId = "pet-1")
        advanceUntilIdle()
        assertTrue(form.uiState.value.isEditMode)
        assertEquals("Luna", form.uiState.value.name)

        form.onDescriptionChange("Después de edición")
        form.savePet()
        advanceUntilIdle()

        assertTrue(form.uiState.value.saveSuccess)
        assertEquals(1, petRepo.updateCalls)
        assertEquals("Después de edición", petRepo.getPetById("pet-1")?.description)
    }

    @Test
    fun detectDuplicateCandidate_showsPrivateWarning() = runTest {
        login()
        petRepo.pet = null
        petRepo.duplicateCandidatesResult = Result.success(
            listOf(PetDuplicateCandidateRow(petId = "other-pet", matchReason = "MICROCHIP"))
        )
        val form = formVm(debounceMs = 0L)
        advanceUntilIdle()
        form.onMicrochipChange("CHIP-DUP-001")
        advanceUntilIdle()

        assertNotNull(form.uiState.value.duplicateWarning)
        assertTrue(
            form.uiState.value.duplicateWarning!!.contains("microchip", ignoreCase = true)
        )
    }

    @Test
    fun listResponsibilities_showsPrincipalAndCoResponsible() = runTest {
        login()
        petRepo.pet = stage5Pet()
        petRepo.accessResult = Result.success(
            stage5AccessContext(canManageResponsibilities = true)
        )
        respRepo.items += stage5Responsibility(
            id = "resp-p",
            role = PetResponsibilityRole.PRINCIPAL,
            holder = PetPrincipalHolder.Person("user_1")
        )
        respRepo.items += stage5Responsibility(id = "resp-c")

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

        val state = vm.uiState.value
        assertNull(state.loadErrorMessage)
        assertEquals("resp-p", state.principal?.id?.value)
        assertEquals(listOf("resp-c"), state.coResponsibles.map { it.id.value })
        assertTrue(state.canManage)
    }

    @Test
    fun transferPet_initiatesPendingTransfer() = runTest {
        login()
        petRepo.pet = stage5Pet()
        petRepo.accessResult = Result.success(
            stage5AccessContext(
                canInitiateTransfer = true,
                canAcceptTransfer = true,
                canCancelTransfer = true
            )
        )
        val vm = PetTransfersViewModel(
            petId = "pet-1",
            transferRepository = transferRepo,
            petRepository = petRepo,
            userRepository = userRepo,
            authRepository = authRepo,
            nowEpochMs = { 10_000L },
            searchDebounceMs = 0L
        )
        advanceUntilIdle()
        vm.initiate(toPersonId = "user_2", toOrganizationId = null)
        advanceUntilIdle()

        assertEquals(1, transferRepo.createCalls)
        assertNotNull(vm.uiState.value.pendingTransfer)
        assertTrue(transferRepo.lastCreated?.toPrincipal is PetPrincipalHolder.Person)
    }

    @Test
    fun markDeceased_thenDetailReflectsStatus() = runTest {
        login()
        petRepo.pet = stage5Pet(status = "ACTIVE")
        petRepo.accessResult = Result.success(
            stage5AccessContext(canRead = true).copy(canMarkDeceased = true)
        )
        petRepo.markDeceasedResult = Result.success(stage5Pet(status = "DECEASED"))
        val detail = detailVm("pet-1")
        advanceUntilIdle()
        assertTrue(detail.canMarkDeceased.value)

        detail.markPetDeceased("integración")
        advanceUntilIdle()

        assertTrue(detail.lifecycleSuccess.value)
        assertEquals(1, petRepo.markDeceasedCalls)
        assertEquals("DECEASED", petRepo.pet?.status)
        assertFalse(detail.canViewGovernance.value)
        assertFalse(detail.canManage.value)
    }

    @Test
    fun editBlocked_afterDeceased() = runTest {
        login()
        petRepo.pet = stage5Pet(status = "DECEASED")
        petRepo.accessResult = Result.success(
            stage5AccessContext(canRead = true).copy(canUpdate = true)
        )
        val form = formVm(editPetId = "pet-1")
        advanceUntilIdle()

        assertFalse(form.uiState.value.isEditMode)
        assertTrue(form.uiState.value.mutationsLocked)
        assertEquals("DECEASED", form.uiState.value.petStatus)
        assertEquals(
            "No se puede editar una mascota fallecida.",
            form.uiState.value.errorMessage
        )
        form.savePet()
        advanceUntilIdle()
        assertEquals(0, petRepo.updateCalls)
        assertEquals(
            M08PetErrorMapper.userMessage("PET_NOT_ACTIVE"),
            form.uiState.value.errorMessage
        )
    }

    @Test
    fun statusHistory_loadsEntries() = runTest {
        login()
        petRepo.pet = stage5Pet()
        petRepo.accessResult = Result.success(
            stage5AccessContext(canRead = true).copy(canViewHistory = true)
        )
        petRepo.statusHistoryResult = Result.success(
            listOf(
                PetStatusHistoryM08Row(
                    id = "h1",
                    petId = "pet-1",
                    previousStatus = "ACTIVE",
                    newStatus = "DECEASED",
                    reasonCode = "DECEASED",
                    actorUserId = "user_1",
                    createdAt = "2026-03-01T00:00:00Z"
                )
            )
        )
        val history = PetStatusHistoryViewModel(
            petId = "pet-1",
            petRepository = petRepo,
            authRepository = authRepo
        )
        advanceUntilIdle()

        assertFalse(history.uiState.value.isLoading)
        assertNull(history.uiState.value.loadErrorMessage)
        assertEquals(1, history.uiState.value.entries.size)
        assertEquals("DECEASED", history.uiState.value.entries.first().newStatus)
        assertTrue(history.uiState.value.canViewHistory)
    }

    @Test
    fun detailWithEmptyHealth_loadsWithoutError() = runTest {
        login()
        petRepo.pet = stage5Pet().copy(
            vaccinations = emptyList(),
            lastDeworming = null,
            lastFleaTreatment = null,
            sterilized = null,
            microchipId = null,
            lastVetVisit = null,
            healthNotes = null,
            reminders = emptyList()
        )
        petRepo.accessResult = Result.success(stage5AccessContext(canRead = true))
        val detail = detailVm("pet-1")
        advanceUntilIdle()

        assertNotNull(detail.pet.value)
        assertNull(detail.petLoadError.value)
        assertTrue(detail.pet.value!!.vaccinations.isEmpty())
        assertTrue(detail.clinicalRecords.value.isEmpty())
    }

    @Test
    fun repositoryError_onDetail_isControlled() = runTest {
        login()
        petRepo.pet = null
        petRepo.fetchError = M08PetException("NETWORK", "NETWORK")
        petRepo.observeError = M08PetException("NETWORK", "NETWORK")
        val detail = detailVm("pet-1")
        advanceUntilIdle()

        assertFalse(detail.isPetLoading.value)
        assertNull(detail.pet.value)
        assertEquals(M08PetErrorMapper.userMessage("NETWORK"), detail.petLoadError.value)
    }

    @Test
    fun blankOrMissingPetId_exposesNotFound() = runTest {
        login()
        petRepo.pet = null
        val blank = detailVm("")
        advanceUntilIdle()
        assertFalse(blank.isPetLoading.value)
        assertEquals(M08PetErrorMapper.userMessage("PET_NOT_FOUND"), blank.petLoadError.value)

        val missing = detailVm("does-not-exist")
        advanceUntilIdle()
        assertFalse(missing.isPetLoading.value)
        assertNull(missing.pet.value)
        assertEquals(M08PetErrorMapper.userMessage("PET_NOT_FOUND"), missing.petLoadError.value)
    }

    @Test
    fun unknownLifecycleStatus_stillLoadsDetail() = runTest {
        login()
        petRepo.pet = stage5Pet(status = "CUSTOM_UNKNOWN_STATUS")
        petRepo.accessResult = Result.success(stage5AccessContext(canRead = true))
        val detail = detailVm("pet-1")
        advanceUntilIdle()

        assertEquals("CUSTOM_UNKNOWN_STATUS", detail.pet.value?.status)
        assertNull(detail.petLoadError.value)
        assertFalse(detail.canManage.value)
        assertFalse(detail.canMarkDeceased.value)
    }
}
