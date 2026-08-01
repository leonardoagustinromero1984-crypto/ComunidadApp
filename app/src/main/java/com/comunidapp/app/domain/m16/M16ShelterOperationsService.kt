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
 *
 * Ocupación física (Modelo A — alineado con `_m11_sync_shelter_capacity`):
 * - physicalOccupancy: ACTIVE, QUARANTINE, MEDICAL_CARE (petIds únicos)
 * - reservedCapacity: RESERVED sin ingreso físico (petIds únicos, excluye físicos)
 * - committedCapacity: physical + reserved sin duplicar
 */
class M16ShelterOperationsService(
    private val clockMillis: () -> Long = { System.currentTimeMillis() }
) {

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
        var partial = snapshot.partialFlags
        val warnings = mutableListOf<String>()

        val openPlacements = snapshot.shelterPlacements.filter { it.status.isOpen }
        fun petIsInactive(petId: String): Boolean {
            val pet = snapshot.petsById[petId]
            return pet != null && (pet.status == "DECEASED" || pet.status == "ARCHIVED")
        }

        val physicalByPet = openPlacements
            .filter { it.status.isPhysicallyOccupying && !petIsInactive(it.petId) }
            .associateBy { it.petId }
        val reservedByPet = openPlacements
            .filter { it.status.isReservedSlot && !physicalByPet.containsKey(it.petId) && !petIsInactive(it.petId) }
            .associateBy { it.petId }

        openPlacements.groupBy { it.petId }.filter { it.value.size > 1 }.keys.forEach { petId ->
            warnings += "Mascota $petId tiene múltiples placements M11 abiertos."
        }

        val activeFosterByPet = snapshot.fosterPlacements
            .filter {
                it.status == M15FosterPlacementStatus.ACTIVE ||
                    it.status == M15FosterPlacementStatus.RESERVED
            }
            .associateBy { it.petId }

        val adoptionByPet = snapshot.adoptions
            .filter { !it.petId.isNullOrBlank() }
            .groupBy { it.petId!! }

        val petIds = linkedSetOf<String>()
        physicalByPet.keys.forEach { petIds += it }
        reservedByPet.keys.forEach { petIds += it }
        activeFosterByPet.keys.forEach { petIds += it }
        adoptionByPet.keys.forEach { petIds += it }

        val now = clockMillis()
        var adoptionDatesMissing = snapshot.adoptions.any {
            it.status == AdoptionStatus.ADOPTED && adoptionCompletedAt(it) == null
        }
        val items = mutableListOf<M16ShelterPetOperationalItem>()

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

            val hasPhysical = physicalByPet.containsKey(petId)
            val hasReserved = reservedByPet.containsKey(petId)
            val inFoster = activeFosterByPet.containsKey(petId)
            val adoptions = adoptionByPet[petId].orEmpty()
            val activeAdoption = adoptions.firstOrNull {
                it.status == AdoptionStatus.PUBLISHED || it.status == AdoptionStatus.PAUSED
            }
            if (adoptions.any { it.status == AdoptionStatus.ADOPTED && adoptionCompletedAt(it) == null }) {
                adoptionDatesMissing = true
            }
            val recentAdopted = adoptions.firstOrNull {
                it.status == AdoptionStatus.ADOPTED && isRecentAdoption(it, now)
            }

            val status = classify(hasPhysical, hasReserved, inFoster, activeAdoption != null, recentAdopted != null)
            val warning = when {
                hasPhysical && inFoster ->
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
                physicallyHoused = hasPhysical,
                reservedSlot = hasReserved,
                adoptionStatusLabel = activeAdoption?.status?.name ?: recentAdopted?.status?.name,
                fosterStatusLabel = activeFosterByPet[petId]?.status?.name,
                adoptionPostId = activeAdoption?.id ?: recentAdopted?.id,
                fosterPlacementId = activeFosterByPet[petId]?.id,
                shelterPlacementId = physicalByPet[petId]?.id ?: reservedByPet[petId]?.id,
                warning = warning
            )
        }

        if (adoptionDatesMissing) {
            partial = partial.copy(adoptionCompletionDatesUnavailable = true)
        }

        val physicalOccupancy = physicalByPet.size
        val reservedCapacity = reservedByPet.size
        val committedCapacity = physicalOccupancy + reservedCapacity
        val totalCapacity = profile.capacity.totalCapacity
        val availableCapacity = (totalCapacity - committedCapacity).coerceAtLeast(0)
        val overCapacityBy = (committedCapacity - totalCapacity).coerceAtLeast(0)
        val isOverCapacity = overCapacityBy > 0
        if (isOverCapacity) {
            warnings += "Capacidad comprometida ($committedCapacity) supera total ($totalCapacity) en $overCapacityBy."
        }

        val snapshotOcc = profile.capacity.currentOccupancy
        val snapshotDiffers = snapshotOcc != null && snapshotOcc != physicalOccupancy

        val inFosterCount = items.count {
            it.status == M16ShelterPetOperationalStatus.IN_ACTIVE_FOSTER ||
                (it.status == M16ShelterPetOperationalStatus.INCONSISTENT && it.fosterStatusLabel != null)
        }
        val activeAdoptionCount = items.count {
            it.status == M16ShelterPetOperationalStatus.ACTIVE_ADOPTION_PROCESS ||
                (it.adoptionStatusLabel == AdoptionStatus.PUBLISHED.name ||
                    it.adoptionStatusLabel == AdoptionStatus.PAUSED.name)
        }
        val recentlyAdoptedCount = snapshot.adoptions.count {
            it.status == AdoptionStatus.ADOPTED && isRecentAdoption(it, now)
        }

        return M16ShelterOperationsSummary(
            shelterId = profile.id,
            organizationId = profile.organizationId,
            breakdown = M16ShelterOccupancyBreakdown(
                totalCapacity = totalCapacity,
                physicalOccupancy = physicalOccupancy,
                reservedCapacity = reservedCapacity,
                committedCapacity = committedCapacity,
                inActiveFosterCount = inFosterCount,
                activeAdoptionCount = activeAdoptionCount,
                recentlyAdoptedCount = recentlyAdoptedCount,
                availableCapacity = availableCapacity,
                overCapacityBy = overCapacityBy,
                isOverCapacity = isOverCapacity,
                configuredOccupancySnapshot = snapshotOcc,
                snapshotDiffersFromCalculated = snapshotDiffers,
                warnings = warnings.distinct()
            ),
            pets = items.sortedBy { it.displayName.lowercase() },
            partialFlags = partial
        )
    }

    private fun adoptionCompletedAt(post: AdoptionPost): Long? =
        post.updatedAt ?: post.publishedAt ?: post.createdAt

    private fun isRecentAdoption(post: AdoptionPost, now: Long): Boolean {
        if (post.status != AdoptionStatus.ADOPTED) return false
        val completedAt = adoptionCompletedAt(post) ?: return false
        return now - completedAt <= M16_RECENT_ADOPTION_WINDOW_MS
    }

    private fun classify(
        hasPhysical: Boolean,
        hasReserved: Boolean,
        inFoster: Boolean,
        activeAdoption: Boolean,
        recentlyAdopted: Boolean
    ): M16ShelterPetOperationalStatus = when {
        hasPhysical && inFoster -> M16ShelterPetOperationalStatus.INCONSISTENT
        inFoster -> M16ShelterPetOperationalStatus.IN_ACTIVE_FOSTER
        hasPhysical && activeAdoption -> M16ShelterPetOperationalStatus.ACTIVE_ADOPTION_PROCESS
        hasPhysical -> M16ShelterPetOperationalStatus.PHYSICALLY_HOUSED
        hasReserved -> M16ShelterPetOperationalStatus.RESERVED_SLOT
        activeAdoption -> M16ShelterPetOperationalStatus.ACTIVE_ADOPTION_PROCESS
        recentlyAdopted -> M16ShelterPetOperationalStatus.RECENTLY_ADOPTED
        else -> M16ShelterPetOperationalStatus.INCONSISTENT
    }

    private fun buildInactiveItem(
        petId: String,
        pet: Pet,
        adoptions: List<AdoptionPost>?,
        now: Long = clockMillis()
    ): M16ShelterPetOperationalItem {
        val recent = adoptions?.firstOrNull {
            it.status == AdoptionStatus.ADOPTED && isRecentAdoption(it, now)
        }
        return M16ShelterPetOperationalItem(
        petId = petId,
        displayName = pet.name,
        species = pet.species.name,
        photoUrl = pet.photoUrl,
        status = if (recent != null) {
            M16ShelterPetOperationalStatus.RECENTLY_ADOPTED
        } else {
            M16ShelterPetOperationalStatus.INACTIVE
        },
        physicallyHoused = false,
        reservedSlot = false,
        adoptionStatusLabel = adoptions?.firstOrNull()?.status?.name,
        fosterStatusLabel = null,
        adoptionPostId = recent?.id ?: adoptions?.firstOrNull()?.id,
        fosterPlacementId = null,
        shelterPlacementId = null,
        warning = "Mascota ${pet.status.lowercase()} — no cuenta en ocupación."
    )
    }
}
