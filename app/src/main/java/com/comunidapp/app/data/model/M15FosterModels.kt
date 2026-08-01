package com.comunidapp.app.data.model

/**
 * LeoVer M15 — Hogares de tránsito (Bloque 1 local).
 * Track canónico alineado con producto D01. Legacy M10/Foster* preservado.
 */

enum class M15FosterHomeStatus {
    DRAFT,
    ACTIVE,
    PAUSED,
    SUSPENDED,
    CLOSED;

    val acceptsRequests: Boolean
        get() = this == ACTIVE
}

enum class M15FosterAvailabilityStatus {
    AVAILABLE,
    LIMITED,
    FULL,
    UNAVAILABLE
}

enum class M15FosterUrgency {
    NORMAL,
    HIGH,
    EMERGENCY
}

enum class M15FosterRequestStatus {
    SUBMITTED,
    UNDER_REVIEW,
    ACCEPTED,
    REJECTED,
    CANCELLED,
    EXPIRED;

    val isActive: Boolean
        get() = this == SUBMITTED || this == UNDER_REVIEW || this == ACCEPTED
}

enum class M15FosterPlacementStatus {
    RESERVED,
    ACTIVE,
    COMPLETED,
    CANCELLED
}

object M15PermissionCodes {
    const val FOSTER_HOME_READ = "foster.home.read"
    const val FOSTER_HOME_MANAGE_OWN = "foster.home.manage_own"
    const val FOSTER_REQUEST_SUBMIT = "foster.request.submit"
    const val FOSTER_REQUEST_REVIEW = "foster.request.review"
    const val FOSTER_PLACEMENT_START = "foster.placement.start"
    const val FOSTER_PUBLIC_LIST = "foster.public.list"

    val all: Set<String> = setOf(
        FOSTER_HOME_READ,
        FOSTER_HOME_MANAGE_OWN,
        FOSTER_REQUEST_SUBMIT,
        FOSTER_REQUEST_REVIEW,
        FOSTER_PLACEMENT_START,
        FOSTER_PUBLIC_LIST
    )
}

object M15AuditEvents {
    const val HOME_CREATED = "m15.foster.home.created"
    const val HOME_ACTIVATED = "m15.foster.home.activated"
    const val HOME_STATUS_CHANGED = "m15.foster.home.status_changed"
    const val REQUEST_SUBMITTED = "m15.foster.request.submitted"
    const val REQUEST_REVIEWED = "m15.foster.request.reviewed"
    const val PLACEMENT_RESERVED = "m15.foster.placement.reserved"
    const val PLACEMENT_STARTED = "m15.foster.placement.started"
    const val EVOLUTION_ADDED = "m15.foster.evolution.added"
    const val PLACEMENT_COMPLETED = "m15.foster.placement.completed"
    const val PLACEMENT_INTERRUPTED = "m15.foster.placement.interrupted"
    const val EXPENSE_RECORDED = "m15.foster.expense.recorded"
    const val HELP_OPENED = "m15.foster.help.opened"
    const val HELP_RESOLVED = "m15.foster.help.resolved"
    const val CUSTODY_REVOKED = "m15.foster.custody.revoked"
}

/** Hooks M06 preparados (sin push real). */
object M15M06Hooks {
    const val HOME_CREATED = "M15_FOSTER_HOME_CREATED"
    const val HOME_ACTIVATED = "M15_FOSTER_HOME_ACTIVATED"
    const val REQUEST_SUBMITTED = "M15_FOSTER_REQUEST_SUBMITTED"
    const val REQUEST_ACCEPTED = "M15_FOSTER_REQUEST_ACCEPTED"
    const val PLACEMENT_STARTED = "M15_FOSTER_PLACEMENT_STARTED"
    const val EVOLUTION_ADDED = "M15_EVOLUTION_ADDED"
    const val PLACEMENT_COMPLETED = "M15_PLACEMENT_COMPLETED"
    const val PLACEMENT_INTERRUPTED = "M15_PLACEMENT_INTERRUPTED"
    const val EXPENSE_RECORDED = "M15_EXPENSE_RECORDED"
    const val HELP_REQUEST_OPENED = "M15_HELP_REQUEST_OPENED"
    const val HELP_REQUEST_RESOLVED = "M15_HELP_REQUEST_RESOLVED"
    const val INFRASTRUCTURE = "M15_NOTIFICATION_INFRASTRUCTURE"

    val all: Set<String> = setOf(
        HOME_CREATED,
        HOME_ACTIVATED,
        REQUEST_SUBMITTED,
        REQUEST_ACCEPTED,
        PLACEMENT_STARTED,
        EVOLUTION_ADDED,
        PLACEMENT_COMPLETED,
        PLACEMENT_INTERRUPTED,
        EXPENSE_RECORDED,
        HELP_REQUEST_OPENED,
        HELP_REQUEST_RESOLVED,
        INFRASTRUCTURE
    )
}

data class M15FosterHome(
    val id: String,
    val ownerUserId: String,
    val displayName: String,
    val description: String? = null,
    val status: M15FosterHomeStatus = M15FosterHomeStatus.DRAFT,
    val availabilityStatus: M15FosterAvailabilityStatus = M15FosterAvailabilityStatus.UNAVAILABLE,
    val totalCapacity: Int,
    val currentOccupancy: Int = 0,
    val reservedCount: Int = 0,
    val acceptedSpecies: Set<String> = emptySet(),
    val acceptedSizes: Set<String> = emptySet(),
    val acceptsSpecialNeeds: Boolean = false,
    val acceptsEmergencies: Boolean = false,
    val zoneText: String,
    val publicLocationText: String? = null,
    /** Never exposed in public listings. */
    val privateAddressText: String? = null,
    val createdAt: Long,
    val updatedAt: Long
) {
    val freeSlots: Int
        get() = (totalCapacity - currentOccupancy - reservedCount).coerceAtLeast(0)

    fun toPublicListing(): M15FosterHomePublicListing = M15FosterHomePublicListing(
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

data class M15FosterHomePublicListing(
    val id: String,
    val displayName: String,
    val description: String?,
    val availabilityStatus: M15FosterAvailabilityStatus,
    val totalCapacity: Int,
    val freeSlots: Int,
    val acceptedSpecies: Set<String>,
    val acceptedSizes: Set<String>,
    val acceptsSpecialNeeds: Boolean,
    val acceptsEmergencies: Boolean,
    val zoneText: String,
    val publicLocationText: String?
)

data class M15FosterRequest(
    val id: String,
    val fosterHomeId: String,
    val petId: String,
    val petName: String? = null,
    val requesterUserId: String? = null,
    val requesterOrganizationId: String? = null,
    val message: String,
    val urgency: M15FosterUrgency = M15FosterUrgency.NORMAL,
    val requestedStartAt: Long? = null,
    val estimatedEndAt: Long? = null,
    val specialNeeds: String? = null,
    val status: M15FosterRequestStatus = M15FosterRequestStatus.SUBMITTED,
    val createdAt: Long,
    val reviewedAt: Long? = null,
    val reviewedBy: String? = null,
    val rejectionReason: String? = null
)

data class M15FosterPlacement(
    val id: String,
    val fosterRequestId: String,
    val fosterHomeId: String,
    val petId: String,
    val petName: String? = null,
    val requesterUserId: String? = null,
    val requesterOrganizationId: String? = null,
    val fosterUserId: String,
    val status: M15FosterPlacementStatus,
    val startedAt: Long,
    val estimatedEndAt: Long? = null,
    val initialNotes: String? = null,
    val endedAt: Long? = null,
    val dischargeReason: M15DischargeReason? = null,
    val dischargeOutcome: M15DischargeOutcome? = null,
    val endNotes: String? = null,
    val endedBy: String? = null
)

data class CreateM15FosterHomeInput(
    val displayName: String,
    val description: String? = null,
    val totalCapacity: Int,
    val acceptedSpecies: Set<String>,
    val acceptedSizes: Set<String>,
    val acceptsSpecialNeeds: Boolean = false,
    val acceptsEmergencies: Boolean = false,
    val zoneText: String,
    val publicLocationText: String? = null,
    val privateAddressText: String? = null,
    val activate: Boolean = false
)

data class UpdateM15FosterHomeInput(
    val homeId: String,
    val displayName: String,
    val description: String? = null,
    val totalCapacity: Int,
    val acceptedSpecies: Set<String>,
    val acceptedSizes: Set<String>,
    val acceptsSpecialNeeds: Boolean = false,
    val acceptsEmergencies: Boolean = false,
    val zoneText: String,
    val publicLocationText: String? = null,
    val privateAddressText: String? = null
)

data class SubmitM15FosterRequestInput(
    val fosterHomeId: String,
    val petId: String,
    val message: String,
    val urgency: M15FosterUrgency = M15FosterUrgency.NORMAL,
    val requestedStartAt: Long? = null,
    val estimatedEndAt: Long? = null,
    val specialNeeds: String? = null,
    val requesterOrganizationId: String? = null
)
