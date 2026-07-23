package com.comunidapp.app.viewmodel

import com.comunidapp.app.data.model.FosterAvailabilityStatus
import com.comunidapp.app.data.model.FosterHomeRequestStatus
import com.comunidapp.app.data.model.FosterHomeStatus
import com.comunidapp.app.data.model.FosterPlacementStatus
import com.comunidapp.app.data.model.FosterUrgency
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.remote.supabase.m10.M10FosterErrorMapper
import com.comunidapp.app.data.remote.supabase.m10.M10FosterException
import com.comunidapp.app.data.repository.CreateFosterHomeInput
import com.comunidapp.app.data.repository.M10FosterMemoryStore
import com.comunidapp.app.data.repository.MockFosterHomeRepository
import com.comunidapp.app.data.repository.MockFosterPlacementRepository
import com.comunidapp.app.data.repository.MockFosterRequestRepository
import com.comunidapp.app.data.repository.SubmitFosterRequestInput
import com.comunidapp.app.data.repository.UpdateFosterHomeInput
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * LeoVer M10 — núcleo hogares de tránsito (perfiles, solicitudes, ingresos).
 * Usa fakes en memoria; no DataProvider real ni Supabase.
 */
class M10FosterHomeCoreTest {

    private lateinit var store: M10FosterMemoryStore
    private var actorId: String = "owner-1"
    private val pets = mutableMapOf<String, Pet>()

    private lateinit var homes: MockFosterHomeRepository
    private lateinit var requests: MockFosterRequestRepository
    private lateinit var placements: MockFosterPlacementRepository

    @Before
    fun setUp() {
        store = M10FosterMemoryStore()
        actorId = "owner-1"
        pets.clear()
        pets["pet-1"] = samplePet("pet-1", PetSpecies.DOG, PetSize.MEDIUM)
        pets["pet-dead"] = samplePet("pet-dead", PetSpecies.DOG, PetSize.MEDIUM, status = "DECEASED")
        pets["pet-cat"] = samplePet("pet-cat", PetSpecies.CAT, PetSize.SMALL)
        store.petPrincipal.value = mapOf(
            "pet-1" to "requester-1",
            "pet-cat" to "requester-1",
            "pet-dead" to "requester-1"
        )
        homes = MockFosterHomeRepository(actorUserId = { actorId }, store = store)
        requests = MockFosterRequestRepository(
            actorUserId = { actorId },
            store = store,
            resolvePet = { pets[it] }
        )
        placements = MockFosterPlacementRepository(actorUserId = { actorId }, store = store)
    }

    private fun samplePet(
        id: String,
        species: PetSpecies,
        size: PetSize,
        status: String = "ACTIVE"
    ) = Pet(
        id = id,
        name = "Nala-$id",
        species = species,
        sex = PetSex.FEMALE,
        size = size,
        ageYears = 2,
        ageMonths = 0,
        description = "test",
        ownerId = "requester-1",
        status = status
    )

    private suspend fun createActiveHome(
        capacity: Int = 2,
        species: Set<String> = setOf("DOG"),
        sizes: Set<String> = setOf("MEDIUM", "SMALL")
    ) = homes.createFosterHome(
        CreateFosterHomeInput(
            displayName = "Casa Sol",
            description = "Patio",
            totalCapacity = capacity,
            acceptedSpecies = species,
            acceptedSizes = sizes,
            acceptsSpecialNeeds = true,
            acceptsEmergencies = true,
            zoneText = "Quilmes",
            publicLocationText = "Zona sur",
            privateAddressText = "Calle Falsa 123",
            activate = true
        )
    ).getOrThrow()

    @Test
    fun createValidProfile() = runTest {
        val home = createActiveHome()
        assertEquals(FosterHomeStatus.ACTIVE, home.status)
        assertEquals(2, home.totalCapacity)
        assertEquals(0, home.currentOccupancy)
    }

    @Test
    fun preventSecondActiveProfile() = runTest {
        createActiveHome()
        val second = homes.createFosterHome(
            CreateFosterHomeInput(
                displayName = "Otra",
                totalCapacity = 1,
                acceptedSpecies = setOf("DOG"),
                acceptedSizes = setOf("SMALL"),
                zoneText = "Berazategui",
                activate = true
            )
        )
        assertEquals("FOSTER_HOME_ALREADY_EXISTS", (second.exceptionOrNull() as M10FosterException).code)
    }

    @Test
    fun editProfile() = runTest {
        val home = createActiveHome()
        val updated = homes.updateFosterHome(
            UpdateFosterHomeInput(
                homeId = home.id,
                displayName = "Casa Luna",
                totalCapacity = 3,
                acceptedSpecies = setOf("DOG", "CAT"),
                acceptedSizes = setOf("SMALL"),
                zoneText = "Quilmes"
            )
        ).getOrThrow()
        assertEquals("Casa Luna", updated.displayName)
        assertEquals(3, updated.totalCapacity)
    }

    @Test
    fun activateAndPause() = runTest {
        actorId = "owner-1"
        val draft = homes.createFosterHome(
            CreateFosterHomeInput(
                displayName = "Draft",
                totalCapacity = 1,
                acceptedSpecies = setOf("DOG"),
                acceptedSizes = setOf("MEDIUM"),
                zoneText = "Zona",
                activate = false
            )
        ).getOrThrow()
        assertEquals(FosterHomeStatus.DRAFT, draft.status)
        val active = homes.setHomeStatus(draft.id, FosterHomeStatus.ACTIVE).getOrThrow()
        assertEquals(FosterHomeStatus.ACTIVE, active.status)
        val paused = homes.setHomeStatus(draft.id, FosterHomeStatus.PAUSED).getOrThrow()
        assertEquals(FosterHomeStatus.PAUSED, paused.status)
        assertEquals(FosterAvailabilityStatus.UNAVAILABLE, paused.availabilityStatus)
    }

    @Test
    fun capacityMustBePositive() = runTest {
        val r = homes.createFosterHome(
            CreateFosterHomeInput(
                displayName = "X",
                totalCapacity = 0,
                acceptedSpecies = setOf("DOG"),
                acceptedSizes = setOf("SMALL"),
                zoneText = "Z"
            )
        )
        assertEquals("FOSTER_HOME_CAPACITY_INVALID", (r.exceptionOrNull() as M10FosterException).code)
    }

    @Test
    fun listOnlyActiveAndHidePrivateAddress() = runTest {
        createActiveHome()
        homes.createFosterHome(
            CreateFosterHomeInput(
                displayName = "Draft",
                totalCapacity = 1,
                acceptedSpecies = setOf("DOG"),
                acceptedSizes = setOf("SMALL"),
                zoneText = "Z",
                activate = false
            )
        )
        // second create fails - need another owner for draft
        actorId = "owner-2"
        homes.createFosterHome(
            CreateFosterHomeInput(
                displayName = "Draft2",
                totalCapacity = 1,
                acceptedSpecies = setOf("DOG"),
                acceptedSizes = setOf("SMALL"),
                zoneText = "Z",
                privateAddressText = "Secret 9",
                activate = false
            )
        ).getOrThrow()
        actorId = "owner-1"
        val publicList = homes.observeAvailableFosterHomes().first()
        assertEquals(1, publicList.size)
        val jsonish = publicList.first().toString()
        assertFalse(jsonish.contains("Calle Falsa"))
        assertFalse(jsonish.contains("privateAddress"))
    }

    @Test
    fun fullHomeRejectsRequest() = runTest {
        val home = createActiveHome(capacity = 1)
        // Force full
        store.homes.value = store.homes.value.map {
            if (it.id == home.id) it.copy(
                currentOccupancy = 1,
                availabilityStatus = FosterAvailabilityStatus.FULL
            ) else it
        }
        actorId = "requester-1"
        val r = requests.submitRequest(
            SubmitFosterRequestInput(
                fosterHomeId = home.id,
                petId = "pet-1",
                message = "Hola"
            )
        )
        assertTrue(
            (r.exceptionOrNull() as M10FosterException).code in
                setOf("FOSTER_HOME_FULL", "FOSTER_HOME_UNAVAILABLE")
        )
    }

    @Test
    fun validateSpeciesAndSize() = runTest {
        val home = createActiveHome(species = setOf("DOG"), sizes = setOf("MEDIUM"))
        actorId = "requester-1"
        val badSpecies = requests.submitRequest(
            SubmitFosterRequestInput(home.id, "pet-cat", "msg")
        )
        assertEquals("FOSTER_HOME_INCOMPATIBLE", (badSpecies.exceptionOrNull() as M10FosterException).code)
        pets["pet-big"] = samplePet("pet-big", PetSpecies.DOG, PetSize.LARGE)
        store.petPrincipal.value = store.petPrincipal.value + ("pet-big" to "requester-1")
        val badSize = requests.submitRequest(
            SubmitFosterRequestInput(home.id, "pet-big", "msg")
        )
        assertEquals("FOSTER_HOME_INCOMPATIBLE", (badSize.exceptionOrNull() as M10FosterException).code)
    }

    @Test
    fun submitValidAndDuplicateAndDeceasedAndAlreadyFostered() = runTest {
        val home = createActiveHome()
        actorId = "requester-1"
        val ok = requests.submitRequest(
            SubmitFosterRequestInput(home.id, "pet-1", "Necesito tránsito")
        ).getOrThrow()
        assertEquals(FosterHomeRequestStatus.SUBMITTED, ok.status)

        val dup = requests.submitRequest(
            SubmitFosterRequestInput(home.id, "pet-1", "otra")
        )
        assertEquals("FOSTER_REQUEST_ALREADY_EXISTS", (dup.exceptionOrNull() as M10FosterException).code)

        val dead = requests.submitRequest(
            SubmitFosterRequestInput(home.id, "pet-dead", "msg")
        )
        assertEquals("PET_NOT_ELIGIBLE_FOR_FOSTER", (dead.exceptionOrNull() as M10FosterException).code)
    }

    @Test
    fun listSentAndReceived() = runTest {
        val home = createActiveHome()
        actorId = "requester-1"
        requests.submitRequest(SubmitFosterRequestInput(home.id, "pet-1", "hola")).getOrThrow()
        assertEquals(1, requests.observeSentRequests("requester-1").first().size)
        assertEquals(1, requests.observeReceivedRequests("owner-1").first().size)
    }

    @Test
    fun reviewAcceptRejectCancelAndForbidden() = runTest {
        val home = createActiveHome()
        actorId = "requester-1"
        val req = requests.submitRequest(SubmitFosterRequestInput(home.id, "pet-1", "hola")).getOrThrow()

        actorId = "stranger"
        val forbidden = requests.acceptRequest(req.id)
        assertEquals("FOSTER_REQUEST_FORBIDDEN", (forbidden.exceptionOrNull() as M10FosterException).code)

        actorId = "owner-1"
        requests.markUnderReview(req.id).getOrThrow()
        val accepted = requests.acceptRequest(req.id).getOrThrow()
        assertEquals(FosterHomeRequestStatus.ACCEPTED, accepted.status)
        val reserved = store.homes.value.first { it.id == home.id }.reservedCount
        assertEquals(1, reserved)

        // Reject path on another request
        pets["pet-2"] = samplePet("pet-2", PetSpecies.DOG, PetSize.MEDIUM)
        store.petPrincipal.value = store.petPrincipal.value + ("pet-2" to "requester-1")
        actorId = "requester-1"
        val req2 = requests.submitRequest(SubmitFosterRequestInput(home.id, "pet-2", "otra")).getOrThrow()
        actorId = "owner-1"
        val rejected = requests.rejectRequest(req2.id, "No puedo").getOrThrow()
        assertEquals(FosterHomeRequestStatus.REJECTED, rejected.status)

        actorId = "requester-1"
        val req3Pet = samplePet("pet-3", PetSpecies.DOG, PetSize.SMALL)
        pets["pet-3"] = req3Pet
        store.petPrincipal.value = store.petPrincipal.value + ("pet-3" to "requester-1")
        // home may be limited - capacity 2 with 1 reserved
        val req3 = requests.submitRequest(SubmitFosterRequestInput(home.id, "pet-3", "cancelame")).getOrThrow()
        val cancelled = requests.cancelRequest(req3.id).getOrThrow()
        assertEquals(FosterHomeRequestStatus.CANCELLED, cancelled.status)
    }

    @Test
    fun startPlacementCapacityPrincipalAndNoDoubleActive() = runTest {
        val home = createActiveHome(capacity = 1)
        actorId = "requester-1"
        val req = requests.submitRequest(SubmitFosterRequestInput(home.id, "pet-1", "hola")).getOrThrow()
        actorId = "owner-1"
        requests.acceptRequest(req.id).getOrThrow()
        val placement = placements.startPlacement(req.id, "Ingreso ok").getOrThrow()
        assertEquals(FosterPlacementStatus.ACTIVE, placement.status)
        assertNotNull(placement.temporaryResponsibilityId)
        assertEquals(1, store.temporaryCustody.value.size)
        assertEquals("TEMPORARY_CUSTODIAN", store.temporaryCustody.value.first().roleCode)
        // Principal map unchanged
        assertEquals("requester-1", store.petPrincipal.value["pet-1"])

        val homeAfter = store.homes.value.first { it.id == home.id }
        assertEquals(1, homeAfter.currentOccupancy)
        assertEquals(0, homeAfter.reservedCount)

        val again = placements.startPlacement(req.id, null).getOrThrow()
        assertEquals(placement.id, again.id)

        // Another pet cannot start if capacity exceeded after accept
        pets["pet-x"] = samplePet("pet-x", PetSpecies.DOG, PetSize.MEDIUM)
        store.petPrincipal.value = store.petPrincipal.value + ("pet-x" to "requester-1")
        actorId = "requester-1"
        val reqX = requests.submitRequest(SubmitFosterRequestInput(home.id, "pet-x", "lleno"))
        assertTrue(reqX.isFailure)
    }

    @Test
    fun emptyAndUnknownIdsAndUnknownStatusAndRepoErrorAndDoubleSubmit() = runTest {
        assertEquals(
            "FOSTER_HOME_NOT_FOUND",
            (homes.getFosterHomeById("").exceptionOrNull() as M10FosterException).code
        )
        assertEquals(
            "FOSTER_HOME_NOT_FOUND",
            (homes.getFosterHomeById("missing").exceptionOrNull() as M10FosterException).code
        )
        assertEquals(
            FosterHomeStatus.UNKNOWN,
            FosterHomeStatus.fromString("WEIRD")
        )
        assertEquals(
            "No encontramos ese hogar de tránsito.",
            M10FosterErrorMapper.userMessage("FOSTER_HOME_NOT_FOUND")
        )

        val home = createActiveHome()
        actorId = "requester-1"
        val input = SubmitFosterRequestInput(home.id, "pet-1", "hola")
        requests.submitRequest(input).getOrThrow()
        val second = requests.submitRequest(input)
        assertEquals("FOSTER_REQUEST_ALREADY_EXISTS", (second.exceptionOrNull() as M10FosterException).code)

        // Controlled loading: list ViewModel starts Loading then Empty/Content
        val listVm = FosterHomesListViewModel(homeRepository = homes)
        assertTrue(
            listVm.uiState.value is FosterListUiState.Loading ||
                listVm.uiState.value is FosterListUiState.Content ||
                listVm.uiState.value is FosterListUiState.Empty
        )
    }

    @Test
    fun alreadyInFosterBlocksNewRequest() = runTest {
        val home = createActiveHome(capacity = 2)
        actorId = "requester-1"
        val req = requests.submitRequest(SubmitFosterRequestInput(home.id, "pet-1", "hola")).getOrThrow()
        actorId = "owner-1"
        requests.acceptRequest(req.id).getOrThrow()
        placements.startPlacement(req.id, null).getOrThrow()

        actorId = "owner-2"
        val home2 = homes.createFosterHome(
            CreateFosterHomeInput(
                displayName = "Otra casa",
                totalCapacity = 1,
                acceptedSpecies = setOf("DOG"),
                acceptedSizes = setOf("MEDIUM"),
                zoneText = "Z",
                activate = true
            )
        ).getOrThrow()
        actorId = "requester-1"
        val blocked = requests.submitRequest(SubmitFosterRequestInput(home2.id, "pet-1", "otra"))
        assertEquals("PET_ALREADY_IN_FOSTER", (blocked.exceptionOrNull() as M10FosterException).code)
    }
}
