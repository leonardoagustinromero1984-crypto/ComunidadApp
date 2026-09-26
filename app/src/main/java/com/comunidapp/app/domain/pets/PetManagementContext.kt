package com.comunidapp.app.domain.pets

import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.domain.context.OperationalContext

/**
 * Management context is distinct from OWNER/PRINCIPAL ownership.
 * A pet created in PERSONA stays visible in PERSONA; switching to VETERINARIA
 * does not list it by default.
 */
object PetManagementContext {
    const val PERSON = "PERSON"
    const val ORGANIZATION = "ORGANIZATION"
    const val PROVIDER = "PROVIDER"

    data class Key(val kind: String, val id: String?)

    fun keyOf(context: OperationalContext, userId: String): Key = when (context) {
        is OperationalContext.Personal,
        is OperationalContext.Rescuer,
        is OperationalContext.Foster -> Key(PERSON, userId)
        is OperationalContext.Veterinary,
        is OperationalContext.Shop,
        is OperationalContext.Organization -> Key(ORGANIZATION, context.entityId)
        is OperationalContext.Provider -> Key(PROVIDER, context.entityId)
    }

    fun visibleIn(
        pet: Pet,
        context: OperationalContext,
        userId: String
    ): Boolean {
        val want = keyOf(context, userId)
        val kind = pet.managementContextKind?.trim()?.uppercase().orEmpty().ifBlank { PERSON }
        val id = pet.managementContextId?.trim()?.takeIf { it.isNotEmpty() }
            ?: pet.createdByUserId?.trim()?.takeIf { it.isNotEmpty() }
            ?: pet.ownerId?.trim()?.takeIf { it.isNotEmpty() }
        if (kind != want.kind) return false
        return when (want.kind) {
            // Accessible list is already holder-scoped. Shared PERSON pets keep
            // the original managementContextId, so do not require it == viewer.
            PERSON -> true
            else -> !want.id.isNullOrBlank() && id == want.id
        }
    }

    fun filter(
        pets: List<Pet>,
        context: OperationalContext,
        userId: String?
    ): List<Pet> {
        if (userId.isNullOrBlank()) return emptyList()
        return pets.filter { visibleIn(it, context, userId) }
    }
}
