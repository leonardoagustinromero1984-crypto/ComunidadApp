package com.comunidapp.app.data.model

/**
 * LeoVer M09 — postulación a una publicación de adopción.
 * La aceptación de un candidato no finaliza la adopción.
 */
data class AdoptionApplication(
    val id: String,
    val adoptionId: String,
    val applicantUserId: String,
    val applicantName: String = "",
    val message: String,
    val housingType: String? = null,
    val hasOtherPets: Boolean? = null,
    val previousExperience: String? = null,
    val contactPhone: String? = null,
    val status: AdoptionApplicationStatus = AdoptionApplicationStatus.SUBMITTED,
    val submittedAt: Long,
    val reviewedAt: Long? = null,
    val reviewedBy: String? = null,
    val rejectionReason: String? = null,
    val adoptionTitle: String = "",
    val petName: String = "",
    val petPhotoUrl: String? = null,
    val createdAt: Long? = null,
    val updatedAt: Long? = null
) {
    val isActive: Boolean
        get() = AdoptionApplicationStatus.isActive(status)

    fun messagePreview(maxLen: Int = 80): String {
        val trimmed = message.trim()
        return if (trimmed.length <= maxLen) trimmed else trimmed.take(maxLen - 1) + "…"
    }

    /** Teléfono solo para postulante o gestor autorizado. */
    fun visibleContactPhone(viewerUserId: String?, isManager: Boolean): String? {
        if (contactPhone.isNullOrBlank()) return null
        val viewer = viewerUserId.orEmpty()
        if (viewer.isBlank()) return null
        if (viewer == applicantUserId || isManager) return contactPhone
        return null
    }
}

enum class AdoptionApplicationStatus {
    SUBMITTED,
    UNDER_REVIEW,
    ACCEPTED,
    REJECTED,
    WITHDRAWN;

    val displayNameEs: String
        get() = when (this) {
            SUBMITTED -> "Enviada"
            UNDER_REVIEW -> "En revisión"
            ACCEPTED -> "Aceptada"
            REJECTED -> "Rechazada"
            WITHDRAWN -> "Retirada"
        }

    companion object {
        fun fromString(value: String?): AdoptionApplicationStatus {
            val raw = value?.trim()?.uppercase().orEmpty()
            return when (raw) {
                "PENDING" -> SUBMITTED // legacy adoption_requests
                else -> entries.find { it.name == raw } ?: SUBMITTED
            }
        }

        fun isActive(status: AdoptionApplicationStatus): Boolean =
            status == SUBMITTED || status == UNDER_REVIEW || status == ACCEPTED
    }
}
