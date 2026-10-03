package com.comunidapp.app.data.model

import com.comunidapp.app.domain.organization.OrganizationPresentation

/** Public service names. Adopciones is the visitor label for ADOPTIONS. */
fun M16ShelterService.visibleLabel(): String = when (this) {
    M16ShelterService.ADOPTIONS -> "Adopciones"
    M16ShelterService.TEMPORARY_SHELTER -> "Tránsito"
    M16ShelterService.RESCUE -> "Rescate"
    M16ShelterService.STERILIZATION -> "Castración"
    M16ShelterService.VETERINARY_CARE -> "Atención veterinaria"
    M16ShelterService.REHABILITATION -> "Rehabilitación"
    M16ShelterService.EDUCATION -> "Educación"
    M16ShelterService.VOLUNTEERING -> "Voluntariado"
    M16ShelterService.OTHER -> "Otra actividad"
}

fun M16ShelterOperationalStatus.visibleLabel(): String = OrganizationPresentation.operationalStatus(this)

fun M16ShelterAvailabilityStatus.visibleLabel(): String = OrganizationPresentation.availability(this)

fun M16ShelterVerificationStatus.publicBadge(): String? = OrganizationPresentation.verifiedBadge(this)
