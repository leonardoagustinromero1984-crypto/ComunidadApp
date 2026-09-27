package com.comunidapp.app.data.remote.supabase.m08

import com.comunidapp.app.data.repository.CatalogBreed

object PetBreedCatalog {
    fun idForName(breeds: List<CatalogBreed>, name: String?): String? {
        val needle = name?.trim().orEmpty()
        if (needle.isEmpty()) return null
        return breeds.firstOrNull { it.name.equals(needle, ignoreCase = true) }?.id
    }

    fun nameForId(breeds: List<CatalogBreed>, id: String?): String? {
        val needle = id?.trim().orEmpty()
        if (needle.isEmpty()) return null
        return breeds.firstOrNull { it.id == needle }?.name
    }
}
