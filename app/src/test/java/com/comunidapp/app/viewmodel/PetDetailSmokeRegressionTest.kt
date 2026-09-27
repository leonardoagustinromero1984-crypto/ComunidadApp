package com.comunidapp.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.comunidapp.app.data.mock.MockAuthDatabase
import com.comunidapp.app.data.mock.MockData
import com.comunidapp.app.data.model.PetReminder
import com.comunidapp.app.data.model.SterilizationStatus
import com.comunidapp.app.data.model.VaccinationRecord
import com.comunidapp.app.data.remote.supabase.canonical.withCanonicalHealth
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

    @Test
    fun openDetail_resolvesPrincipalDisplayName_notRawId() = runTest {
        login()
        val userRepo = FakeStage5UserRepository(
            usersById = mapOf(
                "user_1" to MockData.currentUser.copy(id = "user_1", name = "María González")
            )
        )
        petRepo.accessResult = Result.success(stage5AccessContext(principalPersonId = "user_1"))
        val viewModel = PetDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("petId" to "pet-1")),
            authRepository = authRepo,
            petRepository = petRepo,
            platformRepository = MockPlatformRepository(),
            userRepository = userRepo
        )
        advanceUntilIdle()

        assertEquals("María González", viewModel.principalDisplayName.value)
        assertFalse(viewModel.principalLoading.value)
        assertTrue(userRepo.getUserCalls >= 1)
        assertFalse(viewModel.principalDisplayName.value.orEmpty().contains("user_1"))
    }

    @Test
    fun realCanonGetPetHealthJson_reachesDetailPresentationData() = runTest {
        login()
        val resultData = """{"pet_id": "78068b30-03f1-4d41-82bc-81318a143471", "weights": [], "allergies": [], "conditions": [], "medications": [], "vaccinations": [{"id": "ff141e70-833d-4579-9a4a-6337f787bd66", "source": "DECLARED", "created_at": "2026-08-26T17:11:28.79022+00:00", "vaccine_name": "Rabia", "administered_on": "2026-08-11"}], "declared_notes": null, "last_vet_visit": "2026-08-07", "care_instructions": null, "sterilized_status": "YES", "parasite_treatments": [{"id": "11035b0b-c4c6-402d-bca3-532e59d97a29", "kind": "DEWORMING", "source": "DECLARED", "created_at": "2026-08-26T17:11:29.111477+00:00", "treated_on": "2026-08-11", "product_name": "Piperazina"}, {"id": "b0526829-d918-495e-a75d-32d7bd78ab19", "kind": "ANTIPARASITIC", "source": "DECLARED", "created_at": "2026-08-26T17:11:29.515502+00:00", "treated_on": "2026-08-05", "product_name": "Permetrina (spray)"}]}"""
        val element = kotlinx.serialization.json.Json.parseToJsonElement(resultData)
        val dto = com.comunidapp.app.data.remote.supabase.canonical.CanonicalPetHealthParser.parse(element)
        val domain = with(com.comunidapp.app.data.remote.supabase.m08.PetM08Mappers) {
            com.comunidapp.app.data.remote.supabase.m08.PetM08Row(
                id = "78068b30-03f1-4d41-82bc-81318a143471",
                name = "Samu",
                species = "DOG",
                sex = "MALE",
                size = "MEDIUM"
            ).withCanonicalHealth(dto).toPet()
        }
        petRepo.pet = domain
        val viewModel = PetDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("petId" to domain.id)),
            authRepository = authRepo,
            petRepository = petRepo,
            platformRepository = MockPlatformRepository()
        )
        advanceUntilIdle()
        val loaded = viewModel.pet.value
        assertNotNull(loaded)
        assertEquals(
            com.comunidapp.app.domain.pets.PetHealthViewState.DATA,
            com.comunidapp.app.domain.pets.PetHealthPresentation.state(
                pet = loaded,
                healthLoading = viewModel.isHealthLoading.value,
                healthLoadError = viewModel.healthLoadError.value
            )
        )
        assertEquals("YES", loaded!!.sterilized?.name)
        assertEquals("Rabia", loaded.vaccinations.first().name)
        assertEquals("Piperazina", loaded.dewormingProduct)
        assertEquals("2026-08-07", loaded.lastVetVisit)
    }

    @Test
    fun resumeWithBasicCacheDoesNotDropEnrichedHealth() = runTest {
        login()
        val resultData = """{"pet_id": "78068b30-03f1-4d41-82bc-81318a143471", "weights": [], "allergies": [], "conditions": [], "medications": [], "vaccinations": [{"id": "ff141e70-833d-4579-9a4a-6337f787bd66", "source": "DECLARED", "created_at": "2026-08-26T17:11:28.79022+00:00", "vaccine_name": "Rabia", "administered_on": "2026-08-11"}], "declared_notes": null, "last_vet_visit": "2026-08-07", "care_instructions": null, "sterilized_status": "YES", "parasite_treatments": [{"id": "11035b0b-c4c6-402d-bca3-532e59d97a29", "kind": "DEWORMING", "source": "DECLARED", "created_at": "2026-08-26T17:11:29.111477+00:00", "treated_on": "2026-08-11", "product_name": "Piperazina"}, {"id": "b0526829-d918-495e-a75d-32d7bd78ab19", "kind": "ANTIPARASITIC", "source": "DECLARED", "created_at": "2026-08-26T17:11:29.515502+00:00", "treated_on": "2026-08-05", "product_name": "Permetrina (spray)"}]}"""
        val element = kotlinx.serialization.json.Json.parseToJsonElement(resultData)
        val dto = com.comunidapp.app.data.remote.supabase.canonical.CanonicalPetHealthParser.parse(element)
        val enriched = with(com.comunidapp.app.data.remote.supabase.m08.PetM08Mappers) {
            com.comunidapp.app.data.remote.supabase.m08.PetM08Row(
                id = "78068b30-03f1-4d41-82bc-81318a143471",
                name = "Samu",
                species = "DOG",
                sex = "MALE",
                size = "MEDIUM"
            ).withCanonicalHealth(dto).toPet()
        }
        petRepo.pet = enriched
        val viewModel = PetDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("petId" to enriched.id)),
            authRepository = authRepo,
            petRepository = petRepo,
            platformRepository = MockPlatformRepository()
        )
        advanceUntilIdle()
        petRepo.pet = enriched.copy(
            vaccinations = emptyList(),
            lastDeworming = null,
            dewormingProduct = null,
            lastFleaTreatment = null,
            fleaTreatmentProduct = null,
            lastVetVisit = null,
            sterilized = null,
            healthReadFailed = true
        )
        viewModel.loadPet()
        advanceUntilIdle()
        val loaded = viewModel.pet.value
        assertEquals(
            com.comunidapp.app.domain.pets.PetHealthViewState.DATA,
            com.comunidapp.app.domain.pets.PetHealthPresentation.state(
                pet = loaded,
                healthLoading = viewModel.isHealthLoading.value,
                healthLoadError = viewModel.healthLoadError.value
            )
        )
        assertEquals("YES", loaded?.sterilized?.name)
        assertEquals("Rabia", loaded?.vaccinations?.firstOrNull()?.name)
        assertEquals("2026-08-07", loaded?.lastVetVisit)
    }

    @Test
    fun samuLoadObserveResume_keepsFetchHealthInUiState() = runTest {
        login()
        val resultData = """{"pet_id": "78068b30-03f1-4d41-82bc-81318a143471", "weights": [], "allergies": [], "conditions": [], "medications": [], "vaccinations": [{"id": "ff141e70-833d-4579-9a4a-6337f787bd66", "source": "DECLARED", "created_at": "2026-08-26T17:11:28.79022+00:00", "vaccine_name": "Rabia", "administered_on": "2026-08-11"}], "declared_notes": null, "last_vet_visit": "2026-08-07", "care_instructions": null, "sterilized_status": "YES", "parasite_treatments": [{"id": "11035b0b-c4c6-402d-bca3-532e59d97a29", "kind": "DEWORMING", "source": "DECLARED", "created_at": "2026-08-26T17:11:29.111477+00:00", "treated_on": "2026-08-11", "product_name": "Piperazina"}, {"id": "b0526829-d918-495e-a75d-32d7bd78ab19", "kind": "ANTIPARASITIC", "source": "DECLARED", "created_at": "2026-08-26T17:11:29.515502+00:00", "treated_on": "2026-08-05", "product_name": "Permetrina (spray)"}]}"""
        val element = kotlinx.serialization.json.Json.parseToJsonElement(resultData)
        val dto = com.comunidapp.app.data.remote.supabase.canonical.CanonicalPetHealthParser.parse(element)
        val afterFetch = with(com.comunidapp.app.data.remote.supabase.m08.PetM08Mappers) {
            com.comunidapp.app.data.remote.supabase.m08.PetM08Row(
                id = "78068b30-03f1-4d41-82bc-81318a143471",
                name = "Samu",
                species = "DOG",
                sex = "MALE",
                size = "MEDIUM"
            ).withCanonicalHealth(dto).toPet()
        }
        val listCache = afterFetch.copy(
            vaccinations = emptyList(),
            sterilized = null,
            lastVetVisit = null,
            lastDeworming = null,
            dewormingProduct = null,
            lastFleaTreatment = null,
            fleaTreatmentProduct = null,
            healthReadFailed = false
        )
        petRepo.pet = afterFetch
        petRepo.listCachePet = listCache
        val viewModel = PetDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("petId" to afterFetch.id)),
            authRepository = authRepo,
            petRepository = petRepo,
            platformRepository = MockPlatformRepository()
        )
        advanceUntilIdle()
        viewModel.loadPet()
        advanceUntilIdle()
        val a = afterFetch
        val b = viewModel.pet.value
        assertEquals("YES", a.sterilized?.name)
        assertEquals(1, a.vaccinations.size)
        assertEquals("Piperazina", a.dewormingProduct)
        assertEquals("Permetrina (spray)", a.fleaTreatmentProduct)
        assertEquals("2026-08-07", a.lastVetVisit)
        assertFalse(a.healthReadFailed)
        assertEquals("YES", b?.sterilized?.name)
        assertEquals(1, b?.vaccinations?.size)
        assertEquals("Piperazina", b?.dewormingProduct)
        assertEquals("Permetrina (spray)", b?.fleaTreatmentProduct)
        assertEquals("2026-08-07", b?.lastVetVisit)
        assertFalse(b?.healthReadFailed == true)
        assertEquals(
            com.comunidapp.app.domain.pets.PetHealthViewState.DATA,
            com.comunidapp.app.domain.pets.PetHealthPresentation.state(
                pet = b,
                healthLoading = viewModel.isHealthLoading.value,
                healthLoadError = viewModel.healthLoadError.value
            )
        )
        assertFalse(
            com.comunidapp.app.domain.pets.PetHealthPresentation.state(
                pet = b,
                healthLoading = viewModel.isHealthLoading.value,
                healthLoadError = viewModel.healthLoadError.value
            ) == com.comunidapp.app.domain.pets.PetHealthViewState.EMPTY
        )
    }

    @Test
    fun openDetail_canRestore_whenArchivedAndCapabilityPresent() = runTest {
        login()
        petRepo.pet = stage5Pet(status = "ARCHIVED")
        petRepo.accessResult = Result.success(
            stage5AccessContext(canRead = true).copy(canRestore = true)
        )
        val viewModel = vm()
        advanceUntilIdle()
        assertTrue(viewModel.canRestore.value)
    }
}
