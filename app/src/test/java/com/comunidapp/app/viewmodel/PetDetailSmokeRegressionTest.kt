package com.comunidapp.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.comunidapp.app.data.mock.MockAuthDatabase
import com.comunidapp.app.data.mock.MockData
import com.comunidapp.app.data.model.PetReminder
import com.comunidapp.app.data.model.SterilizationStatus
import com.comunidapp.app.data.model.VaccinationRecord
import com.comunidapp.app.data.remote.supabase.m08.M08PetErrorMapper
import com.comunidapp.app.data.remote.supabase.m08.M08PetException
import com.comunidapp.app.data.repository.MockAuthRepository
import com.comunidapp.app.data.repository.MockPlatformRepository
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
 * LeoVer M08 — regresión M08-SMOKE-001 (crash / fallo al abrir PetDetail).
 * Sin red ni Supabase real.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PetDetailSmokeRegressionTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var authRepo: MockAuthRepository
    private lateinit var petRepo: FakeStage5PetRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        authRepo = MockAuthRepository()
        authRepo.resetForTests()
        petRepo = FakeStage5PetRepository(
            accessResult = Result.success(stage5AccessContext(canRead = true)),
            pet = stage5Pet()
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        MockAuthDatabase.resetToFixtures()
    }

    private suspend fun login() {
        authRepo.login(MockData.currentUser.email, MockAuthDatabase.DEMO_PASSWORD)
    }

    private fun vm(petId: String = "pet-1"): PetDetailViewModel = PetDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("petId" to petId)),
        authRepository = authRepo,
        petRepository = petRepo,
        platformRepository = MockPlatformRepository()
    )

    @Test
    fun openDetail_withCompletePet_loadsWithoutError() = runTest {
        login()
        petRepo.pet = stage5Pet().copy(
            description = "Completa",
            vaccinations = listOf(
                VaccinationRecord(name = "Antirrábica", date = "2026-01-10", nextDueDate = "2027-01-10")
            ),
            lastDeworming = "2026-02-01",
            sterilized = SterilizationStatus.YES,
            microchipId = "CHIP-1",
            healthNotes = "Ok",
            reminders = listOf(PetReminder("r1", "Vacuna", "2026-06-01", "VACCINE"))
        )
        val viewModel = vm()
        advanceUntilIdle()

        assertFalse(viewModel.isPetLoading.value)
        assertNull(viewModel.petLoadError.value)
        assertNotNull(viewModel.pet.value)
        assertEquals("Luna", viewModel.pet.value?.name)
        assertEquals(1, viewModel.pet.value?.vaccinations?.size)
        assertTrue(viewModel.clinicalRecords.value.isEmpty())
    }

    @Test
    fun openDetail_withIncompleteHealth_doesNotFailLoad() = runTest {
        login()
        petRepo.pet = stage5Pet().copy(
            vaccinations = listOf(
                VaccinationRecord(name = "", date = ""),
                VaccinationRecord(name = "Triple", date = "")
            ),
            lastDeworming = "",
            lastFleaTreatment = null,
            sterilized = null,
            microchipId = null,
            lastVetVisit = null,
            healthNotes = null,
            reminders = listOf(PetReminder("", "", "", ""))
        )
        val viewModel = vm()
        advanceUntilIdle()

        assertFalse(viewModel.isPetLoading.value)
        assertNull(viewModel.petLoadError.value)
        assertNotNull(viewModel.pet.value)
        // Incomplete health must not block the detail load itself.
        assertEquals(2, viewModel.pet.value?.vaccinations?.size)
    }

    @Test
    fun openDetail_withoutStatusHistory_stillLoadsPet() = runTest {
        login()
        petRepo.statusHistoryResult = Result.success(emptyList())
        val viewModel = vm()
        advanceUntilIdle()

        assertNotNull(viewModel.pet.value)
        assertNull(viewModel.petLoadError.value)
        // History is not loaded by PetDetail; empty history must not affect detail open.
        assertEquals("ACTIVE", viewModel.pet.value?.status)
    }

    @Test
    fun openDetail_repositoryError_exposesControlledError() = runTest {
        login()
        petRepo.pet = null
        petRepo.fetchError = M08PetException("NETWORK", "NETWORK")
        petRepo.observeError = M08PetException("NETWORK", "NETWORK")
        val viewModel = vm()
        advanceUntilIdle()

        assertFalse(viewModel.isPetLoading.value)
        assertNull(viewModel.pet.value)
        assertEquals(M08PetErrorMapper.userMessage("NETWORK"), viewModel.petLoadError.value)
    }

    @Test
    fun openDetail_unknownPetId_exposesNotFound() = runTest {
        login()
        petRepo.pet = null
        val viewModel = vm(petId = "missing-pet")
        advanceUntilIdle()

        assertFalse(viewModel.isPetLoading.value)
        assertNull(viewModel.pet.value)
        assertEquals(M08PetErrorMapper.userMessage("PET_NOT_FOUND"), viewModel.petLoadError.value)
    }

    @Test
    fun openDetail_blankPetId_exposesNotFoundWithoutLoadingForever() = runTest {
        login()
        val viewModel = vm(petId = "")
        advanceUntilIdle()

        assertFalse(viewModel.isPetLoading.value)
        assertNull(viewModel.pet.value)
        assertEquals(M08PetErrorMapper.userMessage("PET_NOT_FOUND"), viewModel.petLoadError.value)
    }

    @Test
    fun openDetail_initialState_beforeDataArrives_isLoading() = runTest {
        // No login / no advance: constructor starts load; with Unconfined may complete immediately.
        // Assert the contract: either still loading with null pet, or already resolved.
        petRepo.pet = stage5Pet()
        val viewModel = vm()
        val loading = viewModel.isPetLoading.value
        val pet = viewModel.pet.value
        assertTrue(
            "Initial contract broken: loading=$loading pet=${pet?.id}",
            (loading && pet == null) || (!loading && pet != null)
        )
        advanceUntilIdle()
        assertFalse(viewModel.isPetLoading.value)
        assertNotNull(viewModel.pet.value)
    }

    @Test
    fun openDetail_deceasedPet_loadsAndHidesGovernance() = runTest {
        login()
        petRepo.pet = stage5Pet(status = "DECEASED")
        petRepo.accessResult = Result.success(stage5AccessContext(canRead = true))
        val viewModel = vm()
        advanceUntilIdle()

        assertEquals("DECEASED", viewModel.pet.value?.status)
        assertNull(viewModel.petLoadError.value)
        assertFalse(viewModel.canViewGovernance.value)
        assertFalse(viewModel.canManage.value)
        assertFalse(viewModel.canMarkDeceased.value)
    }

    @Test
    fun openDetail_unknownLifecycleStatus_stillLoads() = runTest {
        login()
        petRepo.pet = stage5Pet(status = "CUSTOM_UNKNOWN_STATUS")
        val viewModel = vm()
        advanceUntilIdle()

        assertEquals("CUSTOM_UNKNOWN_STATUS", viewModel.pet.value?.status)
        assertNull(viewModel.petLoadError.value)
        assertFalse(viewModel.isPetLoading.value)
    }
}
