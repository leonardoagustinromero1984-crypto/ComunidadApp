package com.comunidapp.app.domain.auth

import com.comunidapp.app.data.model.User
import com.comunidapp.app.domain.user.OnboardingCompleteness
import com.comunidapp.app.domain.user.ProfileGate
import com.comunidapp.app.domain.user.ProfileSessionGate

/**
 * Auth provider is a login mechanism, never a LeoVer role or AccountType.
 * Future APPLE uses the same PERSON graph.
 */
enum class AuthMethodKind {
    EMAIL_PASSWORD_OTP,
    GOOGLE,
    APPLE
}

object GoogleAuthPolicy {
    const val EMAIL_SIGNUP_CONFIRMATION = "OTP"
    const val GOOGLE_SIGNUP_CONFIRMATION = "GOOGLE_OAUTH"

    val SCOPES: List<String> = listOf("openid", "email", "profile")

    const val EXTRA_GOOGLE_SCOPES = false
    const val REQUIRES_OTP = false
    const val REQUIRES_LEOVER_PASSWORD = false
    const val PROVIDER_TOKEN_PERSISTED = false
    const val PROVIDER_REFRESH_TOKEN_PERSISTED = false
    const val AVATAR_AUTO_PUBLIC = false
    const val AUTH_PROVIDER_IS_ACCOUNT_TYPE = false
    const val AUTH_PROVIDER_IS_PRODUCT_ROLE = false
    const val GOOGLE_AUTO_SELECT = false
    const val OAUTH_PROMPT_SELECT_ACCOUNT = "select_account"

    fun isAllowedScope(scope: String): Boolean =
        SCOPES.any { it.equals(scope.trim(), ignoreCase = true) }
}

enum class PostAuthDestination {
    MAIN_APP,
    COMPLETE_LEOVER_PROFILE
}

/**
 * Deterministic post-auth routing. Destination comes from canonical PERSON
 * state, not from "came from Google".
 */
object PostAuthResolver {
    fun destination(user: User): PostAuthDestination {
        val complete = OnboardingCompleteness.isComplete(user)
        val gate = ProfileSessionGate.evaluate(user)
        return if (complete && gate == ProfileGate.ProfileReady) {
            PostAuthDestination.MAIN_APP
        } else {
            PostAuthDestination.COMPLETE_LEOVER_PROFILE
        }
    }

    fun sameAuthUserReusesPerson(sessionUserId: String, personUserId: String): Boolean =
        sessionUserId.isNotBlank() && sessionUserId == personUserId
}

/**
 * Hosted Supabase identity-linking rules LeoVer relies on.
 * Automatic linking is a dashboard setting; the app never creates a second auth.users.
 */
object IdentityLinkingPolicy {
    const val PERSON_UNIQUE_CONSTRAINT = "persons.user_id PRIMARY KEY → auth.users(id)"
    const val DUPLICATE_PERSON_POSSIBLE = false

    const val CONFIRMED_EMAIL_SAME_ADDRESS =
        "Supabase automatic linking for compatible verified emails; reuse PERSON by auth.uid()"

    const val UNCONFIRMED_EMAIL_EDGE_CASE =
        "Unconfirmed email identities are not auto-linked. Pending OTP email auth.users has no " +
            "LeoVer access and no PERSON (handle_new_user waits for email_confirmed_at). Google " +
            "with the same address typically cannot create a second auth.users (email unique) " +
            "until the pending row is confirmed or removed. Hosted linking is a dashboard setting."
}
