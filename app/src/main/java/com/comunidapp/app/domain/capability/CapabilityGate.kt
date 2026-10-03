package com.comunidapp.app.domain.capability

import com.comunidapp.app.domain.context.OperationalContext
import com.comunidapp.app.domain.onboarding.onb02.LeoverFunction
import com.comunidapp.app.domain.onboarding.onb02.Onb02FlowKind
import com.comunidapp.app.navigation.NavRoutes

/**
 * PERSON is the account identity. It is not a foster home, a rescuer,
 * or permission to publish adoptions.
 *
 * [OperationalContext.Personal] is the active lens, not a permission bundle.
 * Tools that require a function are visible only when that function is held
 * and the active context is the one that function operates in.
 *
 * Choosing "agregar función" does not grant the function. [selectedFunctions]
 * and [activeCapabilities] are functions the identity already holds.
 */
data class CapabilityFacts(
    val context: OperationalContext,
    val activeCapabilities: Set<PersonCapabilityCode> = emptySet(),
    val selectedFunctions: Set<LeoverFunction> = emptySet(),
    val organizationMembershipAuthorized: Boolean = false,
    val fosterMayApply: Boolean = true
) {
    companion object {
        fun forActiveContext(context: OperationalContext): CapabilityFacts = when (context) {
            is OperationalContext.Rescuer -> CapabilityFacts(
                context = context,
                activeCapabilities = setOf(PersonCapabilityCode.RESCUER),
                selectedFunctions = setOf(LeoverFunction.RESCUER)
            )
            is OperationalContext.Foster -> CapabilityFacts(
                context = context,
                activeCapabilities = setOf(PersonCapabilityCode.FOSTER),
                selectedFunctions = setOf(LeoverFunction.FOSTER)
            )
            is OperationalContext.Organization -> CapabilityFacts(
                context = context,
                organizationMembershipAuthorized = true
            )
            else -> CapabilityFacts(context = context)
        }
    }
}

data class SumateVisibility(
    val showAdoption: Boolean,
    val showShelters: Boolean,
    val showLostFound: Boolean,
    val showDonations: Boolean,
    val showEvents: Boolean,
    val showOfferFosterHome: Boolean,
    val showBrowseFosterRequests: Boolean,
    val showFosterManagement: Boolean,
    val adoptionDescribesPublish: Boolean,
    val shelterOpensDirectory: Boolean
)

data class AdoptionSurface(
    val showProfile: Boolean,
    val showMyApplications: Boolean,
    val showPublish: Boolean,
    val showReceivedApplications: Boolean
)

object CapabilityGate {

    fun canPublishAdoption(facts: CapabilityFacts): Boolean = when (facts.context) {
        is OperationalContext.Rescuer ->
            PersonCapabilityCode.RESCUER in facts.activeCapabilities ||
                LeoverFunction.RESCUER in facts.selectedFunctions
        is OperationalContext.Organization -> facts.organizationMembershipAuthorized
        else -> false
    }

    fun canOfferFosterHome(facts: CapabilityFacts): Boolean =
        holdsFoster(facts) && facts.context is OperationalContext.Foster

    fun canBrowseFosterRequests(facts: CapabilityFacts): Boolean =
        canOfferFosterHome(facts)

    fun canApplyAsFoster(facts: CapabilityFacts): Boolean =
        canOfferFosterHome(facts) && facts.fosterMayApply

    /**
     * Anyone who found a pet may ask for a temporary home for that pet.
     * This does not grant [PersonCapabilityCode.FOSTER].
     */
    fun canRequestFosterForFoundPet(facts: CapabilityFacts): Boolean {
        @Suppress("UNUSED_PARAMETER")
        val ignored = facts
        return true
    }

    fun afterRequestingFosterForFoundPet(
        capabilities: Set<PersonCapabilityCode>
    ): Set<PersonCapabilityCode> = capabilities

    fun fosterApplyAllowed(verificationStatus: String): Boolean {
        val blocked = setOf("REJECTED", "SUSPENDED", "PENDING", "BLOCKED")
        return verificationStatus.trim().uppercase() !in blocked
    }

    fun canManageOrganization(facts: CapabilityFacts): Boolean =
        facts.context is OperationalContext.Organization && facts.organizationMembershipAuthorized

    fun canUseFunction(function: LeoverFunction, facts: CapabilityFacts): Boolean {
        if (function.isBaseProfile) return true
        if (function in facts.selectedFunctions) return true
        return when (function) {
            LeoverFunction.RESCUER -> PersonCapabilityCode.RESCUER in facts.activeCapabilities
            LeoverFunction.FOSTER -> PersonCapabilityCode.FOSTER in facts.activeCapabilities
            else -> false
        }
    }

    fun sumate(facts: CapabilityFacts): SumateVisibility {
        val offer = canOfferFosterHome(facts)
        val browse = canBrowseFosterRequests(facts)
        return SumateVisibility(
            showAdoption = true,
            showShelters = true,
            showLostFound = true,
            showDonations = true,
            showEvents = true,
            showOfferFosterHome = offer,
            showBrowseFosterRequests = browse,
            showFosterManagement = offer,
            adoptionDescribesPublish = canPublishAdoption(facts),
            shelterOpensDirectory = facts.context is OperationalContext.Personal
        )
    }

    fun adoptionSurface(facts: CapabilityFacts): AdoptionSurface = AdoptionSurface(
        showProfile = true,
        showMyApplications = true,
        showPublish = canPublishAdoption(facts),
        showReceivedApplications = canPublishAdoption(facts)
    )

    private fun holdsFoster(facts: CapabilityFacts): Boolean =
        PersonCapabilityCode.FOSTER in facts.activeCapabilities ||
            LeoverFunction.FOSTER in facts.selectedFunctions
}

object CapabilityNavigationGuard {

    fun allows(route: String?, facts: CapabilityFacts): Boolean {
        if (route.isNullOrBlank()) return true
        val path = route.substringBefore("?")
        return when {
            path == NavRoutes.ADOPTION_FORM || path.startsWith("adoption_form/") ->
                CapabilityGate.canPublishAdoption(facts)
            path == NavRoutes.M16_SHELTERS_MANAGE ->
                CapabilityGate.canManageOrganization(facts)
            path == NavRoutes.RECEIVED_ADOPTION_APPLICATIONS ->
                CapabilityGate.adoptionSurface(facts).showReceivedApplications
            isFosterHomeTool(path) ->
                CapabilityGate.canOfferFosterHome(facts) ||
                    CapabilityGate.canBrowseFosterRequests(facts)
            path.startsWith("foster_care_request/") ->
                CapabilityGate.canRequestFosterForFoundPet(facts)
            else -> true
        }
    }

    private fun isFosterHomeTool(path: String): Boolean =
        path == NavRoutes.FOSTER_OPEN_REQUESTS ||
            path == NavRoutes.FOSTER_HOMES ||
            path == NavRoutes.MY_FOSTER_HOME ||
            path == NavRoutes.FOSTER_HOME_FORM ||
            path.startsWith("foster_home_form/") ||
            path == NavRoutes.FOSTER_PLACEMENTS ||
            path.startsWith("foster_placements/") ||
            path == NavRoutes.FOSTER_REQUESTS_RECEIVED ||
            path.startsWith("foster_choose_applicant/")
}

/**
 * First composition of the logged-in graph. Home is not a candidate until
 * onboarding and the active context are resolved.
 */
object AppStartupResolver {
    const val RESOLVING_ROUTE = "startup_resolving"

    fun decide(
        userId: String?,
        onboardingKind: Onb02FlowKind?,
        restoredRoute: String?,
        facts: CapabilityFacts
    ): String {
        if (userId.isNullOrBlank()) return RESOLVING_ROUTE
        if (onboardingKind != null) return NavRoutes.onb02(onboardingKind.name)
        val restored = restoredRoute?.trim()?.takeIf { it.isNotEmpty() }
        if (restored != null &&
            restored != NavRoutes.HOME &&
            restored != RESOLVING_ROUTE &&
            CapabilityNavigationGuard.allows(restored, facts)
        ) {
            return restored
        }
        return NavRoutes.HOME
    }

    fun composesHomeBeforeDecision(): Boolean = false
}

enum class CanonicalStartupStep {
    TUTORIAL_GENERAL,
    FUNCTION_SELECTION,
    ADD_FUNCTION_GUIDE,
    FUNCTION_TUTORIAL,
    USE_LEOVER_AS_GUIDE,
    CONTEXT_SELECTION,
    FEED
}

object CanonicalOnboardingJourney {
    fun stepsForNewUser(selectedExtras: Set<LeoverFunction>): List<CanonicalStartupStep> {
        val extras = selectedExtras.filter { it.isSelectableExtra }.toSet()
        if (extras.isEmpty()) {
            return listOf(
                CanonicalStartupStep.TUTORIAL_GENERAL,
                CanonicalStartupStep.FUNCTION_SELECTION,
                CanonicalStartupStep.ADD_FUNCTION_GUIDE,
                CanonicalStartupStep.FEED
            )
        }
        return listOf(
            CanonicalStartupStep.TUTORIAL_GENERAL,
            CanonicalStartupStep.FUNCTION_SELECTION,
            CanonicalStartupStep.FUNCTION_TUTORIAL,
            CanonicalStartupStep.USE_LEOVER_AS_GUIDE,
            CanonicalStartupStep.CONTEXT_SELECTION,
            CanonicalStartupStep.FEED
        )
    }

    fun existingUserRepeatsTutorial(onboardingKind: Onb02FlowKind?): Boolean =
        onboardingKind != null
}
