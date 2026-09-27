package com.comunidapp.app.data.remote.supabase.m08

import com.comunidapp.app.data.remote.supabase.m08.PetM08Mappers.toProfilePet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ConnectionPetProfileMapperTest {

    @Test
    fun exactBirthDate_mapsRealAgeInsteadOfZeroMonths() {
        val row = ProfilePetRow(
            id = "pet-1",
            name = "Luna",
            speciesCode = "DOG",
            sex = "FEMALE",
            birthPrecision = "EXACT_DATE",
            birthDate = LocalDate.now().minusYears(4).toString()
        )

        val pet = row.toProfilePet()

        assertEquals(4, pet.ageYears)
        assertEquals("EXACT_DATE", pet.birthPrecision)
    }

    @Test
    fun unknownBirth_doesNotInventAgeData() {
        val pet = ProfilePetRow(
            id = "pet-2",
            name = "Mora",
            speciesCode = "DOG"
        ).toProfilePet()

        assertEquals(0, pet.ageYears)
        assertEquals(0, pet.ageMonths)
        assertEquals("UNKNOWN", pet.birthPrecision)
        assertNull(pet.birthDate)
    }
}
