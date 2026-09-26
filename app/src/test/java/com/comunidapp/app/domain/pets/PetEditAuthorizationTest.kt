package com.comunidapp.app.domain.pets

import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.remote.supabase.m08.PetAccessContext
import org.junit.Assert.assertEquals
import org.junit.Test

class PetEditAuthorizationTest {

    private val ownerId = "person-owner"
    private val pet = Pet(
        id = "pet-1",
        ownerId = ownerId,
        name = "Lolo",
        species = PetSpecies.DOG,
        sex = PetSex.MALE,
        ageYears = 2,
        size = PetSize.MEDIUM,
        description = "",
        createdByUserId = ownerId
    )

    @Test
    fun ownerActiveCanEditEvenIfContextRpcSaysCannotUpdate() {
        val context = PetAccessContext(
            petId = pet.id,
            relationCode = "OWNER",
            principalPersonId = ownerId,
            principalOrganizationId = null,
            capabilities = emptyList(),
            canRead = true,
            canUpdate = false,
            canManageHealth = false,
            canManageMedia = false,
            canArchive = false,
            canMarkDeceased = false
        )
        val decision = PetEditAuthorization.decide(
            pet = pet,
            context = context,
            sessionUserId = ownerId
        )
        assertEquals(PetEditAuthorization.Decision.CAN_EDIT, decision)
    }

    @Test
    fun ownerCanEditWhenSecondaryContextRpcIsMissing() {
        val decision = PetEditAuthorization.decide(
            pet = pet,
            context = null,
            sessionUserId = ownerId
        )
        assertEquals(PetEditAuthorization.Decision.CAN_EDIT, decision)
    }

    @Test
    fun userWithoutResponsibilityCannotEdit() {
        val context = PetAccessContext(
            petId = pet.id,
            relationCode = "NONE",
            principalPersonId = ownerId,
            principalOrganizationId = null,
            capabilities = emptyList(),
            canRead = false,
            canUpdate = false,
            canManageHealth = false,
            canManageMedia = false,
            canArchive = false,
            canMarkDeceased = false
        )
        val decision = PetEditAuthorization.decide(
            pet = pet,
            context = context,
            sessionUserId = "stranger"
        )
        assertEquals(PetEditAuthorization.Decision.NO_PERMISSION, decision)
    }

    @Test
    fun missingPetIsNotFound() {
        val decision = PetEditAuthorization.decide(
            pet = null,
            context = null,
            sessionUserId = ownerId
        )
        assertEquals(PetEditAuthorization.Decision.PET_NOT_FOUND, decision)
    }
}
