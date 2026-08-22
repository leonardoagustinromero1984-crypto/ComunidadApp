package com.comunidapp.app.domain.auth

/**
 * Explicit Google OAuth phases. Authentication provider is not PERSON onboarding.
 *
 * supabase-kt signInWith(Google) returns after launching the system chooser;
 * the session arrives later via deep link. A missing session at launch is not an error.
 */
enum class GoogleAuthLifecycle {
    IDLE,
    LAUNCHING_OAUTH,
    WAITING_EXTERNAL_AUTH,
    RESOLVING_SESSION,
    SUCCESS,
    CANCELLED,
    ERROR
}

object GoogleOAuthPending {
    const val USER_ID = "__google_oauth_pending__"
    const val STATUS = "OAUTH_PENDING"

    fun isPendingUserId(userId: String?): Boolean = userId == USER_ID

    fun pendingUser(): com.comunidapp.app.data.model.User =
        com.comunidapp.app.data.model.User(
            id = USER_ID,
            name = "",
            email = "",
            onboardingStatus = STATUS
        )

    const val FIRST_TAP_ERROR_ROOT_CAUSE =
        "signInWith(Google) defaulted to ExternalAuthAction.ExternalBrowser ACTION_VIEW; " +
            "Gmail handles https VIEW so the first tap opened mail instead of the Google chooser"
}
