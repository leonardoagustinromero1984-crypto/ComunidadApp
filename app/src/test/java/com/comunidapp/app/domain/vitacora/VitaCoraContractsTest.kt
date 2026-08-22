package com.comunidapp.app.domain.vitacora

import com.comunidapp.app.domain.legal.LegalConsentRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class VitaCoraContractsTest {

    @Test
    fun grantScopesAreCanonical() {
        val names = VitaCoraGrantScope.entries.map { it.name }
        assertEquals(
            listOf("ESSENTIAL", "HEALTH", "ESSENTIAL_AND_HEALTH", "FULL_SHAREABLE"),
            names
        )
    }

    @Test
    fun proposalStatusesAreCanonical() {
        val names = VitaCoraProposalStatus.entries.map { it.name }
        assertEquals(
            listOf("PENDING", "ACCEPTED", "REJECTED", "CORRECTION_REQUESTED"),
            names
        )
    }

    @Test
    fun revokedGrantIsInactive() {
        val grant = VitaCoraGrant(
            id = "g1",
            petId = "p1",
            granteeKind = VitaCoraHolderKind.PERSON,
            granteePersonId = "u2",
            granteeOrganizationId = null,
            purpose = "vet",
            scope = VitaCoraGrantScope.HEALTH,
            expiresAt = null,
            revokedAt = Instant.parse("2026-08-01T00:00:00Z"),
            grantedByActorUserId = "u1"
        )
        assertTrue(grant.isIndefinite)
        assertFalse(grant.isActive)
    }

    @Test
    fun legalIsDraftAndNotMarketing() {
        assertEquals("DRAFT_PRE_LAUNCH", LegalConsentRules.CURRENT_STATUS)
        assertFalse(LegalConsentRules.marketingConsentAllowed())
        assertFalse(LegalConsentRules.tutorialIsConsent())
        assertTrue(LegalConsentRules.allTutorialsSkippable())
    }
}
