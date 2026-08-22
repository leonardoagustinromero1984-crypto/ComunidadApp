package com.comunidapp.app.domain.pets

import com.comunidapp.app.data.model.Pet

object PetListMerge {
    fun withCreated(existing: List<Pet>, created: Pet): List<Pet> {
        if (created.id.isBlank()) return existing
        if (existing.any { it.id == created.id }) return existing
        return listOf(created) + existing
    }
}
