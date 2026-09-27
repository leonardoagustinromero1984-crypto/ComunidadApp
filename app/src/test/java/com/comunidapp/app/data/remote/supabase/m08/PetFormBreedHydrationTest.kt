package com.comunidapp.app.data.remote.supabase.m08

import com.comunidapp.app.data.repository.CatalogBreed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PetFormBreedHydrationTest {

    private val catalogY = listOf(
        CatalogBreed(id = "breed-y", speciesCode = "DOG", name = "Y", sortKey = 1, active = true),
        CatalogBreed(id = "breed-z", speciesCode = "DOG", name = "Z", sortKey = 2, active = true)
    )

    @Test
    fun petFirstCatalogLater_resolvesY() {
        val afterPet = PetFormBreedHydration.resolveSelectedName(
            petBreedName = null,
            petBreedId = "breed-y",
            catalog = emptyList(),
            userChangedBreed = false,
            currentSelection = ""
        )
        assertEquals("", afterPet)
        val afterCatalog = PetFormBreedHydration.resolveSelectedName(
            petBreedName = null,
            petBreedId = "breed-y",
            catalog = catalogY,
            userChangedBreed = false,
            currentSelection = afterPet
        )
        assertEquals("Y", afterCatalog)
    }

    @Test
    fun catalogFirstPetLater_resolvesY() {
        val afterCatalog = PetFormBreedHydration.resolveSelectedName(
            petBreedName = null,
            petBreedId = null,
            catalog = catalogY,
            userChangedBreed = false,
            currentSelection = ""
        )
        assertEquals("", afterCatalog)
        val afterPet = PetFormBreedHydration.resolveSelectedName(
            petBreedName = null,
            petBreedId = "breed-y",
            catalog = catalogY,
            userChangedBreed = false,
            currentSelection = afterCatalog
        )
        assertEquals("Y", afterPet)
    }

    @Test
    fun manualSelectionZ_isNotOverwrittenByLaterCatalog() {
        val resolvedY = PetFormBreedHydration.resolveSelectedName(
            petBreedName = null,
            petBreedId = "breed-y",
            catalog = catalogY,
            userChangedBreed = false,
            currentSelection = ""
        )
        assertEquals("Y", resolvedY)
        val afterManual = PetFormBreedHydration.resolveSelectedName(
            petBreedName = null,
            petBreedId = "breed-y",
            catalog = catalogY,
            userChangedBreed = true,
            currentSelection = "Z"
        )
        assertEquals("Z", afterManual)
        val afterCatalogRefresh = PetFormBreedHydration.resolveSelectedName(
            petBreedName = null,
            petBreedId = "breed-y",
            catalog = catalogY + CatalogBreed("breed-w", "DOG", "W", 3, true),
            userChangedBreed = true,
            currentSelection = "Z"
        )
        assertEquals("Z", afterCatalogRefresh)
    }

    @Test
    fun inactiveBreedId_resolvesFromHistoricalRow_notFromActiveCatalog() {
        val activeOnly = listOf(
            CatalogBreed(id = "breed-z", speciesCode = "DOG", name = "Z", sortKey = 2, active = true)
        )
        val withoutHistory = PetFormBreedHydration.resolveSelectedName(
            petBreedName = null,
            petBreedId = "breed-y",
            catalog = activeOnly,
            userChangedBreed = false,
            currentSelection = ""
        )
        assertEquals("", withoutHistory)
        val historical = CatalogBreed(
            id = "breed-y",
            speciesCode = "DOG",
            name = "Y",
            sortKey = 1,
            active = false
        )
        val withHistory = PetFormBreedHydration.resolveSelectedName(
            petBreedName = null,
            petBreedId = "breed-y",
            catalog = activeOnly + historical,
            userChangedBreed = false,
            currentSelection = ""
        )
        assertEquals("Y", withHistory)
        assertEquals("Y", PetBreedCatalog.nameForId(listOf(historical), "breed-y"))
        assertNull(PetBreedCatalog.nameForId(activeOnly, "breed-y"))
    }
}
