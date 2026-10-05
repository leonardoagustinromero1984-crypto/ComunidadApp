package com.comunidapp.app.domain.onboarding.onb02

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/**
 * Deep links and notification routes wait until this is READY.
 * RESOLVING covers the window before the person id exists.
 * TUTORIAL_REQUIRED blocks external navigation for a new Google user.
 */
enum class InitialOnboardingPhase {
    RESOLVING,
    TUTORIAL_REQUIRED,
    READY
}

object InitialOnboardingGate {
    private val phase = MutableStateFlow(InitialOnboardingPhase.RESOLVING)

    fun current(): InitialOnboardingPhase = phase.value

    fun markResolving() {
        phase.value = InitialOnboardingPhase.RESOLVING
    }

    fun markTutorialRequired() {
        phase.value = InitialOnboardingPhase.TUTORIAL_REQUIRED
    }

    fun markReady() {
        phase.value = InitialOnboardingPhase.READY
    }

    fun allowsExternalNavigation(): Boolean = phase.value == InitialOnboardingPhase.READY

    suspend fun awaitReady() {
        phase.first { it == InitialOnboardingPhase.READY }
    }

    /** A missing id stays unresolved even if the session flag was already consumed. */
    fun phaseAfterEntry(userId: String?, entryKind: Onb02FlowKind?): InitialOnboardingPhase {
        if (userId.isNullOrBlank()) return InitialOnboardingPhase.RESOLVING
        return if (entryKind == null) {
            InitialOnboardingPhase.READY
        } else {
            InitialOnboardingPhase.TUTORIAL_REQUIRED
        }
    }

    fun apply(next: InitialOnboardingPhase) {
        phase.value = next
    }
}
