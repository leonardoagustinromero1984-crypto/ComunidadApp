package com.comunidapp.app.domain.m16

import com.comunidapp.app.data.model.AdoptionPost
import com.comunidapp.app.data.model.AdoptionStatus
import com.comunidapp.app.data.model.M15FosterPlacement
import com.comunidapp.app.data.model.M15FosterPlacementStatus
import com.comunidapp.app.data.model.M16ShelterOccupancyBreakdown
import com.comunidapp.app.data.model.M16ShelterOperationsPartialFlags
import com.comunidapp.app.data.model.M16ShelterOperationsSummary
import com.comunidapp.app.data.model.M16ShelterPetOperationalItem
import com.comunidapp.app.data.model.M16ShelterPetOperationalStatus
import com.comunidapp.app.data.model.M16ShelterProfile
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.ShelterPetPlacement
import com.comunidapp.app.data.model.ShelterPetPlacementStatus

/**
 * Combina autoridades M08/M09/M11/M15 en una proyección M16 de solo lectura.
 */
class M16ShelterOperationsService {

    data class SourceSnapshot(
        val profile: M16ShelterProfile,
        val shelterPlacements: List<ShelterPetPlacement> = emptyList(),
        val adoptions: List<AdoptionPost> = emptyList(),
        val fosterPlacements: List<M15FosterPlacement> = emptyList(),
        val petsById: Map<String, Pet> = emptyMap(),
        val partialFlags: M16ShelterOperationsPartialFlags = M16ShelterOperationsPartialFlags()
    )

    fun buildSummary(snapshot: SourceSnapshot): M16ShelterOperationsSummary {
        val profile = snapshot.profile
        val petIds = linkedSetOf<String>()
        snapshot.shelterPlacements.mapTo(petIds) { it.petId }
        snapshot.adoptions.mapNotNullTo(petIds) { it.petId?.takeIf { id -> id.isNotBlank() } }
        snapshot.fosterPlacements.mapTo(petIds) { it.petId }

        val openShelterByPet = snapshot.shelterPlacements
            .filter { it.status.isOpen }
            .associateBy { it.petId }
        val reservedShelterByPet = snapshot.shelterPlacements
            .filter { it.status == ShelterPetPlacementStatus.RESERVED }
            .associateBy { it.petId }
        val activeFosterByPet = snapshot.fosterPlacements
            .filter { it.status == M15FosterPlacementStatus.ACTIVE || it.status == M15FosterPlacementStatus.RESERVED }
            .associateBy { it.petId }
        val adoptionByPet = snapshot.adoptions
            .filter { !it.petId.isNullOrBlank() }
            .groupBy { it.petId!! }

        val items = mutableListOf<M16ShelterPetOperationalItem>()
        val warnings = mutableListOf<String>()

        for (petId in petIds) {
            val pet = snapshot.petsById[petId]
            if (pet != null && (pet.status == "DECEASED" || pet.status == "ARCHIVED")) {
                items += buildInactiveItem(petId, pet, adoptionByPet[petId])
                continue
            }
            if (pet == null && snapshot.partialFlags.petsSourceUnavailable) {
                warnings += "Mascota $petId no disponible en M08."
                continue
            }

            val housed = openShelterByPet.containsKey(petId)
            val inFoster = activeFosterByPet.containsKey(petId)
            val adoptions = adoptionByPet[petId].orEmpty()
            val activeAdoption = adoptions.firstOrNull {
                it.status == AdoptionStatus.PUBLISHED || it.status == AdoptionStatus.PAUSED
            }
            val recentAdopted = adoptions.firstOrNull { it.status == AdoptionStatus.ADOPTED }

            val status = classify(housed, inFoster, activeAdoption != null, recentAdopted != null)
            val warning = when {
                housed && inFoster ->
                    "Mascota alojada y en tránsito activo — revisar M11/M15."
                activeAdoption != null && recentAdopted != null ->
                    "Adopción activa y completada registradas — revisar M09."
                pet == null -> "Referencia de mascota sin datos M08."
                else -> null
            }
            if (warning != null) warnings += warning

            items += M16ShelterPetOperationalItem(
                petId = petId,
                displayName = pet?.name ?: adoptions.firstOrNull()?.name ?: "Mascota",
                species = pet?.species?.name ?: adoptions.firstOrNull()?.species?.name ?: "UNKNOWN",
                photoUrl = pet?.photoUrl ?: adoptions.firstOrNull()?.photoUrl,
                status = status,
                physicallyHoused = housed,
                adoptionStatusLabel = activeAdoption?.status?.name ?: recentAdopted?.status?.name,
                fosterStatusLabel = activeFosterByPet[petId]?.status?.name,
                adoptionPostId = activeAdoption?.id ?: recentAdopted?.id,
                fosterPlacementId = activeFosterByPet[petId]?.id,
                shelterPlacementId = openShelterByPet[petId]?.id ?: reservedShelterByPet[petId]?.id,
                warning = warning
            )
        }

        val physicalOccupancy = items.count {
            it.physicallyHoused && it.status != M16ShelterPetOperationalStatus.INACTIVE
        }
        val reservedCapacity = reservedShelterByPet.keys.count { petId ->
            items.any { it.petId == petId && it.physicallyHoused }
        }
        val inFosterCount = items.count { it.status == M16ShelterPetOperationalStatus.IN_ACTIVE_FOSTER }
        val activeAdoptionCount = items.count {
            it.status == M16ShelterPetOperationalStatus.ACTIVE_ADOPTION_PROCESS
        }
        val recentlyAdoptedCount = items.count {
            it.status == M16ShelterPetOperationalStatus.RECENTLY_ADOPTED
        }
        val totalCapacity = profile.capacity.totalCapacity
        val available = (totalCapacity - physicalOccupancy - reservedCapacity).coerceAtLeast(0)
        val exceeds = physicalOccupancy + reservedCapacity > totalCapacity
        if (exceeds) {
            warnings += "Ocupación calculada supera la capacidad configurada."
        }

        return M16ShelterOperationsSummary(
            shelterId = profile.id,
            organizationId = profile.organizationId,
            breakdown = M16ShelterOccupancyBreakdown(
                totalCapacity = totalCapacity,
                physicalOccupancy = physicalOccupancy,
                reservedCapacity = reservedCapacity,
                inActiveFosterCount = inFosterCount,
                activeAdoptionCount = activeAdoptionCount,
                recentlyAdoptedCount = recentlyAdoptedCount,
                availableCapacity = available,
                configuredOccupancySnapshot = profile.capacity.currentOccupancy,
                occupancyExceedsCapacity = exceeds,
                warnings = warnings.distinct()
            ),
            pets = items.sortedBy { it.displayName.lowercase() },
            partialFlags = snapshot.partialFlags
        )
    }

    private fun classify(
        housed: Boolean,
        inFoster: Boolean,
        activeAdoption: Boolean,
        recentlyAdopted: Boolean
    ): M16ShelterPetOperationalStatus = when {
        housed && inFoster -> M16ShelterPetOperationalStatus.INCONSISTENT
        inFoster -> M16ShelterPetOperationalStatus.IN_ACTIVE_FOSTER
        housed && activeAdoption -> M16ShelterPetOperationalStatus.ACTIVE_ADOPTION_PROCESS
        housed -> M16ShelterPetOperationalStatus.PHYSICALLY_HOUSED
        activeAdoption -> M16ShelterPetOperationalStatus.ACTIVE_ADOPTION_PROCESS
        recentlyAdopted -> M16ShelterPetOperationalStatus.RECENTLY_ADOPTED
        else -> M16ShelterPetOperationalStatus.INCONSISTENT
    }

    private fun buildInactiveItem(
        petId: String,
        pet: Pet,
        adoptions: List<AdoptionPost>?
    ): M16ShelterPetOperationalItem = M16ShelterPetOperationalItem(
        petId = petId,
        displayName = pet.name,
        species = pet.species.name,
        photoUrl = pet.photoUrl,
        status = M16ShelterPetOperationalStatus.INACTIVE,
        physicallyHoused = false,
        adoptionStatusLabel = adoptions?.firstOrNull()?.status?.name,
        fosterStatusLabel = null,
        adoptionPostId = adoptions?.firstOrNull()?.id,
        fosterPlacementId = null,
        shelterPlacementId = null,
        warning = "Mascota ${pet.status.lowercase()} — no cuenta en ocupación."
    )
}
