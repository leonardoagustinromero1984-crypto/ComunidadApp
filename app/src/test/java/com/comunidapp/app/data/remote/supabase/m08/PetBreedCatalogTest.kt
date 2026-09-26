package com.comunidapp.app.data.remote.supabase.m08

import com.comunidapp.app.data.repository.CatalogBreed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PetBreedCatalogTest {

    private val breeds = listOf(
        CatalogBreed(id = "breed-y", speciesCode = "DOG", name = "Y", sortKey = 1, active = true),
        CatalogBreed(id = "breed-z", speciesCode = "DOG", name = "Z", sortKey = 2, active = true)
    )

    @Test
    fun idForName_matchesExistingBreed() {
        assertEquals("breed-y", PetBreedCatalog.idForName(breeds, "Y"))
        assertEquals("breed-z", PetBreedCatalog.idForName(breeds, " z "))
    }

    @Test
    fun nameForId_roundTrips() {
        assertEquals("Y", PetBreedCatalog.nameForId(breeds, "breed-y"))
        assertNull(PetBreedCatalog.nameForId(breeds, null))
        assertNull(PetBreedCatalog.idForName(breeds, "   "))
    }
}
