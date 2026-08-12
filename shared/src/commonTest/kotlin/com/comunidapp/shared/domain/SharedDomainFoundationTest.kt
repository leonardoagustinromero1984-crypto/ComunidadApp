package com.comunidapp.shared.domain

import com.comunidapp.app.domain.m23.M23BookingResilience
import com.comunidapp.app.domain.onboarding.OnboardingIntent
import com.comunidapp.app.domain.onboarding.OnboardingProgress
import com.comunidapp.app.domain.onboarding.OnboardingStatus
import com.comunidapp.app.domain.onboarding.OnboardingStep
import com.comunidapp.app.domain.onboarding.next
import com.comunidapp.app.domain.onboarding.previous
import com.comunidapp.app.domain.organization.OrganizationId
import com.comunidapp.app.domain.pets.MicrochipNormalizer
import com.comunidapp.app.domain.pets.PetAggregate
import com.comunidapp.app.domain.pets.PetAggregateRules
import com.comunidapp.app.domain.pets.PetCapability
import com.comunidapp.app.domain.pets.PetId
import com.comunidapp.app.domain.pets.PetLifecycleStatus
import com.comunidapp.app.domain.pets.PetLinkStatus
import com.comunidapp.app.domain.pets.PetPrincipalHolder
import com.comunidapp.app.domain.pets.PetResponsibility
import com.comunidapp.app.domain.pets.PetResponsibilityId
import com.comunidapp.app.domain.pets.PetResponsibilityRole
import com.comunidapp.app.domain.pets.PetResponsibilityRules
import com.comunidapp.app.domain.pets.PetTransfer
import com.comunidapp.app.domain.pets.PetTransferId
import com.comunidapp.app.domain.pets.PetTransferRules
import com.comunidapp.app.domain.pets.PetTransferStatus
import com.comunidapp.shared.domain.adoption.AdoptionListingStatus
import com.comunidapp.shared.domain.adoption.AdoptionStatusRules
import com.comunidapp.shared.domain.lostfound.LostFoundCaseStatus
import com.comunidapp.shared.domain.lostfound.LostFoundStatusRules
import com.comunidapp.shared.platform.PlatformClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SharedDomainFoundationTest {

    private val now = 1_700_000_000_000L
    private val petId = PetId("pet-1")
    private val owner = "user-owner"
    private val other = "user-other"

    private fun pet(
        principal: PetPrincipalHolder = PetPrincipalHolder.Person(owner),
        status: PetLifecycleStatus = PetLifecycleStatus.ACTIVE
    ) = PetAggregate(
        id = petId,
        displayName = "Luna",
        status = status,
        principal = principal,
        legacyOwnerUserId = (principal as? PetPrincipalHolder.Person)?.userId,
        createdAtEpochMs = now,
        updatedAtEpochMs = now,
        deceasedAtEpochMs = if (status == PetLifecycleStatus.DECEASED) now else null,
        archivedAtEpochMs = if (status == PetLifecycleStatus.ARCHIVED) now else null
    )

    private fun principalLink(holder: PetPrincipalHolder = PetPrincipalHolder.Person(owner)) =
        PetResponsibility(
            id = PetResponsibilityId("resp-principal"),
            petId = petId,
            role = PetResponsibilityRole.PRINCIPAL,
            status = PetLinkStatus.ACTIVE,
            holder = holder,
            validFromEpochMs = now - 1,
            grantedByUserId = owner,
            createdAtEpochMs = now
        )

    @Test
    fun pet_validator_person_principal() {
        val p = pet()
        val r = principalLink()
        assertTrue(PetAggregateRules.validateNew(p, r).isSuccess)
    }

    @Test
    fun pet_validator_org_principal() {
        val org = PetPrincipalHolder.Organization(OrganizationId("org-1"))
        val p = pet(principal = org).copy(legacyOwnerUserId = null)
        assertTrue(PetAggregateRules.validateNew(p, principalLink(org)).isSuccess)
    }

    @Test
    fun pet_transfer_accept_updates_principal() {
        val transfer = PetTransfer(
            id = PetTransferId("t1"),
            petId = petId,
            fromPrincipal = PetPrincipalHolder.Person(owner),
            toPrincipal = PetPrincipalHolder.Person(other),
            status = PetTransferStatus.PENDING,
            requestedAtEpochMs = now,
            expiresAtEpochMs = now + 10_000,
            requestedByUserId = owner
        )
        assertTrue(PetTransferRules.validateRequest(
            com.comunidapp.app.domain.pets.PetResponsibilityGraph(
                pet(),
                listOf(principalLink()),
                emptyList(),
                transfer
            ),
            transfer,
            now
        ).isSuccess)
        val updated = PetTransferRules.applyAcceptedPrincipal(pet(), transfer).getOrThrow()
        assertEquals(PetPrincipalHolder.Person(other), updated.principal)
    }

    @Test
    fun microchip_normalizer() {
        assertEquals("ABC123", MicrochipNormalizer.normalizeOrNull(" abc-123 "))
        assertNull(MicrochipNormalizer.normalizeOrNull("   "))
    }

    @Test
    fun lost_found_status_transitions() {
        assertTrue(LostFoundStatusRules.canResolve(LostFoundCaseStatus.ACTIVE))
        assertFalse(LostFoundStatusRules.canResolve(LostFoundCaseStatus.CLOSED))
        assertTrue(LostFoundStatusRules.transition(LostFoundCaseStatus.ACTIVE, LostFoundCaseStatus.RESOLVED).isSuccess)
        assertTrue(LostFoundStatusRules.transition(LostFoundCaseStatus.RESOLVED, LostFoundCaseStatus.ACTIVE).isSuccess)
        assertTrue(LostFoundStatusRules.transition(LostFoundCaseStatus.CLOSED, LostFoundCaseStatus.RESOLVED).isFailure)
    }

    @Test
    fun adoption_status_rules() {
        assertTrue(AdoptionStatusRules.canPublish(AdoptionListingStatus.DRAFT))
        assertTrue(AdoptionStatusRules.isPubliclyVisible(AdoptionListingStatus.PUBLISHED))
        assertFalse(AdoptionStatusRules.isPubliclyVisible(AdoptionListingStatus.DRAFT))
        assertTrue(AdoptionStatusRules.transition(AdoptionListingStatus.PUBLISHED, AdoptionListingStatus.ADOPTED).isSuccess)
        assertTrue(AdoptionStatusRules.transition(AdoptionListingStatus.DRAFT, AdoptionListingStatus.ADOPTED).isFailure)
    }

    @Test
    fun booking_resilience_messages() {
        assertEquals(
            "Ese horario ya no está disponible.",
            M23BookingResilience.safeUserMessage(IllegalStateException("M23_SLOT_UNAVAILABLE"))
        )
        assertEquals(
            "Ocurrió un problema temporal. Intentá nuevamente.",
            M23BookingResilience.safeUserMessage(RuntimeException("boom"))
        )
    }

    @Test
    fun onboarding_intent_and_steps() {
        assertEquals(OnboardingStep.IDENTITY, OnboardingStep.WELCOME.next())
        assertEquals(OnboardingStep.WELCOME, OnboardingStep.IDENTITY.previous())
        val progress = OnboardingProgress(
            status = OnboardingStatus.IN_PROGRESS,
            currentStep = OnboardingStep.FIRST_INTENT,
            selectedIntent = OnboardingIntent.REGISTER_PET
        )
        assertTrue(progress.shouldAutoShow())
        assertFalse(progress.isTerminal())
        assertEquals(OnboardingIntent.REGISTER_PET, progress.selectedIntent)
    }

    @Test
    fun pet_capability_codes_stable() {
        assertEquals("pet.read", PetCapability.READ.code)
        assertEquals(PetCapability.CREATE, PetCapability.fromCode("pet.create"))
    }

    @Test
    fun platform_clock_injectable() {
        val fixed = PlatformClock { 42L }
        assertEquals(42L, fixed.nowEpochMs())
    }

    @Test
    fun responsibility_rules_missing_principal() {
        val graph = com.comunidapp.app.domain.pets.PetResponsibilityGraph(
            pet(),
            emptyList(),
            emptyList(),
            null
        )
        assertEquals(
            "PET_PRINCIPAL_MISSING",
            (PetResponsibilityRules.resolvePrincipal(graph).exceptionOrNull() as IllegalArgumentException).message
        )
    }
}
