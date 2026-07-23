package com.comunidapp.app.data.model

/**
 * LeoVer M10 — hogares de tránsito (bloque 1: perfiles, solicitudes, ingresos).
 * Independiente del listing legacy [FosterHomeListing] / [FosterRequest].
 */

enum class FosterHomeStatus {
    DRAFT, ACTIVE, PAUSED, SUSPENDED, CLOSED, UNKNOWN;

    companion object {
        fun fromString(value: String?): FosterHomeStatus =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

enum class FosterAvailabilityStatus {
    AVAILABLE, LIMITED, FULL, UNAVAILABLE, UNKNOWN;

    companion object {
        fun fromString(value: String?): FosterAvailabilityStatus =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

enum class FosterUrgency {
    NORMAL, HIGH, EMERGENCY, UNKNOWN;

    companion object {
        fun fromString(value: String?): FosterUrgency =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

enum class FosterHomeRequestStatus {
    SUBMITTED, UNDER_REVIEW, ACCEPTED, REJECTED, CANCELLED, EXPIRED, UNKNOWN;

    companion object {
        fun fromString(value: String?): FosterHomeRequestStatus =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }

    val isActive: Boolean
        get() = this == SUBMITTED || this == UNDER_REVIEW || this == ACCEPTED
}

enum class FosterPlacementStatus {
    RESERVED, ACTIVE, COMPLETED, CANCELLED, UNKNOWN;

    companion object {
        fun fromString(value: String?): FosterPlacementStatus =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

data class FosterHomeProfile(
    val id: String,
    val ownerUserId: String,
    val displayName: String,
    val description: String? = null,
    val status: FosterHomeStatus = FosterHomeStatus.DRAFT,
    val availabilityStatus: FosterAvailabilityStatus = FosterAvailabilityStatus.UNAVAILABLE,
    val totalCapacity: Int,
    val currentOccupancy: Int = 0,
    val reservedCount: Int = 0,
    val acceptedSpecies: Set<String> = emptySet(),
    val acceptedSizes: Set<String> = emptySet(),
    val acceptsSpecialNeeds: Boolean = false,
    val acceptsEmergencies: Boolean = false,
    val hasOtherAnimals: Boolean? = null,
    val observations: String? = null,
    val zoneText: String,
    val publicLocationText: String? = null,
    /** Never exposed in public listings; owner-only. */
    val privateAddressText: String? = null,
    val createdAt: Long,
    val updatedAt: Long
) {
    val freeSlots: Int
        get() = (totalCapacity - currentOccupancy - reservedCount).coerceAtLeast(0)

    fun toPublicListing(): FosterHomePublicListing = FosterHomePublicListing(
        id = id,
        displayName = displayName,
        description = description,
        availabilityStatus = availabilityStatus,
        totalCapacity = totalCapacity,
        freeSlots = freeSlots,
        acceptedSpecies = acceptedSpecies,
        acceptedSizes = acceptedSizes,
        acceptsSpecialNeeds = acceptsSpecialNeeds,
        acceptsEmergencies = acceptsEmergencies,
        zoneText = zoneText,
        publicLocationText = publicLocationText
    )
}

/** Public projection — no private address, phone, or internal notes. */
data class FosterHomePublicListing(
    val id: String,
    val displayName: String,
    val description: String?,
    val availabilityStatus: FosterAvailabilityStatus,
    val totalCapacity: Int,
    val freeSlots: Int,
    val acceptedSpecies: Set<String>,
    val acceptedSizes: Set<String>,
    val acceptsSpecialNeeds: Boolean,
    val acceptsEmergencies: Boolean,
    val zoneText: String,
    val publicLocationText: String?
)

/**
 * M10 solicitación de tránsito (tabla SQL `foster_care_requests`).
 * No confundir con el legacy [FosterRequest] de servicios.
 */
data class FosterHomeRequest(
    val id: String,
    val fosterHomeId: String,
    val petId: String,
    val petName: String? = null,
    val requesterUserId: String? = null,
    val requesterOrganizationId: String? = null,
    val requesterDisplayName: String? = null,
    val message: String,
    val urgency: FosterUrgency = FosterUrgency.NORMAL,
    val requestedStartAt: Long? = null,
    val estimatedEndAt: Long? = null,
    val specialNeeds: String? = null,
    val status: FosterHomeRequestStatus = FosterHomeRequestStatus.SUBMITTED,
    val createdAt: Long,
    val reviewedAt: Long? = null,
    val reviewedBy: String? = null,
    val rejectionReason: String? = null
)

data class FosterPlacement(
    val id: String,
    val fosterRequestId: String,
    val fosterHomeId: String,
    val petId: String,
    val petName: String? = null,
    val requesterUserId: String? = null,
    val requesterOrganizationId: String? = null,
    val fosterUserId: String,
    val status: FosterPlacementStatus,
    val startedAt: Long,
    val estimatedEndAt: Long? = null,
    val endedAt: Long? = null,
    val initialNotes: String? = null,
    val endReason: String? = null,
    val temporaryResponsibilityId: String? = null
)
