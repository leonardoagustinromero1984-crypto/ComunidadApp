package com.comunidapp.app.domain.canonical

import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalVitaCoraProjectionTest {

    @Test
    fun projectsLivePetWithoutPassportSnapshot() {
        val pet = Pet(
            id = "pet-1",
            name = "Luna",
            species = PetSpecies.DOG,
            sex = PetSex.FEMALE,
            ageYears = 2,
            size = PetSize.MEDIUM,
            description = "",
            healthNotes = "alergia al pollo",
            publicCode = "ABC123"
        )
        val projection = CanonicalVitaCoraProjection.fromPet(pet)
        assertEquals("pet-1", projection?.id)
        assertEquals("pet-1", projection?.petId)
        assertEquals("Luna", projection?.displayName)
        assertEquals("ABC123", projection?.publicCode)
        assertEquals("alergia al pollo", projection?.distinctiveMarks)
        assertNull(projection?.microchipNumber)
    }

    @Test
    fun blankPetIsNotProjected() {
        assertNull(CanonicalVitaCoraProjection.fromPet(null))
        assertTrue(
            CanonicalVitaCoraProjection.fromPet(
                Pet(
                    id = "",
                    name = "X",
                    species = PetSpecies.OTHER,
                    sex = PetSex.UNKNOWN,
                    ageYears = 0,
                    size = PetSize.MEDIUM,
                    description = ""
                )
            ) == null
        )
    }
}
