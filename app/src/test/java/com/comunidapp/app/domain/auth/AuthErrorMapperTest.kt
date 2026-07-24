package com.comunidapp.app.domain.auth

import com.comunidapp.app.core.result.AppErrorKind
import com.comunidapp.app.domain.auth.validation.AuthValidationException
import com.comunidapp.app.domain.auth.validation.AuthValidators
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException

/**
 * LeoVer — mapeo de errores de auth tras el hotfix APK localDebug (post M12 Bloque 3).
 * Verifica los mensajes seguros para personas usuarias y los códigos nuevos (PERMISSION_DENIED,
 * 401 → SESSION_EXPIRED, cleartext/red → NETWORK, config → CONFIGURATION_ERROR).
 */
class AuthErrorMapperTest {

    @Test
    fun maps_invalid_credentials() {
        val error = AuthErrorMapper.fromThrowable(Exception("Invalid login credentials"))
        assertEquals(AuthErrorCode.INVALID_CREDENTIALS.name, error.code)
        assertEquals("El correo o la contraseña son incorrectos.", error.userMessage)
        assertEquals(AppErrorKind.UNAUTHORIZED, error.kind)
    }

    @Test
    fun maps_email_not_verified() {
        val error = AuthErrorMapper.fromThrowable(Exception("Email not confirmed"))
        assertEquals(AuthErrorCode.EMAIL_NOT_VERIFIED.name, error.code)
        assertEquals("Tu correo todavía no fue confirmado.", error.userMessage)
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
        assertEquals("No pudimos conectarnos. Revisá tu conexión.", error.userMessage)
        assertEquals(AppErrorKind.NETWORK, error.kind)
    }

    @Test
    fun maps_cleartext_to_network() {
        val error = AuthErrorMapper.fromThrowable(
            Exception("CLEARTEXT communication to 10.0.2.2 not permitted by network security policy")
        )
        assertEquals(AuthErrorCode.NETWORK_UNAVAILABLE.name, error.code)
        assertEquals("No pudimos conectarnos. Revisá tu conexión.", error.userMessage)
    }

    @Test
    fun maps_failed_to_connect_to_network() {
        val error = AuthErrorMapper.fromThrowable(Exception("Failed to connect to /10.0.2.2:55321"))
        assertEquals(AuthErrorCode.NETWORK_UNAVAILABLE.name, error.code)
    }

    @Test
    fun maps_401_to_session_expired() {
        val error = AuthErrorMapper.fromThrowable(Exception("HTTP 401 Unauthorized"))
        assertEquals(AuthErrorCode.SESSION_EXPIRED.name, error.code)
        assertEquals("Tu sesión venció. Iniciá sesión nuevamente.", error.userMessage)
        assertEquals(AppErrorKind.UNAUTHORIZED, error.kind)
    }

    @Test
    fun maps_invalid_jwt_to_session_expired() {
        val error = AuthErrorMapper.fromThrowable(Exception("invalid JWT: token is expired"))
        assertEquals(AuthErrorCode.SESSION_EXPIRED.name, error.code)
    }

    @Test
    fun maps_refresh_token_invalid_to_session_expired() {
        val error = AuthErrorMapper.fromThrowable(Exception("refresh token is invalid"))
        assertEquals(AuthErrorCode.SESSION_EXPIRED.name, error.code)
    }

    @Test
    fun maps_403_to_permission_denied() {
        val error = AuthErrorMapper.fromThrowable(Exception("HTTP 403 Forbidden"))
        assertEquals(AuthErrorCode.PERMISSION_DENIED.name, error.code)
        assertEquals(AppErrorKind.FORBIDDEN, error.kind)
    }

    @Test
    fun maps_row_level_security_to_permission_denied() {
        val error = AuthErrorMapper.fromThrowable(
            Exception("new row violates row-level security policy for table \"pets\"")
        )
        assertEquals(AuthErrorCode.PERMISSION_DENIED.name, error.code)
        assertEquals(AppErrorKind.FORBIDDEN, error.kind)
    }

    @Test
    fun maps_configuration_error() {
        val error = AuthErrorMapper.fromThrowable(
            Exception("supabase client requires remote HTTPS url + anon key; not configured")
        )
        assertEquals(AuthErrorCode.CONFIGURATION_ERROR.name, error.code)
        assertEquals(
            "La configuración de Supabase no está disponible en esta versión.",
            error.userMessage
        )
        assertEquals(AppErrorKind.CONFIGURATION, error.kind)
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
    fun unknown_message_no_longer_uses_legacy_copy() {
        val error = AuthErrorMapper.fromCode(AuthErrorCode.UNKNOWN_AUTH_ERROR, "boom")
        assertFalse(error.userMessage.contains("Ocurrió un problema de autenticación"))
        assertEquals("No pudimos completar el inicio de sesión. Intentá de nuevo.", error.userMessage)
    }

    @Test
    fun weak_password_message_mentions_eight() {
        val error = AuthErrorMapper.fromCode(AuthErrorCode.WEAK_PASSWORD, "short")
        assertTrue(error.userMessage.contains("8"))
        assertFalse(error.userMessage.contains("6 caracteres"))
    }

    @Test
    fun user_messages_never_leak_technical_detail() {
        val error = AuthErrorMapper.fromThrowable(Exception("HTTP 401 at https://secret.supabase.co token=abc"))
        assertFalse(error.userMessage.contains("supabase.co"))
        assertFalse(error.userMessage.contains("token=abc"))
    }
}
