package com.comunidapp.app.domain.m16

import com.comunidapp.app.data.model.AdoptionPost
import com.comunidapp.app.data.model.AdoptionStatus
import com.comunidapp.app.data.model.M15FosterPlacement
import com.comunidapp.app.data.model.M15FosterPlacementStatus
import com.comunidapp.app.data.model.M16ShelterCapacity
import com.comunidapp.app.data.model.M16ShelterOperationsPartialFlags
import com.comunidapp.app.data.model.M16ShelterOperationalStatus
import com.comunidapp.app.data.model.M16ShelterPetOperationalStatus
import com.comunidapp.app.data.model.M16ShelterProfile
import com.comunidapp.app.data.model.M16ShelterPublicationStatus
import com.comunidapp.app.data.model.M16ShelterVerificationStatus
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.model.ShelterIntakeType
import com.comunidapp.app.data.model.ShelterPetPlacement
import com.comunidapp.app.data.model.ShelterPetPlacementStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class M16ShelterOperationsServiceTest {

    private val fixedNow = 1_700_000_000_000L
    private val service = M16ShelterOperationsService { fixedNow }

    private fun profile(total: Int = 10, snapshot: Int? = null) = M16ShelterProfile(
        id = "shelter_1",
        organizationId = "org_1",
        displayName = "Test",
        operationalStatus = M16ShelterOperationalStatus.ACTIVE,
        publicationStatus = M16ShelterPublicationStatus.PUBLISHED,
        verificationStatus = M16ShelterVerificationStatus.VERIFIED,
        publicZoneText = "Zona",
        capacity = M16ShelterCapacity(totalCapacity = total, currentOccupancy = snapshot ?: 0),
        createdAt = fixedNow,
        updatedAt = fixedNow
    )

    private fun pet(id: String, status: String = "ACTIVE") = Pet(
        id = id,
        ownerId = null,
        name = id,
        species = PetSpecies.DOG,
        sex = PetSex.MALE,
        ageYears = 2,
        size = PetSize.MEDIUM,
        description = "",
        status = status
    )

    private fun placement(petId: String, status: ShelterPetPlacementStatus) = ShelterPetPlacement(
        id = "plc_$petId",
        shelterProfileId = "m11_1",
        petId = petId,
        intakeType = ShelterIntakeType.RESCUE,
        status = status,
        admittedAt = fixedNow,
        admittedBy = "user"
    )

    private fun adoption(
        petId: String,
        status: AdoptionStatus,
        updatedAt: Long? = fixedNow
    ) = AdoptionPost(
        id = "adopt_$petId",
        petId = petId,
        publisherOrganizationId = "org_1",
        shelterName = "S",
        name = petId,
        species = PetSpecies.DOG,
        sex = PetSex.MALE,
        ageYears = 1,
        size = PetSize.SMALL,
        location = "X",
        description = "",
        status = status,
        updatedAt = updatedAt
    )

    private fun foster(petId: String, status: M15FosterPlacementStatus) = M15FosterPlacement(
        id = "foster_$petId",
        fosterRequestId = "req_$petId",
        fosterHomeId = "home_1",
        petId = petId,
        fosterUserId = "u2",
        requesterOrganizationId = "org_1",
        status = status,
        startedAt = fixedNow
    )

    @Test
    fun reservedNotDoubleCounted_modelA() {
        val summary = service.buildSummary(
            M16ShelterOperationsService.SourceSnapshot(
                profile = profile(total = 10),
                shelterPlacements = listOf(
                    placement("p1", ShelterPetPlacementStatus.ACTIVE),
                    placement("p2", ShelterPetPlacementStatus.RESERVED)
                ),
                petsById = mapOf("p1" to pet("p1"), "p2" to pet("p2"))
            )
        )
        assertEquals(1, summary.breakdown.physicalOccupancy)
        assertEquals(1, summary.breakdown.reservedCapacity)
        assertEquals(2, summary.breakdown.committedCapacity)
        assertEquals(8, summary.breakdown.availableCapacity)
        assertEquals(0, summary.breakdown.overCapacityBy)
    }

    @Test
    fun petIdCountedOnce() {
        val summary = service.buildSummary(
            M16ShelterOperationsService.SourceSnapshot(
                profile = profile(),
                shelterPlacements = listOf(
                    placement("p1", ShelterPetPlacementStatus.ACTIVE),
                    placement("p1", ShelterPetPlacementStatus.QUARANTINE)
                ),
                petsById = mapOf("p1" to pet("p1"))
            )
        )
        assertEquals(1, summary.breakdown.physicalOccupancy)
        assertTrue(summary.breakdown.warnings.any { it.contains("múltiples placements") })
    }

    @Test
    fun overCapacityByPreserved() {
        val summary = service.buildSummary(
            M16ShelterOperationsService.SourceSnapshot(
                profile = profile(total = 4),
                shelterPlacements = listOf(
                    placement("p1", ShelterPetPlacementStatus.ACTIVE),
                    placement("p2", ShelterPetPlacementStatus.ACTIVE),
                    placement("p3", ShelterPetPlacementStatus.ACTIVE),
                    placement("p4", ShelterPetPlacementStatus.ACTIVE),
                    placement("p5", ShelterPetPlacementStatus.ACTIVE),
                    placement("p6", ShelterPetPlacementStatus.RESERVED)
                ),
                petsById = (1..6).associate { "p$it" to pet("p$it") }
            )
        )
        assertEquals(5, summary.breakdown.physicalOccupancy)
        assertEquals(1, summary.breakdown.reservedCapacity)
        assertEquals(6, summary.breakdown.committedCapacity)
        assertEquals(0, summary.breakdown.availableCapacity)
        assertEquals(2, summary.breakdown.overCapacityBy)
        assertTrue(summary.breakdown.isOverCapacity)
    }

    @Test
    fun housedPlusActiveFosterIsInconsistent() {
        val summary = service.buildSummary(
            M16ShelterOperationsService.SourceSnapshot(
                profile = profile(),
                shelterPlacements = listOf(placement("p1", ShelterPetPlacementStatus.ACTIVE)),
                fosterPlacements = listOf(foster("p1", M15FosterPlacementStatus.ACTIVE)),
                petsById = mapOf("p1" to pet("p1"))
            )
        )
        assertEquals(
            M16ShelterPetOperationalStatus.INCONSISTENT,
            summary.pets.single().status
        )
    }

    @Test
    fun closedFosterIgnored() {
        val summary = service.buildSummary(
            M16ShelterOperationsService.SourceSnapshot(
                profile = profile(),
                fosterPlacements = listOf(foster("p1", M15FosterPlacementStatus.COMPLETED)),
                adoptions = listOf(adoption("p1", AdoptionStatus.PUBLISHED)),
                petsById = mapOf("p1" to pet("p1"))
            )
        )
        assertFalse(summary.pets.any { it.status == M16ShelterPetOperationalStatus.IN_ACTIVE_FOSTER })
    }

    @Test
    fun activeAdoptionDoesNotIncreasePhysicalOccupancy() {
        val summary = service.buildSummary(
            M16ShelterOperationsService.SourceSnapshot(
                profile = profile(),
                adoptions = listOf(adoption("p1", AdoptionStatus.PUBLISHED)),
                petsById = mapOf("p1" to pet("p1"))
            )
        )
        assertEquals(0, summary.breakdown.physicalOccupancy)
        assertEquals(1, summary.breakdown.activeAdoptionCount)
    }

    @Test
    fun recentAdoptedWithinWindow() {
        val summary = service.buildSummary(
            M16ShelterOperationsService.SourceSnapshot(
                profile = profile(),
                adoptions = listOf(
                    adoption("p1", AdoptionStatus.ADOPTED, updatedAt = fixedNow - 5 * 86_400_000L)
                ),
                petsById = mapOf("p1" to pet("p1", "ARCHIVED"))
            )
        )
        assertEquals(1, summary.breakdown.recentlyAdoptedCount)
    }

    @Test
    fun oldAdoptedNotRecent() {
        val summary = service.buildSummary(
            M16ShelterOperationsService.SourceSnapshot(
                profile = profile(),
                adoptions = listOf(
                    adoption("p1", AdoptionStatus.ADOPTED, updatedAt = fixedNow - 60 * 86_400_000L)
                ),
                petsById = mapOf("p1" to pet("p1", "ARCHIVED"))
            )
        )
        assertEquals(0, summary.breakdown.recentlyAdoptedCount)
    }

    @Test
    fun adoptedWithoutDateFlagsPartial() {
        val summary = service.buildSummary(
            M16ShelterOperationsService.SourceSnapshot(
                profile = profile(),
                adoptions = listOf(
                    adoption("p1", AdoptionStatus.ADOPTED, updatedAt = null)
                ),
                petsById = mapOf("p1" to pet("p1", "ARCHIVED"))
            )
        )
        assertTrue(summary.partialFlags.adoptionCompletionDatesUnavailable)
    }

    @Test
    fun deceasedAndArchivedDoNotOccupy() {
        val summary = service.buildSummary(
            M16ShelterOperationsService.SourceSnapshot(
                profile = profile(),
                shelterPlacements = listOf(
                    placement("dead", ShelterPetPlacementStatus.ACTIVE),
                    placement("arch", ShelterPetPlacementStatus.ACTIVE)
                ),
                petsById = mapOf(
                    "dead" to pet("dead", "DECEASED"),
                    "arch" to pet("arch", "ARCHIVED")
                )
            )
        )
        assertEquals(0, summary.breakdown.physicalOccupancy)
    }

    @Test
    fun emptyShelterZeroMetricsConfirmed() {
        val summary = service.buildSummary(
            M16ShelterOperationsService.SourceSnapshot(profile = profile(total = 12))
        )
        assertEquals(0, summary.breakdown.physicalOccupancy)
        assertEquals(0, summary.breakdown.reservedCapacity)
        assertEquals(12, summary.breakdown.availableCapacity)
        assertTrue(summary.pets.isEmpty())
    }

    @Test
    fun partialFosterSourceFlag() {
        val summary = service.buildSummary(
            M16ShelterOperationsService.SourceSnapshot(
                profile = profile(),
                partialFlags = M16ShelterOperationsPartialFlags(fosterSourceUnavailable = true)
            )
        )
        assertTrue(summary.partialFlags.fosterSourceUnavailable)
    }
}
