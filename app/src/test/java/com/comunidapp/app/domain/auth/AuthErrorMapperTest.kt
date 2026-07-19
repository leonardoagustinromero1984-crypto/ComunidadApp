package com.comunidapp.app.domain.auth

import com.comunidapp.app.domain.auth.validation.AuthValidationException
import com.comunidapp.app.domain.auth.validation.AuthValidators
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException

class AuthErrorMapperTest {

    @Test
    fun maps_invalid_credentials() {
        val error = AuthErrorMapper.fromThrowable(Exception("Invalid login credentials"))
        assertEquals(AuthErrorCode.INVALID_CREDENTIALS.name, error.code)
        assertFalse(error.userMessage.contains("@", ignoreCase = false) && error.userMessage.contains("email.com"))
    }

    @Test
    fun maps_email_not_verified() {
        val error = AuthErrorMapper.fromThrowable(Exception("Debés confirmar tu email antes de iniciar sesión."))
        assertEquals(AuthErrorCode.EMAIL_NOT_VERIFIED.name, error.code)
    }

    @Test
    fun maps_duplicate_to_safe_user_message() {
        val error = AuthErrorMapper.fromThrowable(Exception("User already registered"))
        assertEquals(AuthErrorCode.EMAIL_ALREADY_REGISTERED.name, error.code)
        assertFalse(error.userMessage.contains("Ya existe una cuenta con ese email"))
    }

    @Test
    fun maps_rate_limit() {
        val error = AuthErrorMapper.fromThrowable(Exception("email rate limit exceeded"))
        assertEquals(AuthErrorCode.RATE_LIMITED.name, error.code)
    }

    @Test
    fun maps_recovery_expired() {
        val error = AuthErrorMapper.fromThrowable(Exception("otp_expired"))
        assertEquals(AuthErrorCode.RECOVERY_LINK_EXPIRED.name, error.code)
        assertTrue(error.userMessage.contains("expiró") || error.userMessage.contains("expir"))
        assertFalse(error.userMessage.contains("12345678"))
    }

    @Test
    fun maps_invalid_otp_token() {
        val error = AuthErrorMapper.fromThrowable(Exception("Invalid OTP token"))
        assertEquals(AuthErrorCode.RECOVERY_LINK_INVALID.name, error.code)
        assertTrue(error.userMessage.contains("código") || error.userMessage.contains("enlace"))
        assertFalse(error.userMessage.contains("Invalid OTP"))
    }

    @Test
    fun maps_network() {
        val error = AuthErrorMapper.fromThrowable(SocketTimeoutException("timeout"))
        assertEquals(AuthErrorCode.NETWORK_UNAVAILABLE.name, error.code)
    }

    @Test
    fun maps_password_reset_not_available() {
        val error = AuthErrorMapper.fromThrowable(
            AuthErrorMapper.toException(
                AuthErrorCode.PASSWORD_RESET_NOT_AVAILABLE,
                "Password reset is not available in-app until M01 stage 4"
            )
        )
        assertEquals(AuthErrorCode.PASSWORD_RESET_NOT_AVAILABLE.name, error.code)
    }

    @Test
    fun maps_validation_exception() {
        val failure = AuthValidators.validateEmail("bad").exceptionOrNull() as AuthValidationException
        val error = AuthErrorMapper.fromThrowable(failure)
        assertEquals(AuthErrorCode.INVALID_EMAIL.name, error.code)
    }

    @Test
    fun maps_unknown() {
        val error = AuthErrorMapper.fromThrowable(Exception("weird boom"))
        assertEquals(AuthErrorCode.UNKNOWN_AUTH_ERROR.name, error.code)
        assertTrue(error.technicalMessage.contains("weird"))
    }

    @Test
    fun weak_password_message_mentions_eight() {
        val error = AuthErrorMapper.fromCode(AuthErrorCode.WEAK_PASSWORD, "short")
        assertTrue(error.userMessage.contains("8"))
        assertFalse(error.userMessage.contains("6 caracteres"))
    }
}
