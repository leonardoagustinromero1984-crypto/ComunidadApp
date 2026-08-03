package com.comunidapp.app.domain.onboarding

/** Versión del flujo; incrementar para mostrar onboarding actualizado a usuarios COMPLETED/SKIPPED. */
const val ONBOARDING_VERSION = 1

enum class OnboardingStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED,
    SKIPPED
}

enum class OnboardingStep {
    WELCOME,
    IDENTITY,
    HELP_NETWORK,
    COMMUNITY_AND_CARE,
    FIRST_INTENT,
    MINIMAL_SETUP,
    PRIVACY,
    COMPLETION
}

enum class OnboardingIntent {
    REGISTER_PET,
    LOST_PET,
    FOUND_ANIMAL,
    ADOPT,
    OFFER_FOSTER,
    ORGANIZATION,
    VOLUNTEER,
    EXPLORE
}

enum class ContextualHelpId {
    PET_PASSPORT,
    ALERTS,
    ADOPTIONS,
    SHELTERS
}

data class OnboardingProgress(
    val onboardingVersion: Int = ONBOARDING_VERSION,
    val status: OnboardingStatus = OnboardingStatus.NOT_STARTED,
    val currentStep: OnboardingStep = OnboardingStep.WELCOME,
    val selectedIntent: OnboardingIntent? = null,
    val startedAtEpochMs: Long? = null,
    val completedAtEpochMs: Long? = null,
    val skippedAtEpochMs: Long? = null,
    val contextualHelpSeen: Set<ContextualHelpId> = emptySet(),
    /** Zona aproximada capturada en setup mínimo (no coordenadas). */
    val approximateZone: String? = null,
    val displayNameDraft: String? = null
) {
    fun shouldAutoShow(): Boolean =
        status == OnboardingStatus.NOT_STARTED || status == OnboardingStatus.IN_PROGRESS

    fun isTerminal(): Boolean =
        status == OnboardingStatus.COMPLETED || status == OnboardingStatus.SKIPPED
}

fun OnboardingStep.next(): OnboardingStep? = when (this) {
    OnboardingStep.WELCOME -> OnboardingStep.IDENTITY
    OnboardingStep.IDENTITY -> OnboardingStep.HELP_NETWORK
    OnboardingStep.HELP_NETWORK -> OnboardingStep.COMMUNITY_AND_CARE
    OnboardingStep.COMMUNITY_AND_CARE -> OnboardingStep.FIRST_INTENT
    OnboardingStep.FIRST_INTENT -> OnboardingStep.MINIMAL_SETUP
    OnboardingStep.MINIMAL_SETUP -> OnboardingStep.PRIVACY
    OnboardingStep.PRIVACY -> OnboardingStep.COMPLETION
    OnboardingStep.COMPLETION -> null
}

fun OnboardingStep.previous(): OnboardingStep? = when (this) {
    OnboardingStep.WELCOME -> null
    OnboardingStep.IDENTITY -> OnboardingStep.WELCOME
    OnboardingStep.HELP_NETWORK -> OnboardingStep.IDENTITY
    OnboardingStep.COMMUNITY_AND_CARE -> OnboardingStep.HELP_NETWORK
    OnboardingStep.FIRST_INTENT -> OnboardingStep.COMMUNITY_AND_CARE
    OnboardingStep.MINIMAL_SETUP -> OnboardingStep.FIRST_INTENT
    OnboardingStep.PRIVACY -> OnboardingStep.MINIMAL_SETUP
    OnboardingStep.COMPLETION -> OnboardingStep.PRIVACY
}

fun OnboardingStep.infoPageIndex(): Int? = when (this) {
    OnboardingStep.IDENTITY -> 1
    OnboardingStep.HELP_NETWORK -> 2
    OnboardingStep.COMMUNITY_AND_CARE -> 3
    else -> null
}
