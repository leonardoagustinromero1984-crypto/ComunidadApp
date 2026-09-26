package com.comunidapp.app.viewmodel

import com.comunidapp.app.data.mock.MockAuthDatabase
import com.comunidapp.app.data.mock.MockData
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.remote.supabase.m08.PetAccessContext
import com.comunidapp.app.data.repository.MockAuthRepository
import com.comunidapp.app.domain.pets.PetEditAuthorization
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PetFormViewModelEditPathTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var authRepo: MockAuthRepository
    private lateinit var petRepo: FakeStage5PetRepository
    private lateinit var userRepo: FakeStage5UserRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        authRepo = MockAuthRepository()
        authRepo.resetForTests()
        petRepo = FakeStage5PetRepository()
        userRepo = FakeStage5UserRepository(
            usersById = mapOf(MockData.currentUser.id to MockData.currentUser)
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        MockAuthDatabase.resetToFixtures()
    }

    @Test
    fun ownerActiveHydratesFormAsCanEditWithBackendName() = runTest(dispatcher) {
        authRepo.login(MockData.currentUser.email, MockAuthDatabase.DEMO_PASSWORD)
        val samuId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
        petRepo.pet = Pet(
            id = samuId,
            ownerId = MockData.currentUser.id,
            createdByUserId = MockData.currentUser.id,
            name = "Samu",
            species = PetSpecies.CAT,
            sex = PetSex.MALE,
            ageYears = 3,
            size = PetSize.SMALL,
            description = "Gato",
            status = "ACTIVE"
        )
        petRepo.accessResult = Result.success(
            PetAccessContext(
                petId = samuId,
                relationCode = "OWNER",
                principalPersonId = MockData.currentUser.id,
                principalOrganizationId = null,
                capabilities = emptyList(),
                canRead = true,
                canUpdate = false,
                canManageHealth = false,
                canManageMedia = true,
                canArchive = false,
                canMarkDeceased = false
            )
        )
        val vm = PetFormViewModel(
            editPetId = samuId,
            authRepository = authRepo,
            petRepository = petRepo,
            userRepository = userRepo
        )
        advanceUntilIdle()
        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(PetEditAuthorization.Decision.CAN_EDIT, state.editDecision)
        assertEquals("Samu", state.name)
        assertEquals(PetSpecies.CAT, state.species)
        assertEquals(PetSex.MALE, state.sex)
    }

    @Test
    fun healthSectionPreloadsCanonicalSignals() = runTest(dispatcher) {
        authRepo.login(MockData.currentUser.email, MockAuthDatabase.DEMO_PASSWORD)
        val samuId = "78068b30-03f1-4d41-82bc-81318a143471"
        petRepo.pet = Pet(
            id = samuId,
            ownerId = MockData.currentUser.id,
            createdByUserId = MockData.currentUser.id,
            name = "Samu",
            species = PetSpecies.DOG,
            sex = PetSex.MALE,
            ageYears = 3,
            size = PetSize.MEDIUM,
            description = "Perro",
            status = "ACTIVE",
            sterilized = com.comunidapp.app.data.model.SterilizationStatus.YES,
            lastVetVisit = "2026-08-07",
            vaccinations = listOf(
                com.comunidapp.app.data.model.VaccinationRecord(
                    name = "Rabia",
                    date = "2026-08-11"
                )
            ),
            lastDeworming = "2026-08-11",
            dewormingProduct = "Piperazina",
            lastFleaTreatment = "2026-08-05",
            fleaTreatmentProduct = "Permetrina (spray)"
        )
        petRepo.accessResult = Result.success(
            PetAccessContext(
                petId = samuId,
                relationCode = "OWNER",
                principalPersonId = MockData.currentUser.id,
                principalOrganizationId = null,
                capabilities = emptyList(),
                canRead = true,
                canUpdate = true,
                canManageHealth = true,
                canManageMedia = true,
                canArchive = false,
                canMarkDeceased = false
            )
        )
        val vm = PetFormViewModel(
            editPetId = samuId,
            authRepository = authRepo,
            petRepository = petRepo,
            userRepository = userRepo
        )
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(com.comunidapp.app.data.model.SterilizationStatus.YES, state.sterilized)
        assertEquals("2026-08-07", state.lastVetVisit)
        assertEquals("Rabia", state.vaccinations.firstOrNull()?.name)
        assertEquals("Piperazina", state.dewormingProduct)
        assertEquals("2026-08-11", state.lastDeworming)
        assertEquals("Permetrina (spray)", state.fleaTreatmentProduct)
    }

    @Test
    fun createPetUsesSessionResolvedPersonAndDoesNotEmitPetPerson01() = runTest(dispatcher) {
        authRepo.login(MockData.currentUser.email, MockAuthDatabase.DEMO_PASSWORD)
        userRepo.usersById = emptyMap()
        com.comunidapp.app.domain.user.SessionResolvedPerson.set(
            MockData.currentUser.copy(
                birthDate = "1990-01-15",
                homeLocalityId = "loc-1",
                onboardingStatus = "COMPLETED"
            )
        )
        val vm = PetFormViewModel(
            editPetId = null,
            authRepository = authRepo,
            petRepository = petRepo,
            userRepository = userRepo
        )
        advanceUntilIdle()
        vm.onNameChange("Lola")
        vm.savePet()
        advanceUntilIdle()
        val state = vm.uiState.value
        assertTrue(state.errorMessage?.contains("PET-PERSON-01") != true)
        assertTrue(state.errorMessage?.contains("Todavía no encontramos tu perfil Persona") != true)
        assertEquals(1, petRepo.createCalls)
        com.comunidapp.app.domain.user.SessionResolvedPerson.clear()
    }
}
