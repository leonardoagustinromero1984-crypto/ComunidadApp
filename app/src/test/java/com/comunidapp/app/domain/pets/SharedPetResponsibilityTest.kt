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

/**
 * Shared PERSON holders: one petId, one VitaCora. Invite → accept → list → leave.
 */
class SharedPetResponsibilityTest {

    private data class Link(
        val id: String,
        val petId: String,
        val holderId: String,
        val role: String,
        var status: String
    )

    private class Store {
        private val links = mutableListOf<Link>()
        private val pets = mutableMapOf<String, String>()
        private var seq = 0

        fun createPet(ownerId: String, petId: String): String {
            pets[petId] = ownerId
            seq += 1
            links += Link("l$seq", petId, ownerId, "OWNER", "ACTIVE")
            return petId
        }

        fun invite(actorId: String, personId: String, petId: String): Link {
            require(links.any { it.petId == petId && it.holderId == actorId && it.status == "ACTIVE" && it.role == "OWNER" })
            seq += 1
            val link = Link("l$seq", petId, personId, "OWNER", "PENDING")
            links += link
            return link
        }

        fun accept(linkId: String, actorId: String) {
            val link = links.first { it.id == linkId }
            require(link.holderId == actorId)
            require(link.status == "PENDING")
            link.status = "ACTIVE"
        }

        fun leave(linkId: String, actorId: String) {
            val link = links.first { it.id == linkId }
            require(link.holderId == actorId)
            val owners = links.count { it.petId == link.petId && it.status == "ACTIVE" && it.role == "OWNER" }
            require(owners > 1)
            link.status = "ENDED"
        }

        fun listedPetIds(personId: String): List<String> =
            links.filter { it.holderId == personId && it.status == "ACTIVE" && it.role == "OWNER" }
                .map { it.petId }
                .distinct()

        fun petExists(petId: String): Boolean = pets.containsKey(petId)

        fun canDelete(personId: String, petId: String): Boolean {
            val original = pets[petId] ?: return false
            return original == personId
        }

        fun canEnd(actorId: String, linkId: String): Boolean {
            val link = links.first { it.id == linkId }
            val creator = pets[link.petId] ?: return false
            if (link.holderId == creator && actorId != creator) return false
            return links.any {
                it.petId == link.petId && it.holderId == actorId && it.status == "ACTIVE" && it.role == "OWNER"
            } && (actorId == creator)
        }
    }

    private fun petFor(personId: String, petId: String) = Pet(
        id = petId,
        ownerId = "A",
        createdByUserId = "A",
        name = "X",
        species = PetSpecies.DOG,
        sex = PetSex.UNKNOWN,
        ageYears = 1,
        size = PetSize.MEDIUM,
        description = "",
        managementContextKind = PetManagementContext.PERSON,
        managementContextId = "A"
    ).let { listOf(it) }.let { pets ->
        PetManagementContext.filter(pets, OperationalContext.Personal, personId).map { it.id }
    }

    @Test
    fun aInvitesB_accept_samePetId_sameVitacora_vetHidden_leaveKeepsPet() {
        val store = Store()
        val petId = store.createPet("A", "pet-x")
        val invite = store.invite("A", "B", petId)
        assertEquals("PENDING", invite.status)
        assertEquals(listOf("pet-x"), store.listedPetIds("A"))
        assertTrue(store.listedPetIds("B").isEmpty())

        store.accept(invite.id, "B")
        assertEquals(listOf("pet-x"), store.listedPetIds("A"))
        assertEquals(listOf("pet-x"), store.listedPetIds("B"))
        assertEquals(store.listedPetIds("A").single(), store.listedPetIds("B").single())
        assertTrue(store.petExists(petId))
        assertTrue(store.canDelete("A", petId))
        assertFalse(store.canDelete("B", petId))
        assertFalse(store.canEnd("B", "l1"))
        assertTrue(store.canEnd("A", invite.id))

        val personVisible = petFor("B", petId)
        val vetVisible = PetManagementContext.filter(
            listOf(
                Pet(
                    id = petId,
                    ownerId = "A",
                    createdByUserId = "A",
                    name = "X",
                    species = PetSpecies.DOG,
                    sex = PetSex.UNKNOWN,
                    ageYears = 1,
                    size = PetSize.MEDIUM,
                    description = "",
                    managementContextKind = PetManagementContext.PERSON,
                    managementContextId = "A"
                )
            ),
            OperationalContext.Veterinary("org-vet", "Vet"),
            "B"
        )
        assertEquals(listOf("pet-x"), personVisible)
        assertTrue(vetVisible.isEmpty())

        store.leave(invite.id, "B")
        assertTrue(store.listedPetIds("B").isEmpty())
        assertEquals(listOf("pet-x"), store.listedPetIds("A"))
        assertTrue(store.petExists(petId))
    }
}
