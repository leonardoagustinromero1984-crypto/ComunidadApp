package com.comunidapp.app.domain.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignupSessionPolicyTest {

    @Test
    fun signupConfirmationModeIsEmailOtpOnly() {
        assertEquals("EMAIL_OTP_ONLY", SignupSessionPolicy.SIGNUP_CONFIRMATION_MODE)
    }

    @Test
    fun authUserWithoutSession_cannotEnterApp() {
        assertFalse(SignupSessionPolicy.appAccessAllowed(hasAuthenticatedSession = false, emailConfirmed = false))
        assertFalse(SignupSessionPolicy.appAccessAllowed(hasAuthenticatedSession = false, emailConfirmed = true))
    }

    @Test
    fun unconfirmedSession_cannotEnterApp() {
        assertFalse(SignupSessionPolicy.appAccessAllowed(hasAuthenticatedSession = true, emailConfirmed = false))
    }

    @Test
    fun confirmedSession_canEnterApp() {
        assertTrue(SignupSessionPolicy.appAccessAllowed(hasAuthenticatedSession = true, emailConfirmed = true))
    }

    @Test
    fun unverifiedAuthUserAfterSignupIsExpectedAndMustNotBeDeleted() {
        assertTrue(SignupSessionPolicy.UNVERIFIED_AUTH_USER_EXPECTED)
        assertTrue(SignupSessionPolicy.unverifiedAuthUserIsExpected(emailConfirmedAtPresent = false))
        assertFalse(SignupSessionPolicy.unverifiedAuthUserIsExpected(emailConfirmedAtPresent = true))
        assertTrue(SignupSessionPolicy.mustNotDeleteUnverifiedAuthUser())
        assertTrue(SignupSessionPolicy.resendReusesExistingUnconfirmedAccount())
        assertTrue(
            SignupSessionPolicy.mustReleaseUnconfirmedSession(
                sessionPresent = true,
                emailConfirmed = false
            )
        )
        assertFalse(
            SignupSessionPolicy.mustReleaseUnconfirmedSession(
                sessionPresent = true,
                emailConfirmed = true
            )
        )
        assertTrue(
            SignupSessionPolicy.shouldShowVerificationScreen(
                signupAccepted = true,
                sessionPresent = false,
                emailConfirmed = false
            )
        )
    }

    @Test
    fun staleSessionMustBeClearedBeforeSignup() {
        assertTrue(SignupSessionPolicy.mustClearExistingSessionBeforeSignup("old-user"))
        assertFalse(SignupSessionPolicy.mustClearExistingSessionBeforeSignup(null))
        assertFalse(SignupSessionPolicy.mustClearExistingSessionBeforeSignup(""))
    }

    @Test
    fun sessionMustMatchPersonBeingOnboarded() {
        assertTrue(SignupSessionPolicy.sessionMatchesPerson("u1", "u1"))
        assertFalse(SignupSessionPolicy.sessionMatchesPerson("u1", "u2"))
        assertFalse(SignupSessionPolicy.sessionMatchesPerson(null, "u1"))
    }

    @Test
    fun confirmEmailOn_signupWithoutSession_isAccepted() {
        assertTrue(SignupSessionPolicy.signupAcceptedWithoutSession(httpAccepted = true, sessionPresent = false))
        assertFalse(SignupSessionPolicy.signupAcceptedWithoutSession(httpAccepted = true, sessionPresent = true))
        assertTrue(
            SignupSessionPolicy.shouldShowVerificationScreen(
                signupAccepted = true,
                sessionPresent = false,
                emailConfirmed = false
            )
        )
        assertFalse(
            SignupSessionPolicy.shouldShowVerificationScreen(
                signupAccepted = false,
                sessionPresent = false,
                emailConfirmed = false
            )
        )
    }

    @Test
    fun staleSessionCannotAuthorizeAnotherSignup() {
        assertTrue(
            SignupSessionPolicy.staleSessionWouldContaminateSignup("velu-id", "nuevo@email.com", "velu@email.com")
        )
        assertTrue(SignupSessionPolicy.mustClearExistingSessionBeforeSignup("velu-id"))
        assertFalse(SignupSessionPolicy.staleSessionWouldContaminateSignup(null, "nuevo@email.com", null))
    }
}
