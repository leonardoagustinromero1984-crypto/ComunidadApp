package com.comunidapp.app.data.model

fun M16ShelterService.visibleLabel(): String = when (this) {
    M16ShelterService.ADOPTIONS -> "Adopciones"
    M16ShelterService.TEMPORARY_SHELTER -> "Hogar de tránsito"
    M16ShelterService.RESCUE -> "Rescate"
    M16ShelterService.STERILIZATION -> "Castración"
    M16ShelterService.VETERINARY_CARE -> "Atención veterinaria"
    M16ShelterService.REHABILITATION -> "Rehabilitación"
    M16ShelterService.EDUCATION -> "Educación"
    M16ShelterService.VOLUNTEERING -> "Voluntariado"
    M16ShelterService.OTHER -> "Otro"
}

fun M16ShelterOperationalStatus.visibleLabel(): String = when (this) {
    M16ShelterOperationalStatus.ACTIVE -> "Activos"
    M16ShelterOperationalStatus.PAUSED -> "Pausados"
    M16ShelterOperationalStatus.TEMPORARILY_CLOSED -> "Cierre temporal"
    M16ShelterOperationalStatus.PERMANENTLY_CLOSED -> "Cerrados"
}
