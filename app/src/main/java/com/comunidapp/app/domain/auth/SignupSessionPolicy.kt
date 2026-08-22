package com.comunidapp.app.domain.auth

/**
 * Account creation is not the same as a confirmed, authenticated LeoVer session.
 *
 * SIGNUP_ACCEPTED — GoTrue accepted signup. With Confirm Email ON, auth.users
 * may already exist with email_confirmed_at = null. That is expected, not a bug.
 * ACCOUNT_CREATED_EMAIL_PENDING — unverified auth user; no LeoVer app access.
 * EMAIL_CONFIRMED — email_confirmed_at set; still need a session.
 * AUTHENTICATED — valid session for that confirmed auth user.
 * PERSON_PROVISIONED — after verified identity (OTP email_confirmed_at or Google OAuth +
 * canon_provision_my_person). handle_new_user does not activate PERSON while
 * email_confirmed_at is null.
 * ONBOARDING_INCOMPLETE / COMPLETE — derived from PERSON fields, not first login.
 *
 * Never delete an auth user only because verification is pending.
 * Resend confirmation reuses that unconfirmed account (no duplicate signup).
 */
object SignupSessionPolicy {

    /** Hosted Confirm signup must use {{ .Token }}, not {{ .ConfirmationURL }}. */
    const val SIGNUP_CONFIRMATION_MODE = "EMAIL_OTP_ONLY"

    const val UNVERIFIED_AUTH_USER_EXPECTED = true

    fun unverifiedAuthUserIsExpected(emailConfirmedAtPresent: Boolean): Boolean =
        !emailConfirmedAtPresent

    fun mustNotDeleteUnverifiedAuthUser(): Boolean = true

    fun resendReusesExistingUnconfirmedAccount(): Boolean = true

    /** Unconfirmed session must be released; the auth.users row stays. */
    fun mustReleaseUnconfirmedSession(sessionPresent: Boolean, emailConfirmed: Boolean): Boolean =
        sessionPresent && !emailConfirmed

    fun appAccessAllowed(hasAuthenticatedSession: Boolean, emailConfirmed: Boolean): Boolean =
        hasAuthenticatedSession && emailConfirmed

    fun mustClearExistingSessionBeforeSignup(existingSessionUserId: String?): Boolean =
        !existingSessionUserId.isNullOrBlank()

    fun sessionMatchesPerson(sessionUserId: String?, personUserId: String?): Boolean {
        if (sessionUserId.isNullOrBlank() || personUserId.isNullOrBlank()) return false
        return sessionUserId == personUserId
    }

    /** GoTrue accepted signup. Confirm Email ON often returns no session. */
    fun signupAcceptedWithoutSession(httpAccepted: Boolean, sessionPresent: Boolean): Boolean =
        httpAccepted && !sessionPresent

    fun shouldShowVerificationScreen(
        signupAccepted: Boolean,
        sessionPresent: Boolean,
        emailConfirmed: Boolean
    ): Boolean = signupAccepted && (!sessionPresent || !emailConfirmed)

    fun staleSessionWouldContaminateSignup(
        existingSessionUserId: String?,
        newSignupEmail: String?,
        existingSessionEmail: String?
    ): Boolean {
        if (existingSessionUserId.isNullOrBlank()) return false
        if (newSignupEmail.isNullOrBlank() || existingSessionEmail.isNullOrBlank()) return true
        return !existingSessionEmail.equals(newSignupEmail, ignoreCase = true)
    }

    /**
     * GoTrue hides existing emails: HTTP 200, no session, empty identities, no mail sent.
     * That is not a new LeoVer account.
     */
    fun existingEmailHiddenByGoTrue(
        identitiesCount: Int,
        sessionPresent: Boolean
    ): Boolean = !sessionPresent && identitiesCount == 0
}
