package com.comunidapp.app.domain.pets

import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PetHealthPersistTest {

    @Test
    fun emptyPetHasNoHealthToPersist() {
        val pet = Pet(
            id = "p1",
            name = "Samu",
            species = PetSpecies.DOG,
            sex = PetSex.MALE,
            ageYears = 2,
            ageMonths = 0,
            size = PetSize.MEDIUM,
            description = ""
        )
        assertFalse(PetHealthPersist.hasData(pet))
    }

    @Test
    fun sterilizedAloneIsHealthData() {
        val pet = Pet(
            id = "p1",
            name = "Samu",
            species = PetSpecies.DOG,
            sex = PetSex.MALE,
            ageYears = 2,
            ageMonths = 0,
            size = PetSize.MEDIUM,
            description = "",
            sterilized = com.comunidapp.app.data.model.SterilizationStatus.YES
        )
        assertTrue(PetHealthPersist.hasData(pet))
    }
}
