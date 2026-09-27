package com.comunidapp.app.domain.pets

/**
 * Red de cuidados: PERSON↔PERSON scoped to a pet/VitaCora.
 * Canonical link role stays AUTHORIZED; [CareNetworkRole] is the human label.
 */
enum class CareNetworkRole {
    FAMILY,
    CAREGIVER,
    TRUSTED,
    OTHER;

    fun label(): String = when (this) {
        FAMILY -> "Familiar"
        CAREGIVER -> "Cuidador/a"
        TRUSTED -> "Persona de confianza"
        OTHER -> "Otro"
    }

    companion object {
        fun fromRaw(raw: String?): CareNetworkRole? =
            raw?.trim()?.uppercase()?.let { value ->
                entries.find { it.name == value }
            }
    }
}

data class CareNetworkInvite(
    val linkId: String,
    val petId: String,
    val petName: String,
    val ownerName: String,
    val role: CareNetworkRole,
    val status: String
)

data class CareNetworkPet(
    val linkId: String,
    val petId: String,
    val petName: String,
    val role: CareNetworkRole,
    val ownerName: String,
    val speciesLabel: String = "",
    val photoUrl: String? = null
)

object CareNetworkRules {
    const val LINK_ROLE = "AUTHORIZED"
    const val STATUS_PENDING = "PENDING"
    const val STATUS_ACTIVE = "ACTIVE"
    const val STATUS_ENDED = "ENDED"

    fun canViewVitacora(status: String): Boolean =
        status.equals(STATUS_ACTIVE, ignoreCase = true)

    fun canDeletePet(isOwner: Boolean, viaCareMembership: Boolean): Boolean =
        isOwner && !viaCareMembership

    fun visibleInPersonContext(managementContextKind: String?): Boolean {
        val kind = managementContextKind?.trim()?.uppercase().orEmpty()
        return kind.isEmpty() || kind == PetManagementContext.PERSON
    }
}
