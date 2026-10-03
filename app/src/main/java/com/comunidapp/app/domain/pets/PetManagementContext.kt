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
            // Do not require managementContextId == viewer: a shared PERSON pet
            // keeps the original context id. Visibility still requires the viewer
            // to be the access subject, a holder, or an explicit co-owner.
            PERSON -> viewerMaySeePersonPet(pet, userId)
            else -> !want.id.isNullOrBlank() && id == want.id
        }
    }

    /**
     * PERSON list rule.
     * A non-blank [Pet.accessSubjectUserId] means the row was returned by
     * listAccessiblePets for that uid (including a legitimate share).
     * Without that stamp, the viewer must be the current holder, creator,
     * management context, or an explicit member of [Pet.ownerIds].
     */
    private fun viewerMaySeePersonPet(pet: Pet, userId: String): Boolean {
        if (userId.isBlank()) return false
        val subject = pet.accessSubjectUserId?.trim()?.takeIf { it.isNotEmpty() }
        if (subject != null) return subject == userId
        if (userId == pet.ownerId?.trim()) return true
        if (userId == pet.createdByUserId?.trim()) return true
        if (userId == pet.managementContextId?.trim()) return true
        return pet.ownerIds.any { it.trim() == userId }
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
