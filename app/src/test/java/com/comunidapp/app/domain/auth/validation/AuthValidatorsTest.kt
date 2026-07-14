package com.comunidapp.app.domain.auth.validation

import com.comunidapp.app.domain.auth.AuthErrorCode
import com.comunidapp.app.domain.auth.validation.AuthValidationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AuthValidatorsTest {

    @Test
    fun email_empty_fails() {
        val result = AuthValidators.validateEmail("  ")
        assertTrue(result.isFailure)
        assertEquals(AuthErrorCode.INVALID_EMAIL.name, (result.exceptionOrNull() as AuthValidationException).error.code)
    }

    @Test
    fun email_trim_and_lowercase() {
        val normalized = AuthValidators.validateEmail("  Maria@Email.COM ").getOrThrow().normalized
        assertEquals("maria@email.com", normalized)
    }

    @Test
    fun email_invalid_format() {
        assertTrue(AuthValidators.validateEmail("not-an-email").isFailure)
    }

    @Test
    fun email_too_long() {
        val local = "a".repeat(250)
        assertTrue(AuthValidators.validateEmail("$local@x.com").isFailure)
    }

    @Test
    fun password_too_short() {
        val result = AuthValidators.validatePassword("1234567")
        assertTrue(result.isFailure)
        assertEquals(AuthErrorCode.WEAK_PASSWORD.name, (result.exceptionOrNull() as AuthValidationException).error.code)
    }

    @Test
    fun password_valid_min_8() {
        assertTrue(AuthValidators.validatePassword("demo1234").isSuccess)
    }

    @Test
    fun password_confirmation_mismatch() {
        val result = AuthValidators.validatePasswordConfirmation("demo1234", "demo12345")
        assertTrue(result.isFailure)
    }

    @Test
    fun consents_incomplete() {
        val result = AuthValidators.validateConsents(
            acceptedTerms = true,
            acceptedPrivacy = false,
            termsVersion = "1.0",
            privacyVersion = "1.0"
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun consents_empty_versions() {
        val result = AuthValidators.validateConsents(
            acceptedTerms = true,
            acceptedPrivacy = true,
            termsVersion = "",
            privacyVersion = "1.0"
        )
        assertTrue(result.isFailure)
        assertEquals(AuthErrorCode.CONFIGURATION_ERROR.name, (result.exceptionOrNull() as AuthValidationException).error.code)
    }

    @Test
    fun consents_ok() {
        assertTrue(
            AuthValidators.validateConsents(true, true, "2026-01", "2026-01").isSuccess
        )
    }

    @Test
    fun signUpCommand_has_no_accountType_field() {
        val fields = com.comunidapp.app.domain.auth.SignUpCommand::class.java.declaredFields
            .map { it.name }
        if (fields.any { it.equals("accountType", ignoreCase = true) }) {
            fail("SignUpCommand must not expose AccountType")
        }
    }
}
