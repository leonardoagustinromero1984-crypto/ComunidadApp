package com.comunidapp.app.domain.pets

import com.comunidapp.app.data.model.Pet

object LostPetSelector {
    fun selectable(pets: List<Pet>): List<Pet> = pets.filter { it.isSelectableForLost() }

    fun Pet.isSelectableForLost(): Boolean {
        if (!status.equals("ACTIVE", ignoreCase = true)) return false
        if (archivedAt != null || deceasedAt != null) return false
        if (originKind.equals("FOUND_CASE", ignoreCase = true)) return false
        if (originKind.equals("IMPORT", ignoreCase = true) && name.equals("Sin nombre", ignoreCase = true)) {
            return false
        }
        return true
    }
}
