package com.comunidapp.app.domain.pets

import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.remote.supabase.m08.PetAccessContext

/**
 * Edit gate for the pet form.
 * OWNER / PRINCIPAL ACTIVE on the current PERSON may edit.
 * A secondary access-context RPC is not required to recognize an OWNER
 * that already fetched the pet.
 */
object PetEditAuthorization {

    enum class Decision {
        CAN_EDIT,
        NO_PERMISSION,
        PET_NOT_FOUND
    }

    fun decide(
        pet: Pet?,
        context: PetAccessContext?,
        sessionUserId: String?,
        sessionPersonId: String? = sessionUserId
    ): Decision {
        if (pet == null) return Decision.PET_NOT_FOUND
        if (isOwnerOrPrincipal(pet, context, sessionUserId, sessionPersonId)) {
            return Decision.CAN_EDIT
        }
        if (context == null) return Decision.CAN_EDIT
        if (context.canUpdate) return Decision.CAN_EDIT
        if (isOwnerPrincipalRelation(context.relationCode)) return Decision.CAN_EDIT
        val foreign = context.relationCode.trim().uppercase() in setOf("NONE", "AUTHORIZED")
        val principal = context.principalPersonId?.trim().orEmpty()
        val identities = listOfNotNull(sessionUserId, sessionPersonId)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        if (foreign && principal.isNotEmpty() && principal !in identities) {
            return Decision.NO_PERMISSION
        }
        return Decision.CAN_EDIT
    }

    fun isOwnerOrPrincipal(
        pet: Pet,
        context: PetAccessContext?,
        sessionUserId: String?,
        sessionPersonId: String? = sessionUserId
    ): Boolean {
        if (isOwnerPrincipalRelation(context?.relationCode)) return true
        val identities = listOfNotNull(
            sessionUserId?.takeIf { it.isNotBlank() },
            sessionPersonId?.takeIf { it.isNotBlank() }
        ).toSet()
        if (identities.isEmpty()) return false
        if (pet.ownerId in identities) return true
        if (pet.createdByUserId in identities) return true
        if (pet.ownerIds.any { it in identities }) return true
        if (context?.principalPersonId in identities) return true
        return false
    }

    fun isOwnerPrincipalRelation(relationCode: String?): Boolean {
        val code = relationCode?.trim()?.uppercase().orEmpty()
        return code == "OWNER" || code == "PRINCIPAL"
    }
}
