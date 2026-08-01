package com.comunidapp.app.data.model

/**
 * LeoVer M16 Bloque 3 — proyección operativa de lectura (no muta M08/M09/M15).
 */

enum class M16ShelterPetOperationalStatus {
    PHYSICALLY_HOUSED,
    IN_ACTIVE_FOSTER,
    ACTIVE_ADOPTION_PROCESS,
    RECENTLY_ADOPTED,
    INACTIVE,
    INCONSISTENT
}

enum class M16ShelterOperationsFilter {
    ALL,
    HOUSED,
    IN_FOSTER,
    IN_ADOPTION,
    ADOPTED,
    INCONSISTENT
}

data class M16ShelterOperationsPartialFlags(
    val petsSourceUnavailable: Boolean = false,
    val adoptionsSourceUnavailable: Boolean = false,
    val fosterSourceUnavailable: Boolean = false,
    val shelterOpsSourceUnavailable: Boolean = false
) {
    val hasPartialData: Boolean
        get() = petsSourceUnavailable || adoptionsSourceUnavailable ||
            fosterSourceUnavailable || shelterOpsSourceUnavailable
}

data class M16ShelterOccupancyBreakdown(
    val totalCapacity: Int,
    val physicalOccupancy: Int,
    val reservedCapacity: Int,
    val inActiveFosterCount: Int,
    val activeAdoptionCount: Int,
    val recentlyAdoptedCount: Int,
    val availableCapacity: Int,
    val configuredOccupancySnapshot: Int?,
    val occupancyExceedsCapacity: Boolean,
    val warnings: List<String> = emptyList()
)

data class M16ShelterPetOperationalItem(
    val petId: String,
    val displayName: String,
    val species: String,
    val photoUrl: String?,
    val status: M16ShelterPetOperationalStatus,
    val physicallyHoused: Boolean,
    val adoptionStatusLabel: String?,
    val fosterStatusLabel: String?,
    val adoptionPostId: String?,
    val fosterPlacementId: String?,
    val shelterPlacementId: String?,
    val warning: String?
)

data class M16ShelterOperationsSummary(
    val shelterId: String,
    val organizationId: String,
    val breakdown: M16ShelterOccupancyBreakdown,
    val pets: List<M16ShelterPetOperationalItem>,
    val partialFlags: M16ShelterOperationsPartialFlags = M16ShelterOperationsPartialFlags()
)

fun filterOperationalPets(
    pets: List<M16ShelterPetOperationalItem>,
    filter: M16ShelterOperationsFilter
): List<M16ShelterPetOperationalItem> = when (filter) {
    M16ShelterOperationsFilter.ALL -> pets
    M16ShelterOperationsFilter.HOUSED -> pets.filter { it.physicallyHoused }
    M16ShelterOperationsFilter.IN_FOSTER ->
        pets.filter { it.status == M16ShelterPetOperationalStatus.IN_ACTIVE_FOSTER }
    M16ShelterOperationsFilter.IN_ADOPTION ->
        pets.filter { it.status == M16ShelterPetOperationalStatus.ACTIVE_ADOPTION_PROCESS }
    M16ShelterOperationsFilter.ADOPTED ->
        pets.filter { it.status == M16ShelterPetOperationalStatus.RECENTLY_ADOPTED }
    M16ShelterOperationsFilter.INCONSISTENT ->
        pets.filter { it.status == M16ShelterPetOperationalStatus.INCONSISTENT }
}
