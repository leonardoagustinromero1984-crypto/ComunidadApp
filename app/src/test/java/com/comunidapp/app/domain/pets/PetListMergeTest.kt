package com.comunidapp.app.domain.pets

import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PetListMergeTest {

    private fun pet(id: String, name: String = "Luna") = Pet(
        id = id,
        name = name,
        species = PetSpecies.DOG,
        sex = PetSex.FEMALE,
        ageYears = 1,
        size = PetSize.MEDIUM,
        description = ""
    )

    @Test
    fun created_petId_appears_in_list() {
        val merged = PetListMerge.withCreated(emptyList(), pet("p1"))
        assertEquals(listOf("p1"), merged.map { it.id })
    }

    @Test
    fun existing_id_does_not_duplicate() {
        val merged = PetListMerge.withCreated(listOf(pet("p1")), pet("p1", "Luna 2"))
        assertEquals(1, merged.size)
        assertEquals("Luna", merged.first().name)
    }

    @Test
    fun blank_id_is_not_treated_as_success_row() {
        val merged = PetListMerge.withCreated(emptyList(), pet(""))
        assertTrue(merged.isEmpty())
    }
}
