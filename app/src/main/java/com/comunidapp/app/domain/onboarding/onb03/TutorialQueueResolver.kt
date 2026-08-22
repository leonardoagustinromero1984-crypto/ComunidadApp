package com.comunidapp.app.domain.onboarding.onb03

import com.comunidapp.app.domain.onboarding.onb02.LeoverFunction
import com.comunidapp.app.domain.onboarding.onb02.OrganizationKindOption
import com.comunidapp.app.domain.onboarding.onb02.TutorialId

/**
 * Tutorials auto-trigger only from these events — never from route entry,
 * composition, resume, Google login, or ActiveContext change.
 */
enum class CanonicalTutorialEvent {
    NEW_PERSON_ONBOARDING,
    FUNCTION_ACTIVATED,
    SECOND_CONTEXT_FIRST_AVAILABLE,
    ORGANIZATION_CREATED,
    ORGANIZATION_INVITATION_ACCEPTED,
    COMMERCIAL_TRIAL_FIRST_ACTIVATED
}

enum class TutorialAutoStatus {
    NOT_SEEN,
    VIEWED,
    COMPLETED,
    SKIPPED
}

data class TutorialQueueResult(
    val tutorials: List<TutorialId>,
    val landingRouteHint: String
)

object TutorialQueueResolver {

    fun status(completed: Boolean, skipped: Boolean, viewed: Boolean): TutorialAutoStatus = when {
        completed -> TutorialAutoStatus.COMPLETED
        skipped -> TutorialAutoStatus.SKIPPED
        viewed -> TutorialAutoStatus.VIEWED
        else -> TutorialAutoStatus.NOT_SEEN
    }

    fun isConsumed(completed: Boolean, skipped: Boolean): Boolean = completed || skipped

    fun queue(
        event: CanonicalTutorialEvent,
        consumed: (TutorialId) -> Boolean,
        newlyActivated: Set<LeoverFunction> = emptySet(),
        organizationKind: OrganizationKindOption? = null,
        commercialOrg: Boolean = false,
        invitedAsAdmin: Boolean = false,
        availableContextCount: Int = 1,
        landingRouteHint: String = "home"
    ): TutorialQueueResult {
        val out = linkedSetOf<TutorialId>()
        fun addIfNeeded(id: TutorialId) {
            if (!consumed(id)) out += id
        }

        when (event) {
            CanonicalTutorialEvent.NEW_PERSON_ONBOARDING -> {
                addIfNeeded(TutorialId.T00_MULTI_FUNCTION_INTRO)
            }
            CanonicalTutorialEvent.FUNCTION_ACTIVATED -> {
                newlyActivated.forEach { fn ->
                    if (fn != LeoverFunction.PROFILE_PERSONAL) {
                        addIfNeeded(fn.tutorialId)
                    }
                    if (fn == LeoverFunction.ORGANIZATION) {
                        addIfNeeded(TutorialId.T10_ORGANIZATION)
                        organizationKind?.let { addIfNeeded(it.microTutorialId) }
                    }
                }
                if (newlyActivated.any { it.isCommercialPerson }) {
                    addIfNeeded(TutorialId.T12_COMMERCIAL_PROFESSIONAL)
                }
                if (availableContextCount >= 2) {
                    addIfNeeded(TutorialId.T11_USE_LEOVER_AS)
                }
            }
            CanonicalTutorialEvent.SECOND_CONTEXT_FIRST_AVAILABLE -> {
                addIfNeeded(TutorialId.T11_USE_LEOVER_AS)
            }
            CanonicalTutorialEvent.ORGANIZATION_CREATED -> {
                addIfNeeded(TutorialId.T10_ORGANIZATION)
                if (commercialOrg) addIfNeeded(TutorialId.T13_COMMERCIAL_ORGANIZATION)
                organizationKind?.let { addIfNeeded(it.microTutorialId) }
            }
            CanonicalTutorialEvent.ORGANIZATION_INVITATION_ACCEPTED -> {
                addIfNeeded(
                    if (invitedAsAdmin) TutorialId.T15_ORG_JOIN_ADMIN
                    else TutorialId.T14_ORG_JOIN_MEMBER
                )
                organizationKind?.let { addIfNeeded(it.microTutorialId) }
            }
            CanonicalTutorialEvent.COMMERCIAL_TRIAL_FIRST_ACTIVATED -> {
                if (commercialOrg) addIfNeeded(TutorialId.T13_COMMERCIAL_ORGANIZATION)
                else addIfNeeded(TutorialId.T12_COMMERCIAL_PROFESSIONAL)
            }
        }
        return TutorialQueueResult(out.toList(), landingRouteHint)
    }

    private val LeoverFunction.isCommercialPerson: Boolean
        get() = this == LeoverFunction.VETERINARY_PROFESSIONAL ||
            this == LeoverFunction.WALKER ||
            this == LeoverFunction.CAREGIVER ||
            this == LeoverFunction.TRAINER ||
            this == LeoverFunction.GROOMING ||
            this == LeoverFunction.DAYCARE
}
