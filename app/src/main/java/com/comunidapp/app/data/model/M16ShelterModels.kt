package com.comunidapp.app.data.model

import com.comunidapp.app.domain.organization.OrganizationType

/**
 * LeoVer M16 — Refugios (Bloque 1 local).
 * Perfil especializado vinculado a organización M03; legacy M11/Shelter* preservado.
 */

enum class M16ShelterOperationalStatus {
    ACTIVE,
    PAUSED,
    TEMPORARILY_CLOSED,
    PERMANENTLY_CLOSED;

    val isTerminal: Boolean get() = this == PERMANENTLY_CLOSED
    val acceptsIntake: Boolean get() = this == ACTIVE
}

enum class M16ShelterPublicationStatus {
    DRAFT,
    PUBLISHED,
    UNPUBLISHED
}

enum class M16ShelterVerificationStatus {
    UNVERIFIED,
    PENDING,
    VERIFIED,
    REJECTED,
    SUSPENDED
}

enum class M16ShelterAvailabilityStatus {
    AVAILABLE,
    LIMITED,
    FULL,
    UNAVAILABLE
}

enum class M16PublicContactChannelType {
    INSTITUTIONAL_EMAIL,
    INSTITUTIONAL_PHONE,
    WEBSITE,
    SOCIAL,
    MESSAGING
}

enum class M16ShelterService {
    ADOPTIONS,
    TEMPORARY_SHELTER,
    RESCUE,
    STERILIZATION,
    VETERINARY_CARE,
    REHABILITATION,
    EDUCATION,
    VOLUNTEERING,
    OTHER
}

data class M16ShelterCapacity(
    val totalCapacity: Int,
    val currentOccupancy: Int = 0,
    val reservedCount: Int = 0
) {
    val usedSlots: Int get() = currentOccupancy.coerceAtLeast(0) + reservedCount.coerceAtLeast(0)
    val freeSlots: Int get() = (totalCapacity - usedSlots).coerceAtLeast(0)
}

data class M16OpeningPeriod(
    val dayOfWeek: Int,
    val closed: Boolean = false,
    val openTime: String? = null,
    val closeTime: String? = null
)

data class M16OpeningHours(
    val zoneIdName: String = DEFAULT_ZONE,
    val periods: List<M16OpeningPeriod> = emptyList()
) {
    companion object {
        const val DEFAULT_ZONE = "America/Argentina/Buenos_Aires"
    }
}

data class M16PublicContactChannel(
    val type: M16PublicContactChannelType,
    val value: String,
    val label: String? = null
)

data class M16ShelterNeed(
    val category: String,
    val description: String
)

data class M16ShelterProfile(
    val id: String,
    val organizationId: String,
    val displayName: String,
    val description: String? = null,
    val operationalStatus: M16ShelterOperationalStatus = M16ShelterOperationalStatus.ACTIVE,
    val publicationStatus: M16ShelterPublicationStatus = M16ShelterPublicationStatus.DRAFT,
    val verificationStatus: M16ShelterVerificationStatus = M16ShelterVerificationStatus.UNVERIFIED,
    val publicZoneText: String,
    val coverageAreas: Set<String> = emptySet(),
    val openingHours: M16OpeningHours = M16OpeningHours(),
    val acceptedSpecies: Set<String> = emptySet(),
    val services: Set<M16ShelterService> = emptySet(),
    val publicContacts: List<M16PublicContactChannel> = emptyList(),
    val capacity: M16ShelterCapacity,
    val needs: List<M16ShelterNeed> = emptyList(),
    val publicImageRef: String? = null,
    val internalNotes: String? = null,
    val createdAt: Long,
    val updatedAt: Long
) {
    val availability: M16ShelterAvailabilityStatus
        get() = recomputeM16Availability(
            operationalStatus,
            publicationStatus,
            capacity.totalCapacity,
            capacity.currentOccupancy,
            capacity.reservedCount
        )

    fun toPublicShelter(): M16PublicShelter = M16PrivacySanitizer.toPublicShelter(this)
}

data class M16PublicShelter(
    val id: String,
    val displayName: String,
    val description: String?,
    val operationalStatus: M16ShelterOperationalStatus,
    val publicationStatus: M16ShelterPublicationStatus,
    val verificationStatus: M16ShelterVerificationStatus,
    val publicZoneText: String,
    val coverageAreas: Set<String>,
    val openingHours: M16OpeningHours,
    val acceptedSpecies: Set<String>,
    val services: Set<M16ShelterService>,
    val publicContacts: List<M16PublicContactChannel>,
    val totalCapacity: Int,
    val freeSlotsApproximate: Int,
    val availability: M16ShelterAvailabilityStatus,
    val needs: List<M16ShelterNeed>,
    val publicImageRef: String?
)

data class M16ShelterSummary(
    val id: String,
    val displayName: String,
    val operationalStatus: M16ShelterOperationalStatus,
    val publicZoneText: String,
    val services: Set<M16ShelterService>,
    val acceptedSpecies: Set<String>,
    val availability: M16ShelterAvailabilityStatus,
    val verificationStatus: M16ShelterVerificationStatus,
    val publicImageRef: String?
)

data class M16ShelterSearchFilter(
    val query: String = "",
    val species: String? = null,
    val service: M16ShelterService? = null,
    val verifiedOnly: Boolean = false,
    val operationalStatus: M16ShelterOperationalStatus? = null
)

data class CreateM16ShelterProfileInput(
    val organizationId: String,
    val displayName: String,
    val description: String? = null,
    val publicZoneText: String,
    val totalCapacity: Int,
    val acceptedSpecies: Set<String> = emptySet(),
    val services: Set<M16ShelterService> = emptySet(),
    val publish: Boolean = false
)

data class UpdateM16ShelterPublicInput(
    val shelterId: String,
    val displayName: String,
    val description: String? = null,
    val publicZoneText: String,
    val coverageAreas: Set<String> = emptySet(),
    val acceptedSpecies: Set<String> = emptySet(),
    val publicImageRef: String? = null
)

object M16PermissionCodes {
    const val SHELTER_PUBLIC_READ = "shelter.public.read"
    const val SHELTER_MANAGE = "shelter.manage"
}

object M16M06Hooks {
    const val PROFILE_CREATED = "M16_SHELTER_PROFILE_CREATED"
    const val PROFILE_PUBLISHED = "M16_SHELTER_PROFILE_PUBLISHED"
    const val VERIFICATION_REQUESTED = "M16_SHELTER_VERIFICATION_REQUESTED"
    const val INFRASTRUCTURE = "M16_NOTIFICATION_INFRASTRUCTURE"
}

/** Tipos M03 elegibles para perfil de refugio M16. */
val M16_ELIGIBLE_ORGANIZATION_TYPES: Set<OrganizationType> = setOf(
    OrganizationType.SHELTER,
    OrganizationType.RESCUE_GROUP,
    OrganizationType.NGO
)

fun recomputeM16Availability(
    operational: M16ShelterOperationalStatus,
    publication: M16ShelterPublicationStatus,
    capacity: Int,
    occupancy: Int,
    reserved: Int
): M16ShelterAvailabilityStatus {
    if (operational != M16ShelterOperationalStatus.ACTIVE ||
        publication != M16ShelterPublicationStatus.PUBLISHED
    ) {
        return M16ShelterAvailabilityStatus.UNAVAILABLE
    }
    val used = occupancy.coerceAtLeast(0) + reserved.coerceAtLeast(0)
    return when {
        used >= capacity.coerceAtLeast(0) -> M16ShelterAvailabilityStatus.FULL
        used > 0 -> M16ShelterAvailabilityStatus.LIMITED
        else -> M16ShelterAvailabilityStatus.AVAILABLE
    }
}

object M16PrivacySanitizer {
    private val emailPattern = Regex("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}")
    private val phonePattern = Regex("(?i)(\\+?\\d[\\d\\s().-]{6,}\\d)")

    fun scrubPublicText(text: String): String =
        text.replace(emailPattern, "[redactado]").replace(phonePattern, "[redactado]")

    fun toPublicShelter(profile: M16ShelterProfile): M16PublicShelter = M16PublicShelter(
        id = profile.id,
        displayName = scrubPublicText(profile.displayName),
        description = profile.description?.let { scrubPublicText(it) },
        operationalStatus = profile.operationalStatus,
        publicationStatus = profile.publicationStatus,
        verificationStatus = profile.verificationStatus,
        publicZoneText = scrubPublicText(profile.publicZoneText),
        coverageAreas = profile.coverageAreas.map { scrubPublicText(it) }.toSet(),
        openingHours = profile.openingHours,
        acceptedSpecies = profile.acceptedSpecies,
        services = profile.services,
        publicContacts = profile.publicContacts,
        totalCapacity = profile.capacity.totalCapacity,
        freeSlotsApproximate = profile.capacity.freeSlots,
        availability = profile.availability,
        needs = profile.needs.map { it.copy(description = scrubPublicText(it.description)) },
        publicImageRef = profile.publicImageRef
    )

    fun toSummary(public: M16PublicShelter): M16ShelterSummary = M16ShelterSummary(
        id = public.id,
        displayName = public.displayName,
        operationalStatus = public.operationalStatus,
        publicZoneText = public.publicZoneText,
        services = public.services,
        acceptedSpecies = public.acceptedSpecies,
        availability = public.availability,
        verificationStatus = public.verificationStatus,
        publicImageRef = public.publicImageRef
    )
}
