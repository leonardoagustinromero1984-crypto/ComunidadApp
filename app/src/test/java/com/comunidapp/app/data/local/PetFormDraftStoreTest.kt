package com.comunidapp.app.data.local

import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.viewmodel.PetFormUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PetFormDraftStoreTest {

    @Test
    fun staleEmptyDraftDoesNotRecoverEditForNamedPet() {
        val fetched = PetFormUiState(
            isLoading = false,
            isEditMode = true,
            petId = "pet-1",
            name = "Samu",
            species = PetSpecies.DOG,
            sex = PetSex.MALE
        )
        val stale = PetFormDraft(
            userId = "user-1",
            editPetId = "pet-1",
            name = ""
        )
        assertFalse(stale.isRecoverableForEdit(fetched))
        assertEquals("Samu", stale.applyTo(fetched).name)
    }

    @Test
    fun deliberateDraftWithEditsRecovers() {
        val fetched = PetFormUiState(
            isLoading = false,
            isEditMode = true,
            petId = "pet-1",
            name = "Samu",
            species = PetSpecies.DOG,
            sex = PetSex.MALE,
            size = PetSize.MEDIUM
        )
        val draft = PetFormDraft(
            userId = "user-1",
            editPetId = "pet-1",
            name = "Samu",
            species = PetSpecies.CAT.name
        )
        assertTrue(draft.isRecoverableForEdit(fetched))
        assertEquals(PetSpecies.CAT, draft.applyTo(fetched).species)
    }
}
