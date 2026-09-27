package com.comunidapp.app.domain.onboarding.onb02

import com.comunidapp.app.data.local.Onb02Completion

/**
 * Onboarding required state is independent from tutorial VIEWED/COMPLETED/SKIPPED.
 * Completing a tutorial never completes onboarding. A newly created PERSON
 * never skips "¿Cómo querés usar LeoVer?" just because auth succeeded.
 */
object Onb02EntryPolicy {

    fun decide(
        completion: Onb02Completion,
        selectionConfirmed: Boolean
    ): Onb02FlowKind? {
        if (completion == Onb02Completion.COMPLETED) return null
        if (!selectionConfirmed) return Onb02FlowKind.FULL_ONBOARDING
        return when (completion) {
            Onb02Completion.FULL_PENDING, Onb02Completion.NOT_STARTED ->
                Onb02FlowKind.FULL_ONBOARDING
            Onb02Completion.EXISTING_T00_PENDING -> Onb02FlowKind.EXISTING_USER_T00
            Onb02Completion.EXISTING_ACKNOWLEDGED, Onb02Completion.COMPLETED -> null
        }
    }

    /**
     * Reinstall / wiped local prefs: skip the selector only when backend
     * tutorial_progress FLOW (onb02_flow) is already COMPLETED.
     * A complete PERSON with empty local prefs is not enough — that is how
     * new Google users and STAGING resets were skipping Tutorial LeoVer.
     */
    fun skipSelectorForExistingComplete(
        completion: Onb02Completion,
        remoteTutorialFlowCompleted: Boolean,
        justCompletedProfileSetup: Boolean
    ): Boolean =
        remoteTutorialFlowCompleted &&
            !justCompletedProfileSetup &&
            completion != Onb02Completion.FULL_PENDING
}
