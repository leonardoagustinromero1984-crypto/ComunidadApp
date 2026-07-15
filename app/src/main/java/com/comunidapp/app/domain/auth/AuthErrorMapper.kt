package com.comunidapp.app.domain.auth

import com.comunidapp.app.core.result.AppError
import com.comunidapp.app.core.result.AppErrorKind
import com.comunidapp.app.domain.auth.validation.AuthValidationException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

enum class AuthErrorCode {
    INVALID_CREDENTIALS,
    EMAIL_NOT_VERIFIED,
    EMAIL_ALREADY_REGISTERED,
    WEAK_PASSWORD,
    INVALID_EMAIL,
    RECOVERY_LINK_INVALID,
    RECOVERY_LINK_EXPIRED,
    SESSION_EXPIRED,
    RATE_LIMITED,
    NETWORK_UNAVAILABLE,
    CONFIGURATION_ERROR,
    ACCOUNT_DELETION_FAILED,
    /** Sin sesión de recovery válida para completar el reset. */
    PASSWORD_RESET_NOT_AVAILABLE,
    LEGAL_CONSENT_REQUIRED,
    UNKNOWN_AUTH_ERROR
}

class AuthException(
    val authError: AppError,
    cause: Throwable? = null
) : Exception(authError.technicalMessage, cause) {
    val code: String get() = authError.code ?: AuthErrorCode.UNKNOWN_AUTH_ERROR.name
}

object AuthErrorMapper {

    fun fromCode(
        code: AuthErrorCode,
        technicalMessage: String,
        cause: Throwable? = null
    ): AppError = AppError(
        kind = kindFor(code),
        userMessage = userMessageFor(code),
        technicalMessage = technicalMessage.take(300),
        cause = cause,
        code = code.name
    )

    fun toException(
        code: AuthErrorCode,
        technicalMessage: String,
        cause: Throwable? = null
    ): AuthException = AuthException(fromCode(code, technicalMessage, cause), cause)

    fun fromThrowable(throwable: Throwable): AppError {
        when (throwable) {
            is AuthValidationException -> return throwable.error
            is AuthException -> return throwable.authError
        }

        val raw = throwable.message.orEmpty()
        val code = detectCode(throwable, raw)
        val technical = when (code) {
            AuthErrorCode.UNKNOWN_AUTH_ERROR ->
                raw.ifBlank { throwable::class.java.simpleName }.take(300)
            else -> raw.take(300).ifBlank { code.name }
        }
        return fromCode(code, technical, throwable)
    }

    fun fromThrowableToException(throwable: Throwable): AuthException {
        if (throwable is AuthException) return throwable
        return AuthException(fromThrowable(throwable), throwable)
    }

    private fun detectCode(throwable: Throwable, raw: String): AuthErrorCode {
        if (throwable is UnknownHostException ||
            throwable is ConnectException ||
            throwable is SocketTimeoutException ||
            throwable is IOException
        ) {
            return AuthErrorCode.NETWORK_UNAVAILABLE
        }

        return when {
            raw.contains("Password reset is not available", ignoreCase = true) ||
                raw.contains("Abrí el link que te enviamos", ignoreCase = true) ->
                AuthErrorCode.PASSWORD_RESET_NOT_AVAILABLE

            raw.contains("Invalid login credentials", ignoreCase = true) ||
                raw.contains("Email o contraseña incorrectos", ignoreCase = true) ->
                AuthErrorCode.INVALID_CREDENTIALS

            raw.contains("Debés confirmar tu email", ignoreCase = true) ||
                raw.contains("Email not confirmed", ignoreCase = true) ||
                raw.contains("email_not_confirmed", ignoreCase = true) ->
                AuthErrorCode.EMAIL_NOT_VERIFIED

            raw.contains("User already registered", ignoreCase = true) ||
                raw.contains("already been registered", ignoreCase = true) ||
                raw.contains("Ya existe una cuenta", ignoreCase = true) ->
                AuthErrorCode.EMAIL_ALREADY_REGISTERED

            raw.contains("Password should be at least", ignoreCase = true) ||
                raw.contains("contraseña debe tener al menos", ignoreCase = true) ->
                AuthErrorCode.WEAK_PASSWORD

            raw.contains("rate limit", ignoreCase = true) ||
                raw.contains("over_email_send_rate_limit", ignoreCase = true) ||
                raw.contains("email rate limit exceeded", ignoreCase = true) ->
                AuthErrorCode.RATE_LIMITED

            raw.contains("Email link is invalid or has expired", ignoreCase = true) ||
                raw.contains("otp_expired", ignoreCase = true) ||
                raw.contains("Código inválido o expirado", ignoreCase = true) ->
                AuthErrorCode.RECOVERY_LINK_EXPIRED

            raw.contains("invalid", ignoreCase = true) &&
                (raw.contains("link", ignoreCase = true) || raw.contains("token", ignoreCase = true)) ->
                AuthErrorCode.RECOVERY_LINK_INVALID

            raw.contains("session", ignoreCase = true) &&
                (raw.contains("expired", ignoreCase = true) || raw.contains("missing", ignoreCase = true)) ->
                AuthErrorCode.SESSION_EXPIRED

            raw.contains("SUPABASE", ignoreCase = true) &&
                raw.contains("config", ignoreCase = true) ->
                AuthErrorCode.CONFIGURATION_ERROR

            raw.contains("account deletion", ignoreCase = true) ||
                raw.contains("ACCOUNT_DELETION", ignoreCase = true) ->
                AuthErrorCode.ACCOUNT_DELETION_FAILED

            raw.contains("CONSENT", ignoreCase = true) ||
                raw.contains("legal consent", ignoreCase = true) ->
                AuthErrorCode.LEGAL_CONSENT_REQUIRED

            raw.contains("email", ignoreCase = true) &&
                (raw.contains("invalid", ignoreCase = true) || raw.contains("formato", ignoreCase = true)) ->
                AuthErrorCode.INVALID_EMAIL

            else -> AuthErrorCode.UNKNOWN_AUTH_ERROR
        }
    }

    private fun kindFor(code: AuthErrorCode): AppErrorKind = when (code) {
        AuthErrorCode.INVALID_CREDENTIALS,
        AuthErrorCode.EMAIL_NOT_VERIFIED,
        AuthErrorCode.SESSION_EXPIRED -> AppErrorKind.UNAUTHORIZED
        AuthErrorCode.EMAIL_ALREADY_REGISTERED -> AppErrorKind.CONFLICT
        AuthErrorCode.WEAK_PASSWORD,
        AuthErrorCode.INVALID_EMAIL,
        AuthErrorCode.RECOVERY_LINK_INVALID,
        AuthErrorCode.RECOVERY_LINK_EXPIRED,
        AuthErrorCode.PASSWORD_RESET_NOT_AVAILABLE,
        AuthErrorCode.LEGAL_CONSENT_REQUIRED -> AppErrorKind.VALIDATION
        AuthErrorCode.RATE_LIMITED -> AppErrorKind.RATE_LIMITED
        AuthErrorCode.NETWORK_UNAVAILABLE -> AppErrorKind.NETWORK
        AuthErrorCode.CONFIGURATION_ERROR -> AppErrorKind.CONFIGURATION
        AuthErrorCode.ACCOUNT_DELETION_FAILED -> AppErrorKind.SERVER
        AuthErrorCode.UNKNOWN_AUTH_ERROR -> AppErrorKind.UNKNOWN
    }

    private fun userMessageFor(code: AuthErrorCode): String = when (code) {
        AuthErrorCode.INVALID_CREDENTIALS ->
            "Email o contraseña incorrectos."
        AuthErrorCode.EMAIL_NOT_VERIFIED ->
            "Debés confirmar tu email antes de iniciar sesión."
        AuthErrorCode.EMAIL_ALREADY_REGISTERED ->
            "No se pudo completar el registro. Revisá tu email o iniciá sesión."
        AuthErrorCode.WEAK_PASSWORD ->
            "La contraseña debe tener al menos 8 caracteres."
        AuthErrorCode.INVALID_EMAIL ->
            "Revisá el email ingresado."
        AuthErrorCode.RECOVERY_LINK_INVALID ->
            "El enlace de recuperación no es válido."
        AuthErrorCode.RECOVERY_LINK_EXPIRED ->
            "El enlace o código expiró. Solicitá uno nuevo."
        AuthErrorCode.SESSION_EXPIRED ->
            "Tu sesión expiró. Volvé a iniciar sesión."
        AuthErrorCode.RATE_LIMITED ->
            "Demasiados intentos. Probá más tarde."
        AuthErrorCode.NETWORK_UNAVAILABLE ->
            "Sin conexión o el servicio no responde."
        AuthErrorCode.CONFIGURATION_ERROR ->
            "La aplicación no está configurada correctamente."
        AuthErrorCode.ACCOUNT_DELETION_FAILED ->
            "No se pudo eliminar la cuenta. Intentá más tarde."
        AuthErrorCode.PASSWORD_RESET_NOT_AVAILABLE ->
            "Abrí el enlace del email para continuar el restablecimiento."
        AuthErrorCode.LEGAL_CONSENT_REQUIRED ->
            "Debés aceptar los términos y la política de privacidad vigentes."
        AuthErrorCode.UNKNOWN_AUTH_ERROR ->
            "Ocurrió un problema de autenticación. Intentá de nuevo."
    }
}
