package com.comunidapp.app.viewmodel

/**
 * Navigation display for [SessionViewModel.sessionState].
 *
 * Remember LoggedIn across a refresh Loading pulse so Home does not flash Login.
 * Never remember LoggedOut: after Google OAuth, PERSON hydrate emits Loading and
 * the first attempt must not stay on Login.
 */
object SessionNavDisplay {
    fun rememberLoggedIn(
        current: SessionState,
        previouslyLoggedIn: Boolean,
        sessionUserId: String? = null,
        rememberedUserId: String? = null
    ): Boolean =
        when (current) {
            SessionState.LoggedIn -> true
            SessionState.AdminSession,
            SessionState.AdminPasswordChangeRequired,
            SessionState.AdminMfaEnrollmentRequired,
            SessionState.AdminMfaChallengeRequired -> false
            SessionState.Loading ->
                previouslyLoggedIn &&
                    !sessionUserId.isNullOrBlank() &&
                    sessionUserId == rememberedUserId
            else -> false
        }

    fun resolve(current: SessionState, lastReadyWasLoggedIn: Boolean): SessionState {
        if (current == SessionState.Loading && lastReadyWasLoggedIn) {
            return SessionState.LoggedIn
        }
        return current
    }
}
