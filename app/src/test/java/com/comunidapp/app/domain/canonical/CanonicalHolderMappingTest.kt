package com.comunidapp.app.domain.canonical

import com.comunidapp.app.domain.pets.PetResponsibilityRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalHolderMappingTest {

    @Test
    fun mapsMultipleOwnersAuthorizedAndOrganization() {
        val mapped = CanonicalHolderMapping.map(
            listOf(
                CanonicalHolderMapping.HolderInput(
                    linkId = "l1",
                    holderKind = "PERSON",
                    role = "OWNER",
                    status = "ACTIVE",
                    personId = "p1",
                    organizationId = null,
                    displayName = "Ana"
                ),
                CanonicalHolderMapping.HolderInput(
                    linkId = "l2",
                    holderKind = "PERSON",
                    role = "OWNER",
                    status = "ACTIVE",
                    personId = "p2",
                    organizationId = null,
                    displayName = "Luis"
                ),
                CanonicalHolderMapping.HolderInput(
                    linkId = "l3",
                    holderKind = "PERSON",
                    role = "AUTHORIZED",
                    status = "ACTIVE",
                    personId = "p3",
                    organizationId = null,
                    displayName = "Mara"
                ),
                CanonicalHolderMapping.HolderInput(
                    linkId = "l4",
                    holderKind = "ORGANIZATION",
                    role = "RESPONSIBLE",
                    status = "ACTIVE",
                    personId = null,
                    organizationId = "org-1",
                    displayName = "Refugio Sur"
                )
            )
        )
        assertEquals(4, mapped.size)
        assertEquals(PetResponsibilityRole.PRINCIPAL, mapped[0].uiRole)
        assertEquals(PetResponsibilityRole.CO_RESPONSIBLE, mapped[1].uiRole)
        assertEquals(PetResponsibilityRole.CO_RESPONSIBLE, mapped[2].uiRole)
        assertEquals(PetResponsibilityRole.CO_RESPONSIBLE, mapped[3].uiRole)
        assertEquals("org-1", mapped[3].organizationId)
        assertEquals(2, mapped.count { it.canonicalRole == "OWNER" })
        assertTrue(mapped.any { it.canonicalRole == "AUTHORIZED" })
        assertTrue(mapped.any { it.holderKind == "ORGANIZATION" })
    }

    @Test
    fun doesNotCollapseToSingleOwner() {
        val mapped = CanonicalHolderMapping.map(
            listOf(
                CanonicalHolderMapping.HolderInput("a", "PERSON", "OWNER", "ACTIVE", "p1", null, "A"),
                CanonicalHolderMapping.HolderInput("b", "PERSON", "OWNER", "ACTIVE", "p2", null, "B")
            )
        )
        assertEquals(2, mapped.size)
        assertEquals(1, mapped.count { it.uiRole == PetResponsibilityRole.PRINCIPAL })
        assertEquals(1, mapped.count { it.uiRole == PetResponsibilityRole.CO_RESPONSIBLE })
    }
}
