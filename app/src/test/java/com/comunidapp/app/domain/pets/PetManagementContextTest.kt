package com.comunidapp.app.domain.pets

import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.domain.context.OperationalContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PetManagementContextTest {

    private val userId = "user-u"
    private val orgId = "org-vet-x"

    private fun pet(
        id: String,
        kind: String?,
        contextId: String?
    ) = Pet(
        id = id,
        ownerId = userId,
        createdByUserId = userId,
        name = id,
        species = PetSpecies.DOG,
        sex = PetSex.UNKNOWN,
        ageYears = 1,
        size = PetSize.MEDIUM,
        description = "",
        managementContextKind = kind,
        managementContextId = contextId
    )

    @Test
    fun personSeesPersonalPetVeterinaryDoesNot() {
        val petA = pet("pet-a", PetManagementContext.PERSON, userId)
        val person = PetManagementContext.filter(
            listOf(petA),
            OperationalContext.Personal,
            userId
        )
        val vet = PetManagementContext.filter(
            listOf(petA),
            OperationalContext.Veterinary(orgId, "Veterinaria X"),
            userId
        )
        assertEquals(listOf("pet-a"), person.map { it.id })
        assertTrue(vet.isEmpty())
    }

    @Test
    fun veterinarySeesGrantedOrgPetNotPersonalDefault() {
        val personal = pet("pet-a", PetManagementContext.PERSON, userId)
        val granted = pet("pet-b", PetManagementContext.ORGANIZATION, orgId)
        val listed = PetManagementContext.filter(
            listOf(personal, granted),
            OperationalContext.Veterinary(orgId, "Veterinaria X"),
            userId
        )
        assertEquals(listOf("pet-b"), listed.map { it.id })
        assertFalse(listed.any { it.id == "pet-a" })
    }

    @Test
    fun legacyNullContextIsPerson() {
        val legacy = pet("pet-legacy", null, null)
        assertTrue(
            PetManagementContext.visibleIn(legacy, OperationalContext.Personal, userId)
        )
        assertFalse(
            PetManagementContext.visibleIn(
                legacy,
                OperationalContext.Veterinary(orgId, "Vet"),
                userId
            )
        )
    }

    @Test
    fun sharedPersonPetVisibleToSecondHolderNotInVeterinary() {
        val shared = pet("pet-x", PetManagementContext.PERSON, "user-a").copy(
            ownerId = "user-a",
            createdByUserId = "user-a",
            accessSubjectUserId = "user-b"
        )
        val personB = PetManagementContext.filter(
            listOf(shared),
            OperationalContext.Personal,
            "user-b"
        )
        val vetB = PetManagementContext.filter(
            listOf(shared),
            OperationalContext.Veterinary(orgId, "Veterinaria X"),
            "user-b"
        )
        assertEquals(listOf("pet-x"), personB.map { it.id })
        assertTrue(vetB.isEmpty())
    }

    @Test
    fun explicitOwnerLinkKeepsSharedPetVisibleWithoutReplacingContext() {
        val shared = pet("pet-x", PetManagementContext.PERSON, "user-a").copy(
            ownerId = "user-a",
            createdByUserId = "user-a",
            ownerIds = listOf("user-a", "user-b")
        )
        val personB = PetManagementContext.filter(
            listOf(shared),
            OperationalContext.Personal,
            "user-b"
        )
        assertEquals(listOf("pet-x"), personB.map { it.id })
    }

    @Test
    fun unsharedPersonPetOfAnotherAccountIsHidden() {
        val foreign = pet("pet-a", PetManagementContext.PERSON, "user-a").copy(
            ownerId = "user-a",
            createdByUserId = "user-a"
        )
        val personB = PetManagementContext.filter(
            listOf(foreign),
            OperationalContext.Personal,
            "user-b"
        )
        assertTrue(personB.isEmpty())
    }
}
