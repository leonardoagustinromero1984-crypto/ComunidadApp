package com.comunidapp.app.domain.pets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CareNetworkMembershipTest {

    private class Store {
        data class Link(
            val id: String,
            val petId: String,
            val ownerId: String,
            val memberId: String,
            val role: CareNetworkRole,
            var status: String,
            val managementContextKind: String = PetManagementContext.PERSON
        )

        private val links = mutableListOf<Link>()
        private var seq = 0

        fun invite(ownerId: String, memberId: String, petId: String, role: CareNetworkRole): Link {
            seq += 1
            val link = Link("l$seq", petId, ownerId, memberId, role, CareNetworkRules.STATUS_PENDING)
            links += link
            return link
        }

        fun accept(linkId: String, actorId: String) {
            val link = links.first { it.id == linkId }
            require(link.memberId == actorId)
            require(link.status == CareNetworkRules.STATUS_PENDING)
            link.status = CareNetworkRules.STATUS_ACTIVE
        }

        fun leave(linkId: String, actorId: String) {
            val link = links.first { it.id == linkId }
            require(link.memberId == actorId)
            link.status = CareNetworkRules.STATUS_ENDED
        }

        fun ownerRemove(linkId: String, actorId: String) {
            val link = links.first { it.id == linkId }
            require(link.ownerId == actorId)
            link.status = CareNetworkRules.STATUS_ENDED
        }

        fun vitacoraVisible(petId: String, personId: String): Boolean {
            val owned = links.any {
                it.petId == petId && it.ownerId == personId && it.status == CareNetworkRules.STATUS_ACTIVE
            }
            if (owned) return true
            return links.any {
                it.petId == petId &&
                    it.memberId == personId &&
                    CareNetworkRules.canViewVitacora(it.status)
            }
        }

        fun carePetsFor(personId: String, contextKind: String): List<Link> =
            links.filter {
                it.memberId == personId &&
                    it.status == CareNetworkRules.STATUS_ACTIVE &&
                    CareNetworkRules.visibleInPersonContext(contextKind)
            }

        fun petStillOwned(petId: String, ownerId: String): Boolean =
            links.any { it.petId == petId && it.ownerId == ownerId }
    }

    @Test
    fun invitePendingDoesNotGrantVitacora() {
        val store = Store()
        val invite = store.invite("A", "B", "pet-x", CareNetworkRole.FAMILY)
        assertEquals(CareNetworkRules.STATUS_PENDING, invite.status)
        assertFalse(store.vitacoraVisible("pet-x", "B"))
        assertFalse(CareNetworkRules.canDeletePet(isOwner = false, viaCareMembership = true))
    }

    @Test
    fun acceptListsPetInPersonCareNetworkAndOpensVitacora() {
        val store = Store()
        val invite = store.invite("A", "B", "pet-x", CareNetworkRole.FAMILY)
        store.accept(invite.id, "B")
        assertTrue(store.vitacoraVisible("pet-x", "B"))
        val listed = store.carePetsFor("B", PetManagementContext.PERSON)
        assertEquals(1, listed.size)
        assertEquals("pet-x", listed.first().petId)
        assertTrue(store.petStillOwned("pet-x", "A"))
        assertFalse(CareNetworkRules.canDeletePet(isOwner = false, viaCareMembership = true))
        assertTrue(CareNetworkRules.canDeletePet(isOwner = true, viaCareMembership = false))
    }

    @Test
    fun leaveAndOwnerRemoveRevokeAccessWithoutDeletingPet() {
        val store = Store()
        val invite = store.invite("A", "B", "pet-x", CareNetworkRole.CAREGIVER)
        store.accept(invite.id, "B")
        store.leave(invite.id, "B")
        assertFalse(store.vitacoraVisible("pet-x", "B"))
        assertTrue(store.carePetsFor("B", PetManagementContext.PERSON).isEmpty())
        assertTrue(store.petStillOwned("pet-x", "A"))

        val second = store.invite("A", "C", "pet-x", CareNetworkRole.TRUSTED)
        store.accept(second.id, "C")
        store.ownerRemove(second.id, "A")
        assertFalse(store.vitacoraVisible("pet-x", "C"))
        assertTrue(store.petStillOwned("pet-x", "A"))
    }

    @Test
    fun vetContextDoesNotListCarePets() {
        val store = Store()
        val invite = store.invite("A", "B", "pet-x", CareNetworkRole.OTHER)
        store.accept(invite.id, "B")
        assertTrue(store.carePetsFor("B", PetManagementContext.PERSON).isNotEmpty())
        assertTrue(store.carePetsFor("B", PetManagementContext.ORGANIZATION).isEmpty())
        assertFalse(CareNetworkRules.visibleInPersonContext(PetManagementContext.ORGANIZATION))
    }
}
