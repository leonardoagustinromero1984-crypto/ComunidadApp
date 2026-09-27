package com.comunidapp.app.domain.pets

import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetSpecies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PetInternalIdTest {

    private val petUuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
    private val publicCode = "LV-TEST-CODE"

    private val accessible = listOf(
        Pet(
            id = petUuid,
            name = "Luna",
            species = PetSpecies.DOG,
            sex = com.comunidapp.app.data.model.PetSex.FEMALE,
            ageYears = 2,
            size = com.comunidapp.app.data.model.PetSize.MEDIUM,
            description = "",
            publicCode = publicCode,
            publicVitacoraNumber = 12345L
        )
    )

    @Test
    fun acceptsInternalUuidForEdit() {
        assertEquals(petUuid, PetInternalId.resolveForEdit(petUuid, accessible))
    }

    @Test
    fun neverUsesPublicCodeAsPetIdForRepositoryFetch() {
        assertNull(PetInternalId.parseUuid(publicCode))
        assertNull(PetInternalId.resolveForEdit(publicCode, emptyList()))
    }

    @Test
    fun resolvesPublicCodeOnlyFromAccessiblePetsForNavigationRecovery() {
        assertEquals(petUuid, PetInternalId.resolveForEdit(publicCode, accessible))
        assertEquals(petUuid, PetInternalId.resolveForEdit("12345", accessible))
    }
}
