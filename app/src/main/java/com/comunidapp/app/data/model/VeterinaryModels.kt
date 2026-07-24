package com.comunidapp.app.data.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

/**
 * LeoVer M12 — dominio inicial de veterinarias (Bloque 1, local/fake).
 * Independiente del legacy [ServiceProfile] / tabla `service_profiles`.
 */

enum class VeterinaryClinicStatus {
    DRAFT, ACTIVE, PAUSED, SUSPENDED, ARCHIVED, UNKNOWN;

    companion object {
        fun fromString(value: String?): VeterinaryClinicStatus =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }

    val isPubliclyListable: Boolean get() = this == ACTIVE
}

enum class VeterinaryVerificationStatus {
    UNVERIFIED, PENDING, VERIFIED, REJECTED, SUSPENDED, UNKNOWN;

    companion object {
        fun fromString(value: String?): VeterinaryVerificationStatus =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

enum class VeterinaryProfessionalStatus {
    ACTIVE, INACTIVE, SUSPENDED, ARCHIVED, UNKNOWN;

    companion object {
        fun fromString(value: String?): VeterinaryProfessionalStatus =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

enum class VeterinarySpecialty {
    GENERAL_MEDICINE,
    EMERGENCY_AND_CRITICAL_CARE,
    SURGERY,
    INTERNAL_MEDICINE,
    DERMATOLOGY,
    CARDIOLOGY,
    NEUROLOGY,
    ONCOLOGY,
    OPHTHALMOLOGY,
    TRAUMATOLOGY,
    REPRODUCTION,
    EXOTIC_ANIMALS,
    DENTISTRY,
    DIAGNOSTIC_IMAGING,
    LABORATORY,
    OTHER,
    UNKNOWN;

    companion object {
        fun fromString(value: String?): VeterinarySpecialty =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

enum class VeterinaryServiceCategory {
    CONSULTATION,
    VACCINATION,
    SURGERY,
    HOSPITALIZATION,
    LABORATORY,
    DIAGNOSTIC_IMAGING,
    EMERGENCY_GUARD,
    PREVENTIVE_CARE,
    DENTISTRY,
    PHARMACY,
    OTHER,
    UNKNOWN;

    companion object {
        fun fromString(value: String?): VeterinaryServiceCategory =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

/** Especies admitidas en servicios veterinarios M12 (independiente de [PetSpecies]). */
enum class AnimalSpecies {
    DOG, CAT, BIRD, RABBIT, RODENT, REPTILE, HORSE, EXOTIC, OTHER, UNKNOWN;

    companion object {
        fun fromString(value: String?): AnimalSpecies =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

data class VeterinaryClinicProfile(
    val id: String,
    val organizationId: String,
    val branchId: String? = null,
    val displayName: String,
    val description: String? = null,
    val status: VeterinaryClinicStatus = VeterinaryClinicStatus.DRAFT,
    val verificationStatus: VeterinaryVerificationStatus = VeterinaryVerificationStatus.UNVERIFIED,
    val publicZoneText: String,
    val publicAddressText: String? = null,
    val publicPhone: String? = null,
    val publicEmail: String? = null,
    val websiteUrl: String? = null,
    val socialLinks: Map<String, String> = emptyMap(),
    val logoAssetRef: String? = null,
    val coverAssetRef: String? = null,
    val offersEmergencyCare: Boolean = false,
    val isOpen24Hours: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant
)

/** Proyección pública: sin dirección privada ni notas internas. */
data class VeterinaryPublicListing(
    val id: String,
    val organizationId: String,
    val displayName: String,
    val description: String? = null,
    val publicZoneText: String,
    val verificationStatus: VeterinaryVerificationStatus,
    val specialties: Set<VeterinarySpecialty>,
    val serviceCategories: Set<VeterinaryServiceCategory>,
    val offersEmergencyCare: Boolean,
    val isOpen24Hours: Boolean,
    val logoAssetRef: String? = null,
    val status: VeterinaryClinicStatus
)

data class VeterinaryProfessional(
    val id: String,
    val userId: String? = null,
    val clinicId: String? = null,
    val displayName: String,
    val licenseNumber: String? = null,
    val licenseJurisdiction: String? = null,
    val verificationStatus: VeterinaryVerificationStatus = VeterinaryVerificationStatus.UNVERIFIED,
    val biography: String? = null,
    val specialties: Set<VeterinarySpecialty> = emptySet(),
    val publicContactEnabled: Boolean = false,
    val publicPhone: String? = null,
    val publicEmail: String? = null,
    val avatarAssetRef: String? = null,
    val status: VeterinaryProfessionalStatus = VeterinaryProfessionalStatus.ACTIVE
)

data class VeterinaryService(
    val id: String,
    val clinicId: String,
    val name: String,
    val category: VeterinaryServiceCategory,
    val description: String? = null,
    val species: Set<AnimalSpecies> = emptySet(),
    val active: Boolean = true,
    val requiresAppointment: Boolean = true,
    val emergencyAvailable: Boolean = false
)

data class VeterinaryOpeningHours(
    val clinicId: String,
    val dayOfWeek: DayOfWeek,
    val closed: Boolean = false,
    val opensAt: LocalTime? = null,
    val closesAt: LocalTime? = null,
    val emergencyOnly: Boolean = false
)

data class VeterinaryDirectoryFilter(
    val query: String? = null,
    val zoneText: String? = null,
    val specialty: VeterinarySpecialty? = null,
    val serviceCategory: VeterinaryServiceCategory? = null,
    val emergencyCareOnly: Boolean = false,
    val open24HoursOnly: Boolean = false,
    val verifiedOnly: Boolean = false
)

/** Identificadores de permiso M12 (registro de dominio; sin seeds SQL en Bloque 1). */
object VeterinaryPermissionCodes {
    const val PROFILE_READ = "veterinary.profile.read"
    const val PROFILE_MANAGE = "veterinary.profile.manage"
    const val PROFESSIONAL_READ = "veterinary.professional.read"
    const val PROFESSIONAL_MANAGE = "veterinary.professional.manage"
    const val SERVICE_READ = "veterinary.service.read"
    const val SERVICE_MANAGE = "veterinary.service.manage"

    val all: Set<String> = setOf(
        PROFILE_READ,
        PROFILE_MANAGE,
        PROFESSIONAL_READ,
        PROFESSIONAL_MANAGE,
        SERVICE_READ,
        SERVICE_MANAGE
    )
}

object VeterinaryAuditEvents {
    const val VETERINARY_CLINIC_DRAFT_CREATED = "VETERINARY_CLINIC_DRAFT_CREATED"
    const val VETERINARY_CLINIC_PROFILE_UPDATED = "VETERINARY_CLINIC_PROFILE_UPDATED"
    const val VETERINARY_PROFESSIONAL_LINKED = "VETERINARY_PROFESSIONAL_LINKED"
    const val VETERINARY_SERVICE_UPDATED = "VETERINARY_SERVICE_UPDATED"
}

object VeterinaryM06Hooks {
    const val VETERINARY_CLINIC_DRAFT_CREATED = "VETERINARY_CLINIC_DRAFT_CREATED"
    const val VETERINARY_CLINIC_PROFILE_UPDATED = "VETERINARY_CLINIC_PROFILE_UPDATED"
    const val VETERINARY_PROFESSIONAL_LINKED = "VETERINARY_PROFESSIONAL_LINKED"
    const val VETERINARY_SERVICE_UPDATED = "VETERINARY_SERVICE_UPDATED"
}
