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

// ---------------------------------------------------------------------------
// LeoVer M11 — bloque 2: campañas, insumos y red de ayuda no monetaria
// ---------------------------------------------------------------------------

enum class ShelterCampaignCategory {
    FOOD, MEDICATION, HYGIENE, VETERINARY, TRANSPORT, INFRASTRUCTURE, EMERGENCY, OTHER, UNKNOWN;

    companion object {
        fun fromString(value: String?): ShelterCampaignCategory =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

enum class ShelterCampaignVisibility {
    PUBLIC, INTERNAL, UNKNOWN;

    companion object {
        fun fromString(value: String?): ShelterCampaignVisibility =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

enum class ShelterCampaignStatus {
    DRAFT, ACTIVE, PAUSED, COMPLETED, CANCELLED, UNKNOWN;

    companion object {
        fun fromString(value: String?): ShelterCampaignStatus =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

enum class ShelterSupplyCategory {
    FOOD, MEDICATION, HYGIENE, VETERINARY, TRANSPORT, INFRASTRUCTURE, EMERGENCY, OTHER, UNKNOWN;

    companion object {
        fun fromString(value: String?): ShelterSupplyCategory =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

enum class ShelterSupplyPriority {
    NORMAL, HIGH, URGENT, UNKNOWN;

    companion object {
        fun fromString(value: String?): ShelterSupplyPriority =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

enum class ShelterSupplyRequestStatus {
    DRAFT, OPEN, PARTIALLY_COMMITTED, FULLY_COMMITTED, PARTIALLY_RECEIVED,
    FULFILLED, EXPIRED, CANCELLED, UNKNOWN;

    companion object {
        fun fromString(value: String?): ShelterSupplyRequestStatus =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }

    val isOpen: Boolean
        get() = this == DRAFT || this == OPEN || this == PARTIALLY_COMMITTED ||
            this == FULLY_COMMITTED || this == PARTIALLY_RECEIVED
}

enum class ShelterSupplyContributionStatus {
    PLEDGED, CONFIRMED, PARTIALLY_RECEIVED, RECEIVED, CANCELLED, REJECTED, UNKNOWN;

    companion object {
        fun fromString(value: String?): ShelterSupplyContributionStatus =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

data class ShelterCampaign(
    val id: String,
    val shelterProfileId: String,
    val title: String,
    val description: String,
    val category: ShelterCampaignCategory,
    val visibility: ShelterCampaignVisibility,
    val status: ShelterCampaignStatus,
    val startsAt: Long? = null,
    val endsAt: Long? = null,
    val coverAssetRef: String? = null,
    val createdBy: String,
    val createdAt: Long,
    val updatedAt: Long
)

/** Proyección pública: sin datos internos ni bancarios. */
data class ShelterCampaignPublicListing(
    val id: String,
    val shelterProfileId: String,
    val shelterDisplayName: String? = null,
    val title: String,
    val description: String,
    val category: ShelterCampaignCategory,
    val status: ShelterCampaignStatus,
    val startsAt: Long? = null,
    val endsAt: Long? = null,
    val coverAssetRef: String? = null
)

fun ShelterCampaign.toPublicListing(shelterDisplayName: String? = null): ShelterCampaignPublicListing =
    ShelterCampaignPublicListing(
        id = id,
        shelterProfileId = shelterProfileId,
        shelterDisplayName = shelterDisplayName,
        title = title,
        description = description,
        category = category,
        status = status,
        startsAt = startsAt,
        endsAt = endsAt,
        coverAssetRef = coverAssetRef
    )

data class ShelterCampaignUpdate(
    val id: String,
    val campaignId: String,
    val authorUserId: String,
    val visibility: ShelterCampaignVisibility,
    val message: String,
    val evidenceRef: String? = null,
    val createdAt: Long
)

data class ShelterSupplyRequest(
    val id: String,
    val shelterProfileId: String,
    val campaignId: String? = null,
    val category: ShelterSupplyCategory,
    val itemName: String,
    val description: String? = null,
    val quantityRequested: Int,
    val quantityCommitted: Int = 0,
    val quantityReceived: Int = 0,
    val unitText: String,
    val priority: ShelterSupplyPriority,
    val status: ShelterSupplyRequestStatus,
    val expiresAt: Long? = null,
    val publicNotes: String? = null,
    val internalNotes: String? = null,
    val createdBy: String,
    val createdAt: Long,
    val updatedAt: Long
)

/** Proyección pública: sin internalNotes; incluye progreso. */
data class ShelterSupplyRequestPublicListing(
    val id: String,
    val shelterProfileId: String,
    val shelterDisplayName: String? = null,
    val campaignId: String? = null,
    val category: ShelterSupplyCategory,
    val itemName: String,
    val description: String? = null,
    val quantityRequested: Int,
    val quantityCommitted: Int,
    val quantityReceived: Int,
    val unitText: String,
    val priority: ShelterSupplyPriority,
    val status: ShelterSupplyRequestStatus,
    val expiresAt: Long? = null,
    val publicNotes: String? = null
)

fun ShelterSupplyRequest.toPublicListing(shelterDisplayName: String? = null): ShelterSupplyRequestPublicListing =
    ShelterSupplyRequestPublicListing(
        id = id,
        shelterProfileId = shelterProfileId,
        shelterDisplayName = shelterDisplayName,
        campaignId = campaignId,
        category = category,
        itemName = itemName,
        description = description,
        quantityRequested = quantityRequested,
        quantityCommitted = quantityCommitted,
        quantityReceived = quantityReceived,
        unitText = unitText,
        priority = priority,
        status = status,
        expiresAt = expiresAt,
        publicNotes = publicNotes
    )

data class ShelterSupplyContribution(
    val id: String,
    val requestId: String,
    val contributorUserId: String,
    val quantityCommitted: Int,
    val quantityReceived: Int = 0,
    val status: ShelterSupplyContributionStatus,
    val contributorNotes: String? = null,
    val internalReceiptNotes: String? = null,
    val evidenceRef: String? = null,
    val committedAt: Long,
    val receivedAt: Long? = null,
    val cancelledAt: Long? = null
)

fun recomputeSupplyRequestStatus(
    requested: Int,
    committed: Int,
    received: Int,
    expiresAt: Long?,
    now: Long,
    currentIfTerminal: ShelterSupplyRequestStatus
): ShelterSupplyRequestStatus {
    when (currentIfTerminal) {
        ShelterSupplyRequestStatus.CANCELLED -> return ShelterSupplyRequestStatus.CANCELLED
        ShelterSupplyRequestStatus.DRAFT -> return ShelterSupplyRequestStatus.DRAFT
        else -> Unit
    }
    if (requested > 0 && received >= requested) return ShelterSupplyRequestStatus.FULFILLED
    if (received > 0) return ShelterSupplyRequestStatus.PARTIALLY_RECEIVED
    if (requested > 0 && committed >= requested) return ShelterSupplyRequestStatus.FULLY_COMMITTED
    if (committed > 0) return ShelterSupplyRequestStatus.PARTIALLY_COMMITTED
    if (expiresAt != null && expiresAt < now) return ShelterSupplyRequestStatus.EXPIRED
    return ShelterSupplyRequestStatus.OPEN
}

// ---------------------------------------------------------------------------
// LeoVer M11 — bloque 3: urgencias, eventos y reportes operativos
// ---------------------------------------------------------------------------

enum class ShelterEmergencyCategory {
    MEDICAL, FOOD, MEDICATION, CAPACITY, TRANSPORT, INFRASTRUCTURE, RESCUE, OTHER, UNKNOWN;

    companion object {
        fun fromString(value: String?): ShelterEmergencyCategory =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

enum class ShelterEmergencySeverity {
    LOW, MEDIUM, HIGH, CRITICAL, UNKNOWN;

    companion object {
        fun fromString(value: String?): ShelterEmergencySeverity =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

enum class ShelterEmergencyVisibility {
    PUBLIC, INTERNAL, UNKNOWN;

    companion object {
        fun fromString(value: String?): ShelterEmergencyVisibility =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

enum class ShelterEmergencyStatus {
    DRAFT, ACTIVE, RESOLVED, EXPIRED, CANCELLED, UNKNOWN;

    companion object {
        fun fromString(value: String?): ShelterEmergencyStatus =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

enum class ShelterEventType {
    VOLUNTEERING, ADOPTION_DAY, COLLECTION, TRAINING, OPEN_HOUSE, COMMUNITY, OTHER, UNKNOWN;

    companion object {
        fun fromString(value: String?): ShelterEventType =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

enum class ShelterEventVisibility {
    PUBLIC, INTERNAL, UNKNOWN;

    companion object {
        fun fromString(value: String?): ShelterEventVisibility =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

enum class ShelterEventStatus {
    DRAFT, PUBLISHED, FULL, COMPLETED, CANCELLED, UNKNOWN;

    companion object {
        fun fromString(value: String?): ShelterEventStatus =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

enum class ShelterEventRegistrationStatus {
    REGISTERED, WAITLISTED, ATTENDED, NO_SHOW, CANCELLED, UNKNOWN;

    companion object {
        fun fromString(value: String?): ShelterEventRegistrationStatus =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }

    val isActive: Boolean get() = this == REGISTERED || this == WAITLISTED
}

data class ShelterEmergency(
    val id: String,
    val shelterProfileId: String,
    val petId: String?,
    val title: String,
    val description: String,
    val category: ShelterEmergencyCategory,
    val severity: ShelterEmergencySeverity,
    val visibility: ShelterEmergencyVisibility,
    val status: ShelterEmergencyStatus,
    val startsAt: Long,
    val expiresAt: Long?,
    val resolvedAt: Long?,
    val resolutionNotes: String?,
    val evidenceRef: String?,
    val createdBy: String,
    val createdAt: Long,
    val updatedAt: Long
)

/** Proyección pública: sin notas de resolución, evidencia ni datos internos. */
data class ShelterEmergencyPublicListing(
    val id: String,
    val shelterProfileId: String,
    val shelterDisplayName: String? = null,
    val title: String,
    val description: String,
    val category: ShelterEmergencyCategory,
    val severity: ShelterEmergencySeverity,
    val status: ShelterEmergencyStatus,
    val startsAt: Long,
    val expiresAt: Long?
)

fun ShelterEmergency.toPublicListing(shelterDisplayName: String? = null): ShelterEmergencyPublicListing =
    ShelterEmergencyPublicListing(
        id = id,
        shelterProfileId = shelterProfileId,
        shelterDisplayName = shelterDisplayName,
        title = title,
        description = description,
        category = category,
        severity = severity,
        status = status,
        startsAt = startsAt,
        expiresAt = expiresAt
    )

data class ShelterEvent(
    val id: String,
    val shelterProfileId: String,
    val title: String,
    val description: String,
    val eventType: ShelterEventType,
    val visibility: ShelterEventVisibility,
    val status: ShelterEventStatus,
    val startsAt: Long,
    val endsAt: Long,
    val capacity: Int?,
    val registeredCount: Int,
    val publicLocationText: String?,
    val privateLocationText: String?,
    val coverAssetRef: String?,
    val createdBy: String,
    val createdAt: Long,
    val updatedAt: Long
)

/** Proyección pública: sin dirección privada. */
data class ShelterEventPublicListing(
    val id: String,
    val shelterProfileId: String,
    val shelterDisplayName: String? = null,
    val title: String,
    val description: String,
    val eventType: ShelterEventType,
    val status: ShelterEventStatus,
    val startsAt: Long,
    val endsAt: Long,
    val capacity: Int?,
    val registeredCount: Int,
    val publicLocationText: String?,
    val coverAssetRef: String?
)

fun ShelterEvent.toPublicListing(shelterDisplayName: String? = null): ShelterEventPublicListing =
    ShelterEventPublicListing(
        id = id,
        shelterProfileId = shelterProfileId,
        shelterDisplayName = shelterDisplayName,
        title = title,
        description = description,
        eventType = eventType,
        status = status,
        startsAt = startsAt,
        endsAt = endsAt,
        capacity = capacity,
        registeredCount = registeredCount,
        publicLocationText = publicLocationText,
        coverAssetRef = coverAssetRef
    )

data class ShelterEventRegistration(
    val id: String,
    val eventId: String,
    val userId: String,
    val status: ShelterEventRegistrationStatus,
    val notes: String?,
    val registeredAt: Long,
    val cancelledAt: Long?
)

data class ShelterCapacityMetrics(
    val shelterProfileId: String,
    val from: Long,
    val to: Long,
    val totalCapacity: Int,
    val currentOccupancy: Int,
    val reservedCapacity: Int,
    val freeSlots: Int
)

data class ShelterPetMetrics(
    val shelterProfileId: String,
    val from: Long,
    val to: Long,
    val activeCount: Int,
    val quarantineCount: Int,
    val medicalCareCount: Int,
    val releasesCount: Int,
    val adoptionsCount: Int
)

data class ShelterVolunteerMetrics(
    val shelterProfileId: String,
    val from: Long,
    val to: Long,
    val activeCount: Int,
    val pausedCount: Int,
    val endedCount: Int
)

data class ShelterCampaignMetrics(
    val shelterProfileId: String,
    val from: Long,
    val to: Long,
    val activeCount: Int,
    val completedCount: Int
)

data class ShelterSupplyMetrics(
    val shelterProfileId: String,
    val from: Long,
    val to: Long,
    val openRequestsCount: Int,
    val fulfilledRequestsCount: Int,
    val quantityReceivedTotal: Int
)

data class ShelterEmergencyMetrics(
    val shelterProfileId: String,
    val from: Long,
    val to: Long,
    val activeCount: Int,
    val criticalCount: Int,
    val resolvedCount: Int
)

data class ShelterEventMetrics(
    val shelterProfileId: String,
    val from: Long,
    val to: Long,
    val upcomingCount: Int,
    val completedCount: Int,
    val registrationsCount: Int
)

data class ShelterOperationalSummary(
    val shelterProfileId: String,
    val from: Long,
    val to: Long,
    val capacity: ShelterCapacityMetrics,
    val pets: ShelterPetMetrics,
    val volunteers: ShelterVolunteerMetrics,
    val campaigns: ShelterCampaignMetrics,
    val supplies: ShelterSupplyMetrics,
    val emergencies: ShelterEmergencyMetrics,
    val events: ShelterEventMetrics,
    val generatedAt: Long
)

data class ShelterReportExport(
    val csvContent: String,
    val fileName: String,
    val generatedAt: Long
)

fun isEmergencyExpired(
    expiresAt: Long?,
    now: Long,
    status: ShelterEmergencyStatus
): Boolean {
    if (status == ShelterEmergencyStatus.EXPIRED) return true
    if (status != ShelterEmergencyStatus.ACTIVE) return false
    return expiresAt != null && expiresAt <= now
}
