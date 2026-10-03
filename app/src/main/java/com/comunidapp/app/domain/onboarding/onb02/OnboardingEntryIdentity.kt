package com.comunidapp.app.domain.onboarding.onb02

import com.comunidapp.app.data.local.Onb02Completion

/**
 * First Google session can have PERSON in memory before Auth.currentUser is visible.
 * Entry must wait for that id. A null id is not "onboarding finished".
 */
object OnboardingEntryIdentity {
    fun resolve(sessionPersonId: String?, authUserId: String?): String? =
        sessionPersonId?.trim()?.takeIf { it.isNotEmpty() }
            ?: authUserId?.trim()?.takeIf { it.isNotEmpty() }

    fun holdsNavigationUntilTutorial(completion: Onb02Completion): Boolean =
        completion == Onb02Completion.NOT_STARTED ||
            completion == Onb02Completion.FULL_PENDING
}
