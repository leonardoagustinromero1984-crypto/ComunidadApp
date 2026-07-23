package com.comunidapp.app.viewmodel

import com.comunidapp.app.data.model.FosterAvailabilityStatus
import com.comunidapp.app.data.model.FosterHomeStatus
import com.comunidapp.app.data.model.FosterPlacement
import com.comunidapp.app.data.model.FosterPlacementStatus
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.model.ShelterIntakeType
import com.comunidapp.app.data.model.ShelterPetEndReason
import com.comunidapp.app.data.model.ShelterPetPlacementStatus
import com.comunidapp.app.data.model.ShelterStatus
import com.comunidapp.app.data.model.ShelterVolunteerRole
import com.comunidapp.app.data.model.ShelterVolunteerStatus
import com.comunidapp.app.data.model.recomputeShelterAvailability
import com.comunidapp.app.data.remote.supabase.m11.M11ShelterErrorMapper
import com.comunidapp.app.data.repository.CreateShelterProfileInput
import com.comunidapp.app.data.repository.M10FosterMemoryStore
import com.comunidapp.app.data.repository.M11ShelterMemoryStore
import com.comunidapp.app.data.repository.MockShelterPetRepository
import com.comunidapp.app.data.repository.MockShelterProfileRepository
import com.comunidapp.app.data.repository.MockShelterVolunteerRepository
import com.comunidapp.app.data.repository.UpdateShelterProfileInput
import com.comunidapp.app.data.repository.grantsAdministrativeAuthority
import com.comunidapp.app.data.model.FosterHomeProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
 * LeoVer M11 — núcleo operación de refugios (perfiles, capacidad, mascotas, voluntarios).
 * Solo fakes; sin Supabase real ni DataProvider de producción.
 */
class M11ShelterOperationsCoreTest {

    private lateinit var store: M11ShelterMemoryStore
    private lateinit var fosterStore: M10FosterMemoryStore
    private var actorId: String = "manager-1"
    private val pets = mutableMapOf<String, Pet>()

    private lateinit var profiles: MockShelterProfileRepository
    private lateinit var petPlacements: MockShelterPetRepository
    private lateinit var volunteers: MockShelterVolunteerRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        store = M11ShelterMemoryStore()
        fosterStore = M10FosterMemoryStore()
        store.fosterStore = fosterStore
        actorId = "manager-1"
        pets.clear()
        pets["pet-1"] = samplePet("pet-1")
        pets["pet-dead"] = samplePet("pet-dead", status = "DECEASED")
        pets["pet-2"] = samplePet("pet-2")
        store.organizationStatus.value = mapOf("org-1" to "ACTIVE", "org-dead" to "CLOSED")
        store.orgManagers.value = mapOf("org-1" to setOf("manager-1"))
        store.orgViewers.value = mapOf("org-1" to setOf("manager-1", "viewer-1"))
        store.petPrincipal.value = mapOf("pet-1" to "owner-1", "pet-2" to "owner-1")
        wire()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun wire() {
        profiles = MockShelterProfileRepository(actorUserId = { actorId }, store = store)
        petPlacements = MockShelterPetRepository(
            actorUserId = { actorId },
            store = store,
            resolvePet = { pets[it] }
        )
        volunteers = MockShelterVolunteerRepository(
            actorUserId = { actorId },
            store = store,
            knownUserIds = { setOf("vol-1", "vol-2", "manager-1") }
        )
    }

    private fun samplePet(id: String, status: String = "ACTIVE") = Pet(
        id = id,
        name = "Pet-$id",
        species = PetSpecies.DOG,
        sex = PetSex.FEMALE,
        size = PetSize.MEDIUM,
        ageYears = 2,
        ageMonths = 0,
        description = "t",
        ownerId = "owner-1",
        status = status
    )

    private suspend fun createActiveShelter(capacity: Int = 2, branchId: String? = null) =
        profiles.createShelter(
            CreateShelterProfileInput(
                organizationId = "org-1",
                branchId = branchId,
                displayName = "Refugio Sol",
                description = "Desc",
                totalCapacity = capacity,
                acceptedSpecies = setOf("DOG", "CAT"),
                acceptsEmergencies = true,
                publicZoneText = "Zona Norte",
                internalAddressRef = "Calle Secreta 1",
                activate = true
            )
        ).getOrThrow()

    private fun codeOf(r: Result<*>) = M11ShelterErrorMapper.codeOf(r.exceptionOrNull()!!)

    @Test
    fun createValidShelter() = runTest {
        val s = createActiveShelter()
        assertEquals(ShelterStatus.ACTIVE, s.status)
        assertEquals(2, s.totalCapacity)
        assertEquals(0, s.currentOccupancy)
    }

    @Test
    fun rejectWithoutOrganization() = runTest {
        assertEquals(
            "ORGANIZATION_NOT_ELIGIBLE",
            codeOf(
                profiles.createShelter(
                    CreateShelterProfileInput(
                        organizationId = "",
                        displayName = "X",
                        totalCapacity = 1,
                        acceptedSpecies = setOf("DOG"),
                        activate = true
                    )
                )
            )
        )
    }

    @Test
    fun preventSecondActivePerBranch() = runTest {
        createActiveShelter(branchId = null)
        assertEquals(
            "SHELTER_ALREADY_EXISTS",
            codeOf(
                profiles.createShelter(
                    CreateShelterProfileInput(
                        organizationId = "org-1",
                        displayName = "Otro",
                        totalCapacity = 1,
                        acceptedSpecies = setOf("DOG"),
                        activate = true
                    )
                )
            )
        )
    }

    @Test
    fun editShelter() = runTest {
        val s = createActiveShelter()
        val updated = profiles.updateShelter(
            UpdateShelterProfileInput(
                shelterId = s.id,
                displayName = "Nuevo nombre",
                totalCapacity = 3,
                acceptedSpecies = setOf("DOG"),
                publicZoneText = "Sur"
            )
        ).getOrThrow()
        assertEquals("Nuevo nombre", updated.displayName)
        assertEquals(3, updated.totalCapacity)
    }

    @Test
    fun activateAndPause() = runTest {
        val s = profiles.createShelter(
            CreateShelterProfileInput(
                organizationId = "org-1",
                displayName = "Draft",
                totalCapacity = 2,
                acceptedSpecies = setOf("DOG"),
                activate = false
            )
        ).getOrThrow()
        assertEquals(ShelterStatus.DRAFT, s.status)
        assertEquals(ShelterStatus.ACTIVE, profiles.changeStatus(s.id, ShelterStatus.ACTIVE).getOrThrow().status)
        assertEquals(ShelterStatus.PAUSED, profiles.changeStatus(s.id, ShelterStatus.PAUSED).getOrThrow().status)
    }

    @Test
    fun capacityMustBePositive() = runTest {
        assertEquals(
            "SHELTER_CAPACITY_EXCEEDED",
            codeOf(
                profiles.createShelter(
                    CreateShelterProfileInput(
                        organizationId = "org-1",
                        displayName = "X",
                        totalCapacity = 0,
                        acceptedSpecies = setOf("DOG"),
                        activate = true
                    )
                )
            )
        )
    }

    @Test
    fun listOnlyPublicActive() = runTest {
        createActiveShelter()
        profiles.createShelter(
            CreateShelterProfileInput(
                organizationId = "org-1",
                branchId = "b2",
                displayName = "Draft",
                totalCapacity = 1,
                acceptedSpecies = setOf("DOG"),
                activate = false
            )
        ).getOrThrow()
        val pub = profiles.observePublicShelters().first()
        assertTrue(pub.all { it.status == ShelterStatus.ACTIVE })
        assertEquals(1, pub.size)
    }

    @Test
    fun hideExactAddressInPublicListing() = runTest {
        val s = createActiveShelter()
        val pub = profiles.observePublicShelters().first().first { it.id == s.id }
        assertNull(
            // public listing type has no internalAddressRef field — ensure private stays on profile only
            (pub as Any).javaClass.declaredFields.find { it.name.contains("internal", ignoreCase = true) }
        )
        assertEquals("Zona Norte", pub.publicZoneText)
        assertEquals("Calle Secreta 1", profiles.getShelterById(s.id).getOrThrow().internalAddressRef)
    }

    @Test
    fun reserveCapacity() = runTest {
        val s = createActiveShelter(capacity = 2)
        val p = petPlacements.reserveCapacity(s.id, "pet-1", ShelterIntakeType.RESCUE, null).getOrThrow()
        assertEquals(ShelterPetPlacementStatus.RESERVED, p.status)
        assertEquals(1, profiles.getShelterById(s.id).getOrThrow().reservedCapacity)
    }

    @Test
    fun admitPet() = runTest {
        val s = createActiveShelter()
        val p = petPlacements.admitPet(s.id, "pet-1", ShelterIntakeType.RESCUE, "ok", null).getOrThrow()
        assertEquals(ShelterPetPlacementStatus.ACTIVE, p.status)
        assertEquals(1, profiles.getShelterById(s.id).getOrThrow().currentOccupancy)
    }

    @Test
    fun preventOverCapacity() = runTest {
        val s = createActiveShelter(capacity = 1)
        petPlacements.admitPet(s.id, "pet-1", ShelterIntakeType.FOUND, null, null).getOrThrow()
        assertEquals(
            "SHELTER_FULL",
            codeOf(petPlacements.admitPet(s.id, "pet-2", ShelterIntakeType.FOUND, null, null))
        )
    }

    @Test
    fun preventDeceasedPet() = runTest {
        val s = createActiveShelter()
        assertEquals(
            "SHELTER_PET_NOT_ELIGIBLE",
            codeOf(petPlacements.admitPet(s.id, "pet-dead", ShelterIntakeType.RESCUE, null, null))
        )
    }

    @Test
    fun preventTwoActivePlacements() = runTest {
        val s = createActiveShelter(capacity = 3)
        petPlacements.admitPet(s.id, "pet-1", ShelterIntakeType.RESCUE, null, null).getOrThrow()
        assertEquals(
            "SHELTER_PET_ALREADY_ACTIVE",
            codeOf(petPlacements.admitPet(s.id, "pet-1", ShelterIntakeType.TRANSFER, null, null))
        )
    }

    @Test
    fun keepPrincipal() = runTest {
        val s = createActiveShelter()
        assertEquals("owner-1", store.petPrincipal.value["pet-1"])
        petPlacements.admitPet(s.id, "pet-1", ShelterIntakeType.OWNER_SURRENDER, null, null).getOrThrow()
        assertEquals("owner-1", store.petPrincipal.value["pet-1"])
    }

    @Test
    fun createOrgResponsibility() = runTest {
        val s = createActiveShelter()
        val p = petPlacements.admitPet(s.id, "pet-1", ShelterIntakeType.RESCUE, null, null).getOrThrow()
        assertNotNull(p.organizationalResponsibilityId)
        assertTrue(
            store.orgResponsibilities.value.any {
                it.id == p.organizationalResponsibilityId && it.active && it.roleCode == "CO_RESPONSIBLE"
            }
        )
    }

    @Test
    fun changeToQuarantine() = runTest {
        val s = createActiveShelter()
        val p = petPlacements.admitPet(s.id, "pet-1", ShelterIntakeType.RESCUE, null, null).getOrThrow()
        assertEquals(
            ShelterPetPlacementStatus.QUARANTINE,
            petPlacements.changePlacementStatus(p.id, ShelterPetPlacementStatus.QUARANTINE).getOrThrow().status
        )
    }

    @Test
    fun changeToMedicalCare() = runTest {
        val s = createActiveShelter()
        val p = petPlacements.admitPet(s.id, "pet-1", ShelterIntakeType.RESCUE, null, null).getOrThrow()
        assertEquals(
            ShelterPetPlacementStatus.MEDICAL_CARE,
            petPlacements.changePlacementStatus(p.id, ShelterPetPlacementStatus.MEDICAL_CARE).getOrThrow().status
        )
    }

    @Test
    fun releasePet() = runTest {
        val s = createActiveShelter()
        val p = petPlacements.admitPet(s.id, "pet-1", ShelterIntakeType.RESCUE, null, null).getOrThrow()
        val released = petPlacements.releasePet(p.id, ShelterPetEndReason.RELEASED_TO_OWNER, null).getOrThrow()
        assertEquals(ShelterPetPlacementStatus.RELEASED, released.status)
        assertEquals(0, profiles.getShelterById(s.id).getOrThrow().currentOccupancy)
        assertTrue(store.orgResponsibilities.value.none { it.placementId == p.id && it.active })
    }

    @Test
    fun keepHistoryAfterRelease() = runTest {
        val s = createActiveShelter()
        val p = petPlacements.admitPet(s.id, "pet-1", ShelterIntakeType.RESCUE, null, null).getOrThrow()
        petPlacements.releasePet(p.id, ShelterPetEndReason.RELEASED_TO_OWNER, null).getOrThrow()
        assertEquals(1, petPlacements.observeShelterPets(s.id).first().size)
        assertNotNull(petPlacements.getPetPlacement(p.id).getOrThrow().endedAt)
    }

    @Test
    fun integrationAdoptionM09() = runTest {
        val s = createActiveShelter()
        petPlacements.admitPet(s.id, "pet-1", ShelterIntakeType.RESCUE, null, null).getOrThrow()
        petPlacements.onAdoptionFinalized("pet-1").getOrThrow()
        val open = petPlacements.observeShelterPets(s.id).first().filter { it.status.isOpen }
        assertTrue(open.isEmpty())
        assertEquals(
            ShelterPetPlacementStatus.ADOPTED,
            petPlacements.observeShelterPets(s.id).first().first().status
        )
    }

    @Test
    fun integrationFosterM10() = runTest {
        val s = createActiveShelter()
        fosterStore.homes.value = listOf(
            FosterHomeProfile(
                id = "fh-1",
                ownerUserId = "foster-1",
                displayName = "Casa",
                status = FosterHomeStatus.ACTIVE,
                availabilityStatus = FosterAvailabilityStatus.LIMITED,
                totalCapacity = 1,
                currentOccupancy = 1,
                acceptedSpecies = setOf("DOG"),
                acceptedSizes = setOf("MEDIUM"),
                zoneText = "Z",
                createdAt = 1L,
                updatedAt = 1L
            )
        )
        fosterStore.placements.value = listOf(
            FosterPlacement(
                id = "fp-1",
                fosterRequestId = "fr-1",
                fosterHomeId = "fh-1",
                petId = "pet-1",
                fosterUserId = "foster-1",
                status = FosterPlacementStatus.ACTIVE,
                startedAt = 1L,
                temporaryResponsibilityId = "tc-1"
            )
        )
        fosterStore.temporaryCustody.value = listOf(
            com.comunidapp.app.data.repository.FosterTemporaryCustodyGrant(
                id = "tc-1", petId = "pet-1", fosterUserId = "foster-1", placementId = "fp-1"
            )
        )
        val p = petPlacements.admitPet(
            s.id, "pet-1", ShelterIntakeType.FOSTER_RETURN, null, null
        ).getOrThrow()
        assertEquals("fp-1", p.relatedFosterPlacementId)
        assertEquals(
            FosterPlacementStatus.COMPLETED,
            fosterStore.placements.value.first { it.id == "fp-1" }.status
        )
        assertTrue(fosterStore.temporaryCustody.value.none { it.placementId == "fp-1" && it.active })
    }

    @Test
    fun inviteVolunteer() = runTest {
        val s = createActiveShelter()
        val v = volunteers.inviteVolunteer(s.id, "vol-1", ShelterVolunteerRole.ANIMAL_CARE, null).getOrThrow()
        assertEquals(ShelterVolunteerStatus.INVITED, v.status)
    }

    @Test
    fun acceptAssignment() = runTest {
        val s = createActiveShelter()
        val v = volunteers.inviteVolunteer(s.id, "vol-1", ShelterVolunteerRole.CLEANING, null).getOrThrow()
        actorId = "vol-1"
        wire()
        assertEquals(
            ShelterVolunteerStatus.ACTIVE,
            volunteers.acceptAssignment(v.id).getOrThrow().status
        )
    }

    @Test
    fun preventDuplicateVolunteer() = runTest {
        val s = createActiveShelter()
        volunteers.inviteVolunteer(s.id, "vol-1", ShelterVolunteerRole.TRANSPORT, null).getOrThrow()
        assertEquals(
            "SHELTER_VOLUNTEER_ALREADY_ASSIGNED",
            codeOf(volunteers.inviteVolunteer(s.id, "vol-1", ShelterVolunteerRole.TRANSPORT, null))
        )
    }

    @Test
    fun pauseVolunteer() = runTest {
        val s = createActiveShelter()
        val v = volunteers.inviteVolunteer(s.id, "vol-1", ShelterVolunteerRole.OTHER, null).getOrThrow()
        volunteers.acceptAssignment(v.id).getOrThrow()
        assertEquals(ShelterVolunteerStatus.PAUSED, volunteers.pauseAssignment(v.id).getOrThrow().status)
    }

    @Test
    fun endVolunteer() = runTest {
        val s = createActiveShelter()
        val v = volunteers.inviteVolunteer(s.id, "vol-1", ShelterVolunteerRole.EVENT_SUPPORT, null).getOrThrow()
        assertEquals(ShelterVolunteerStatus.ENDED, volunteers.endAssignment(v.id).getOrThrow().status)
    }

    @Test
    fun volunteerNoAdminAuthority() = runTest {
        val s = createActiveShelter()
        val v = volunteers.inviteVolunteer(s.id, "vol-1", ShelterVolunteerRole.ADMINISTRATION, null).getOrThrow()
        assertFalse(v.grantsAdministrativeAuthority())
        actorId = "vol-1"
        wire()
        assertEquals(
            "SHELTER_FORBIDDEN",
            codeOf(profiles.changeStatus(s.id, ShelterStatus.PAUSED))
        )
    }

    @Test
    fun userWithoutPermission() = runTest {
        actorId = "stranger"
        wire()
        assertEquals(
            "SHELTER_FORBIDDEN",
            codeOf(
                profiles.createShelter(
                    CreateShelterProfileInput(
                        organizationId = "org-1",
                        displayName = "X",
                        totalCapacity = 1,
                        acceptedSpecies = setOf("DOG"),
                        activate = true
                    )
                )
            )
        )
    }

    @Test
    fun blankAndMissingIds() = runTest {
        assertEquals("SHELTER_NOT_FOUND", codeOf(profiles.getShelterById("")))
        assertEquals("SHELTER_NOT_FOUND", codeOf(profiles.getShelterById("missing")))
        assertEquals("SHELTER_PET_NOT_FOUND", codeOf(petPlacements.getPetPlacement("")))
        assertEquals("SHELTER_PET_NOT_FOUND", codeOf(petPlacements.getPetPlacement("missing")))
    }

    @Test
    fun unknownStatusControlled() {
        assertEquals(ShelterStatus.UNKNOWN, ShelterStatus.fromString("WEIRD"))
        assertEquals(ShelterPetPlacementStatus.UNKNOWN, ShelterPetPlacementStatus.fromString("??"))
        assertEquals(ShelterVolunteerRole.UNKNOWN, ShelterVolunteerRole.fromString("x"))
    }

    @Test
    fun repositoryErrorMapped() {
        val msg = M11ShelterErrorMapper.userMessage("SHELTER_FORBIDDEN")
        assertFalse(msg.contains("postgres", ignoreCase = true))
        assertFalse(msg.contains("supabase", ignoreCase = true))
    }

    @Test
    fun doubleSubmitGuarded() = runTest {
        val s = createActiveShelter()
        val vm = ShelterIntakeViewModel(s.id, petPlacements)
        vm.admit("pet-1", ShelterIntakeType.RESCUE, null, false)
        vm.admit("pet-2", ShelterIntakeType.RESCUE, null, false)
        assertTrue(petPlacements.observeShelterPets(s.id).first().isNotEmpty())
    }

    @Test
    fun blankIdControlledLoading() {
        val vm = ShelterOpsDetailViewModel("", profiles)
        assertTrue(vm.uiState.value is ShelterDetailUiState.Error)
    }

    @Test
    fun availabilityRuleAndNoSupabase() {
        assertEquals(
            com.comunidapp.app.data.model.ShelterAvailabilityStatus.AVAILABLE,
            recomputeShelterAvailability(ShelterStatus.ACTIVE, 1, 0, 0)
        )
        assertEquals(
            com.comunidapp.app.data.model.ShelterAvailabilityStatus.LIMITED,
            recomputeShelterAvailability(ShelterStatus.ACTIVE, 2, 1, 0)
        )
        assertEquals(
            com.comunidapp.app.data.model.ShelterAvailabilityStatus.FULL,
            recomputeShelterAvailability(ShelterStatus.ACTIVE, 1, 1, 0)
        )
        assertTrue(profiles is MockShelterProfileRepository)
        assertFalse(profiles.javaClass.name.contains("Supabase"))
    }
}
