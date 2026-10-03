package com.comunidapp.app.domain.capability

import com.comunidapp.app.domain.context.ActiveContextSelection
import com.comunidapp.app.domain.context.OperationalContext
import com.comunidapp.app.domain.context.OperationalContextKind
import com.comunidapp.app.domain.context.resolveActiveContext
import com.comunidapp.app.domain.onboarding.onb02.LeoverFunction
import com.comunidapp.app.domain.onboarding.onb02.Onb02FlowKind
import com.comunidapp.app.domain.user.AccountIdentityCleanup
import com.comunidapp.app.domain.onboarding.onb02.InitialOnboardingGate
import com.comunidapp.app.domain.onboarding.onb02.InitialOnboardingPhase
import com.comunidapp.app.navigation.NavRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityGateAndStartupTest {

    @Test
    fun purePersonCannotPublishOrOfferFoster() {
        val facts = CapabilityFacts(context = OperationalContext.Personal)
        assertFalse(CapabilityGate.canPublishAdoption(facts))
        assertFalse(CapabilityGate.canOfferFosterHome(facts))
        assertFalse(CapabilityGate.canBrowseFosterRequests(facts))
        assertFalse(CapabilityGate.canApplyAsFoster(facts))
        assertTrue(CapabilityGate.canRequestFosterForFoundPet(facts))
        val before = emptySet<PersonCapabilityCode>()
        assertEquals(before, CapabilityGate.afterRequestingFosterForFoundPet(before))
        assertFalse(CapabilityGate.canUseFunction(LeoverFunction.FOSTER, facts))
    }

    @Test
    fun fosterContextCanOfferBrowseAndApplyWhenVerificationAllows() {
        val allowed = CapabilityFacts.forActiveContext(
            OperationalContext.Foster("home-1", "Hogar de tránsito")
        )
        assertTrue(CapabilityGate.canOfferFosterHome(allowed))
        assertTrue(CapabilityGate.canBrowseFosterRequests(allowed))
        assertTrue(CapabilityGate.canApplyAsFoster(allowed))
        val blocked = allowed.copy(fosterMayApply = CapabilityGate.fosterApplyAllowed("REJECTED"))
        assertFalse(CapabilityGate.canApplyAsFoster(blocked))
        assertTrue(CapabilityGate.canOfferFosterHome(blocked))
    }

    @Test
    fun rescuerAndShelterCanPublishAdoption() {
        val rescuer = CapabilityFacts.forActiveContext(OperationalContext.Rescuer("u1"))
        assertTrue(CapabilityGate.canPublishAdoption(rescuer))
        val shelter = CapabilityFacts.forActiveContext(
            OperationalContext.Organization("org-1", "Refugio", "SHELTER")
        )
        assertTrue(CapabilityGate.canPublishAdoption(shelter))
        assertTrue(CapabilityGate.canManageOrganization(shelter))
    }

    @Test
    fun capabilityWithoutMatchingContextHidesAdminTools() {
        val personWithFoster = CapabilityFacts(
            context = OperationalContext.Personal,
            activeCapabilities = setOf(PersonCapabilityCode.FOSTER),
            selectedFunctions = setOf(LeoverFunction.FOSTER)
        )
        assertFalse(CapabilityGate.canOfferFosterHome(personWithFoster))
        assertFalse(CapabilityGate.canBrowseFosterRequests(personWithFoster))
        assertFalse(CapabilityGate.sumate(personWithFoster).showOfferFosterHome)
        val personWithRescuer = CapabilityFacts(
            context = OperationalContext.Personal,
            activeCapabilities = setOf(PersonCapabilityCode.RESCUER),
            selectedFunctions = setOf(LeoverFunction.RESCUER)
        )
        assertFalse(CapabilityGate.canPublishAdoption(personWithRescuer))
        assertFalse(CapabilityGate.adoptionSurface(personWithRescuer).showPublish)
    }

    @Test
    fun purePersonSumateAndAdoptions() {
        val surface = CapabilityGate.sumate(CapabilityFacts.forActiveContext(OperationalContext.Personal))
        assertTrue(surface.showAdoption)
        assertTrue(surface.showShelters)
        assertTrue(surface.showLostFound)
        assertTrue(surface.showDonations)
        assertTrue(surface.showEvents)
        assertFalse(surface.showOfferFosterHome)
        assertFalse(surface.showBrowseFosterRequests)
        assertFalse(surface.showFosterManagement)
        val adoptions = CapabilityGate.adoptionSurface(
            CapabilityFacts.forActiveContext(OperationalContext.Personal)
        )
        assertFalse(adoptions.showPublish)
        assertTrue(adoptions.showProfile)
        assertTrue(adoptions.showMyApplications)
        assertFalse(adoptions.showReceivedApplications)
    }

    @Test
    fun fosterAndRescuerSurfaces() {
        val foster = CapabilityGate.sumate(
            CapabilityFacts.forActiveContext(OperationalContext.Foster("h1", "Hogar"))
        )
        assertTrue(foster.showOfferFosterHome)
        assertTrue(foster.showBrowseFosterRequests)
        val rescuer = CapabilityGate.adoptionSurface(
            CapabilityFacts.forActiveContext(OperationalContext.Rescuer("u1"))
        )
        assertTrue(rescuer.showPublish)
        assertTrue(rescuer.showReceivedApplications)
    }

    @Test
    fun defensiveNavigationRejectsIncompatibleDestinations() {
        val person = CapabilityFacts.forActiveContext(OperationalContext.Personal)
        assertFalse(CapabilityNavigationGuard.allows(NavRoutes.ADOPTION_FORM, person))
        assertFalse(CapabilityNavigationGuard.allows(NavRoutes.FOSTER_OPEN_REQUESTS, person))
        assertFalse(CapabilityNavigationGuard.allows(NavRoutes.FOSTER_HOME_FORM, person))
        assertTrue(CapabilityNavigationGuard.allows(NavRoutes.fosterCareRequest("pet-1"), person))
        assertTrue(CapabilityNavigationGuard.allows(NavRoutes.ADOPTIONS, person))
        val foster = CapabilityFacts.forActiveContext(OperationalContext.Foster("h1", "Hogar"))
        assertTrue(CapabilityNavigationGuard.allows(NavRoutes.FOSTER_OPEN_REQUESTS, foster))
        assertFalse(CapabilityNavigationGuard.allows(NavRoutes.ADOPTION_FORM, foster))
    }

    @Test
    fun newUserDoesNotComposeHomeBeforeTutorial() {
        assertFalse(AppStartupResolver.composesHomeBeforeDecision())
        val route = AppStartupResolver.decide(
            userId = "new-user",
            onboardingKind = Onb02FlowKind.FULL_ONBOARDING,
            restoredRoute = NavRoutes.HOME,
            facts = CapabilityFacts.forActiveContext(OperationalContext.Personal)
        )
        assertEquals(NavRoutes.onb02(Onb02FlowKind.FULL_ONBOARDING.name), route)
        assertFalse(route == NavRoutes.HOME)
    }

    @Test
    fun resolvingStaysNeutralUntilUserExists() {
        val route = AppStartupResolver.decide(
            userId = null,
            onboardingKind = null,
            restoredRoute = NavRoutes.ADOPTION_FORM,
            facts = CapabilityFacts.forActiveContext(OperationalContext.Personal)
        )
        assertEquals(AppStartupResolver.RESOLVING_ROUTE, route)
        assertFalse(route == NavRoutes.HOME)
    }

    @Test
    fun newPersonOnlyJourneyEndsAtPersonalFeed() {
        assertEquals(
            listOf(
                CanonicalStartupStep.TUTORIAL_GENERAL,
                CanonicalStartupStep.FUNCTION_SELECTION,
                CanonicalStartupStep.ADD_FUNCTION_GUIDE,
                CanonicalStartupStep.FEED
            ),
            CanonicalOnboardingJourney.stepsForNewUser(emptySet())
        )
    }

    @Test
    fun newFunctionJourneyChoosesContextBeforeFeed() {
        assertEquals(
            listOf(
                CanonicalStartupStep.TUTORIAL_GENERAL,
                CanonicalStartupStep.FUNCTION_SELECTION,
                CanonicalStartupStep.FUNCTION_TUTORIAL,
                CanonicalStartupStep.USE_LEOVER_AS_GUIDE,
                CanonicalStartupStep.CONTEXT_SELECTION,
                CanonicalStartupStep.FEED
            ),
            CanonicalOnboardingJourney.stepsForNewUser(setOf(LeoverFunction.FOSTER))
        )
    }

    @Test
    fun existingUserDoesNotRepeatCompletedTutorial() {
        val route = AppStartupResolver.decide(
            userId = "existing",
            onboardingKind = null,
            restoredRoute = null,
            facts = CapabilityFacts.forActiveContext(OperationalContext.Personal)
        )
        assertEquals(NavRoutes.HOME, route)
        assertFalse(CanonicalOnboardingJourney.existingUserRepeatsTutorial(null))
    }

    @Test
    fun logoutClearsStartupPhaseSoNextAccountDoesNotReuseIt() {
        InitialOnboardingGate.markReady()
        AccountIdentityCleanup.clear()
        assertEquals(InitialOnboardingPhase.RESOLVING, InitialOnboardingGate.current())
        val next = AppStartupResolver.decide(
            userId = "user-b",
            onboardingKind = Onb02FlowKind.FULL_ONBOARDING,
            restoredRoute = NavRoutes.FOSTER_PLACEMENTS,
            facts = CapabilityFacts.forActiveContext(OperationalContext.Personal)
        )
        assertEquals(NavRoutes.onb02(Onb02FlowKind.FULL_ONBOARDING.name), next)
    }

    @Test
    fun invalidPersistedContextDoesNotOpenPrivilegedFeed() {
        val resolved = resolveActiveContext(
            available = listOf(OperationalContext.Personal),
            saved = ActiveContextSelection(OperationalContextKind.ORGANIZATION, "org-gone"),
            lastActive = OperationalContext.Organization("org-gone", "Refugio", "SHELTER")
        )
        assertTrue(resolved is OperationalContext.Personal)
        val route = AppStartupResolver.decide(
            userId = "existing",
            onboardingKind = null,
            restoredRoute = NavRoutes.ADOPTION_FORM,
            facts = CapabilityFacts.forActiveContext(resolved)
        )
        assertEquals(NavRoutes.HOME, route)
    }
}
