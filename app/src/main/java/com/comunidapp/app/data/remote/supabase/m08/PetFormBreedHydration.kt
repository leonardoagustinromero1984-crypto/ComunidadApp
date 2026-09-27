package com.comunidapp.app.data.remote.supabase.m08

import com.comunidapp.app.data.repository.CatalogBreed

/**
 * Resolves the breed dropdown once both pet.breedId and the catalog are available.
 * A later catalog refresh must not overwrite a manual user selection.
 */
object PetFormBreedHydration {
    fun resolveSelectedName(
        petBreedName: String?,
        petBreedId: String?,
        catalog: List<CatalogBreed>,
        userChangedBreed: Boolean,
        currentSelection: String
    ): String {
        if (userChangedBreed) return currentSelection.trim()
        val fromId = PetBreedCatalog.nameForId(catalog, petBreedId)
        if (!fromId.isNullOrBlank()) return fromId
        val fromPet = petBreedName?.trim().orEmpty()
        if (fromPet.isNotEmpty()) {
            val canonical = PetBreedCatalog.idForName(catalog, fromPet)
                ?.let { PetBreedCatalog.nameForId(catalog, it) }
            return canonical ?: fromPet
        }
        return currentSelection.trim()
    }
}
