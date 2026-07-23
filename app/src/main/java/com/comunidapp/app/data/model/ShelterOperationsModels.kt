package com.comunidapp.app.data.model

/**
 * LeoVer M11 — operación de refugios (bloque 1).
 * Independiente del listing legacy [Shelter] / tabla `shelters` (006).
 */

enum class ShelterStatus {
    DRAFT, ACTIVE, PAUSED, SUSPENDED, CLOSED, UNKNOWN;

    companion object {
        fun fromString(value: String?): ShelterStatus =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }

    val isOperative: Boolean get() = this == ACTIVE
}

enum class ShelterAvailabilityStatus {
    AVAILABLE, LIMITED, FULL, UNAVAILABLE, UNKNOWN;

    companion object {
        fun fromString(value: String?): ShelterAvailabilityStatus =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

enum class ShelterIntakeType {
    RESCUE, OWNER_SURRENDER, TRANSFER, FOSTER_RETURN, FOUND, OTHER, UNKNOWN;

    companion object {
        fun fromString(value: String?): ShelterIntakeType =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

enum class ShelterPetPlacementStatus {
    RESERVED, ACTIVE, QUARANTINE, MEDICAL_CARE, RELEASED, TRANSFERRED,
    ADOPTED, DECEASED, CANCELLED, UNKNOWN;

    companion object {
        fun fromString(value: String?): ShelterPetPlacementStatus =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }

    val countsTowardCapacity: Boolean
        get() = this == RESERVED || this == ACTIVE || this == QUARANTINE || this == MEDICAL_CARE

    val isOpen: Boolean get() = countsTowardCapacity
}

enum class ShelterPetEndReason {
    RELEASED_TO_OWNER, ADOPTED, TRANSFERRED, FOSTERED, DECEASED, OTHER, UNKNOWN;

    companion object {
        fun fromString(value: String?): ShelterPetEndReason =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

enum class ShelterVolunteerRole {
    ANIMAL_CARE, CLEANING, TRANSPORT, ADMINISTRATION, VETERINARY_SUPPORT,
    EVENT_SUPPORT, OTHER, UNKNOWN;

    companion object {
        fun fromString(value: String?): ShelterVolunteerRole =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

enum class ShelterVolunteerStatus {
    INVITED, ACTIVE, PAUSED, ENDED, REJECTED, UNKNOWN;

    companion object {
        fun fromString(value: String?): ShelterVolunteerStatus =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }

    val isOpen: Boolean
        get() = this == INVITED || this == ACTIVE || this == PAUSED
}

data class ShelterProfile(
    val id: String,
    val organizationId: String,
    val branchId: String? = null,
    val displayName: String,
    val description: String? = null,
    val status: ShelterStatus = ShelterStatus.DRAFT,
    val totalCapacity: Int,
    val currentOccupancy: Int = 0,
    val reservedCapacity: Int = 0,
    val acceptedSpecies: Set<String> = emptySet(),
    val acceptsEmergencies: Boolean = false,
    val publicZoneText: String? = null,
    val internalAddressRef: String? = null,
    val createdAt: Long,
    val updatedAt: Long
) {
    val usedSlots: Int get() = currentOccupancy + reservedCapacity
    val freeSlots: Int get() = (totalCapacity - usedSlots).coerceAtLeast(0)
    val availability: ShelterAvailabilityStatus
        get() = recomputeShelterAvailability(status, totalCapacity, currentOccupancy, reservedCapacity)
}

/** Proyección pública: sin dirección interna ni contactos. */
data class ShelterPublicListing(
    val id: String,
    val organizationId: String,
    val displayName: String,
    val description: String? = null,
    val publicZoneText: String? = null,
    val totalCapacity: Int,
    val freeSlotsApproximate: Int,
    val acceptedSpecies: Set<String>,
    val acceptsEmergencies: Boolean,
    val status: ShelterStatus,
    val availability: ShelterAvailabilityStatus
)

fun ShelterProfile.toPublicListing(): ShelterPublicListing = ShelterPublicListing(
    id = id,
    organizationId = organizationId,
    displayName = displayName,
    description = description,
    publicZoneText = publicZoneText,
    totalCapacity = totalCapacity,
    freeSlotsApproximate = freeSlots,
    acceptedSpecies = acceptedSpecies,
    acceptsEmergencies = acceptsEmergencies,
    status = status,
    availability = availability
)

data class ShelterPetPlacement(
    val id: String,
    val shelterProfileId: String,
    val petId: String,
    val petName: String? = null,
    val intakeType: ShelterIntakeType,
    val status: ShelterPetPlacementStatus,
    val admittedAt: Long,
    val admittedBy: String,
    val sourceOrganizationId: String? = null,
    val sourceUserId: String? = null,
    val notes: String? = null,
    val endedAt: Long? = null,
    val endReason: ShelterPetEndReason? = null,
    val organizationalResponsibilityId: String? = null,
    val relatedFosterPlacementId: String? = null
)

data class ShelterVolunteerAssignment(
    val id: String,
    val shelterProfileId: String,
    val userId: String,
    val role: ShelterVolunteerRole,
    val status: ShelterVolunteerStatus,
    val startsAt: Long,
    val endsAt: Long? = null,
    val notes: String? = null,
    val invitedBy: String? = null
)

fun recomputeShelterAvailability(
    status: ShelterStatus,
    capacity: Int,
    occupancy: Int,
    reserved: Int
): ShelterAvailabilityStatus {
    if (status != ShelterStatus.ACTIVE) return ShelterAvailabilityStatus.UNAVAILABLE
    val used = occupancy.coerceAtLeast(0) + reserved.coerceAtLeast(0)
    return when {
        used >= capacity.coerceAtLeast(0) -> ShelterAvailabilityStatus.FULL
        used > 0 -> ShelterAvailabilityStatus.LIMITED
        else -> ShelterAvailabilityStatus.AVAILABLE
    }
}
