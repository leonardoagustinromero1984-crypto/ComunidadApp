package com.comunidapp.app.ui.components

import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ConnectedPetAgeDisplayTest {

    private fun pet(
        birthPrecision: String = "UNKNOWN",
        birthDate: String? = null,
        ageYears: Int = 0,
        ageMonths: Int = 0
    ) = Pet(
        id = "pet",
        name = "Luna",
        species = PetSpecies.DOG,
        sex = PetSex.FEMALE,
        ageYears = ageYears,
        ageMonths = ageMonths,
        size = PetSize.MEDIUM,
        description = "",
        birthPrecision = birthPrecision,
        birthDate = birthDate
    )

    @Test
    fun unknownAge_doesNotRenderZeroMonths() {
        assertEquals("Edad desconocida", pet().ageDisplay())
    }

    @Test
    fun exactOneYear_usesSingular() {
        val value = pet(
            birthPrecision = "EXACT_DATE",
            birthDate = LocalDate.now().minusYears(1).toString()
        ).ageDisplay()

        assertEquals("1 año", value)
    }

    @Test
    fun legacyOneMonth_usesSingular() {
        assertEquals("1 mes", pet(ageMonths = 1).ageDisplay())
    }
}
