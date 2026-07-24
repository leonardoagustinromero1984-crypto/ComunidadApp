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
    /** 403 / RLS / permiso — no implica cerrar sesión. */
    PERMISSION_DENIED,
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

        val raw = buildString {
            append(throwable.message.orEmpty())
            append(' ')
            append(throwable.cause?.message.orEmpty())
            append(' ')
            append(throwable::class.java.name)
        }
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
        val lower = raw.lowercase()

        if (throwable is UnknownHostException ||
            throwable is ConnectException ||
            throwable is SocketTimeoutException ||
            throwable is IOException ||
            lower.contains("timeout") ||
            lower.contains("timed out") ||
            lower.contains("unable to resolve host") ||
            lower.contains("failed to connect") ||
            lower.contains("connection refused") ||
            lower.contains("cleartext") ||
            lower.contains("cleartexttrafficpermitted") ||
            (lower.contains("network") &&
                !lower.contains("unauthorized") &&
                !lower.contains("invalid login"))
        ) {
            return AuthErrorCode.NETWORK_UNAVAILABLE
        }

        return when {
            lower.contains("password reset is not available") ||
                lower.contains("abrí el link que te enviamos") ->
                AuthErrorCode.PASSWORD_RESET_NOT_AVAILABLE

            lower.contains("invalid login credentials") ||
                lower.contains("email o contraseña incorrectos") ||
                lower.contains("invalid_credentials") ->
                AuthErrorCode.INVALID_CREDENTIALS

            lower.contains("debés confirmar tu email") ||
                lower.contains("email not confirmed") ||
                lower.contains("email_not_confirmed") ->
                AuthErrorCode.EMAIL_NOT_VERIFIED

            lower.contains("user already registered") ||
                lower.contains("already been registered") ||
                lower.contains("ya existe una cuenta") ->
                AuthErrorCode.EMAIL_ALREADY_REGISTERED

            lower.contains("password should be at least") ||
                lower.contains("contraseña debe tener al menos") ->
                AuthErrorCode.WEAK_PASSWORD

            lower.contains("rate limit") ||
                lower.contains("over_email_send_rate_limit") ||
                lower.contains("email rate limit exceeded") ->
                AuthErrorCode.RATE_LIMITED

            // 401 / JWT / sesión — antes que recovery link para que "invalid jwt"
            // o "refresh token invalid" no se confundan con un OTP/enlace de recuperación.
            lower.contains("401") ||
                lower.contains("unauthorized") ||
                lower.contains("invalid jwt") ||
                lower.contains("jwt expired") ||
                lower.contains("session_not_found") ||
                (lower.contains("refresh") && (lower.contains("invalid") || lower.contains("expired"))) ||
                (lower.contains("session") &&
                    (lower.contains("expired") || lower.contains("missing") || lower.contains("not found"))) ->
                AuthErrorCode.SESSION_EXPIRED

            lower.contains("email link is invalid or has expired") ||
                lower.contains("otp_expired") ||
                lower.contains("token has expired") ||
                lower.contains("código inválido o expirado") ->
                AuthErrorCode.RECOVERY_LINK_EXPIRED

            lower.contains("invalid") &&
                (lower.contains("link") ||
                    lower.contains("token") ||
                    lower.contains("otp") ||
                    lower.contains("código") ||
                    lower.contains("codigo")) ->
                AuthErrorCode.RECOVERY_LINK_INVALID

            // 403 / RLS — no forzar logout
            lower.contains("403") ||
                lower.contains("forbidden") ||
                lower.contains("row-level security") ||
                lower.contains("rls") ||
                lower.contains("permission denied") ||
                lower.contains("not authorized") && !lower.contains("login") ->
                AuthErrorCode.PERMISSION_DENIED

            lower.contains("supabase") &&
                (lower.contains("config") || lower.contains("not configured") ||
                    lower.contains("not available") || lower.contains("client requires")) ->
                AuthErrorCode.CONFIGURATION_ERROR

            lower.contains("account deletion") ||
                lower.contains("account_deletion") ->
                AuthErrorCode.ACCOUNT_DELETION_FAILED

            lower.contains("consent") ||
                lower.contains("legal consent") ->
                AuthErrorCode.LEGAL_CONSENT_REQUIRED

            lower.contains("email") &&
                (lower.contains("invalid") || lower.contains("formato")) ->
                AuthErrorCode.INVALID_EMAIL

            else -> AuthErrorCode.UNKNOWN_AUTH_ERROR
        }
    }

    private fun kindFor(code: AuthErrorCode): AppErrorKind = when (code) {
        AuthErrorCode.INVALID_CREDENTIALS,
        AuthErrorCode.EMAIL_NOT_VERIFIED,
        AuthErrorCode.SESSION_EXPIRED -> AppErrorKind.UNAUTHORIZED
        AuthErrorCode.PERMISSION_DENIED -> AppErrorKind.FORBIDDEN
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
            "El correo o la contraseña son incorrectos."
        AuthErrorCode.EMAIL_NOT_VERIFIED ->
            "Tu correo todavía no fue confirmado."
        AuthErrorCode.EMAIL_ALREADY_REGISTERED ->
            "No se pudo completar el registro. Revisá tu email o iniciá sesión."
        AuthErrorCode.WEAK_PASSWORD ->
            "La contraseña debe tener al menos 8 caracteres."
        AuthErrorCode.INVALID_EMAIL ->
            "Revisá el email ingresado."
        AuthErrorCode.RECOVERY_LINK_INVALID ->
            "El enlace o código no es válido."
        AuthErrorCode.RECOVERY_LINK_EXPIRED ->
            "El enlace o código expiró. Solicitá uno nuevo."
        AuthErrorCode.SESSION_EXPIRED ->
            "Tu sesión venció. Iniciá sesión nuevamente."
        AuthErrorCode.RATE_LIMITED ->
            "Demasiados intentos. Probá más tarde."
        AuthErrorCode.NETWORK_UNAVAILABLE ->
            "No pudimos conectarnos. Revisá tu conexión."
        AuthErrorCode.CONFIGURATION_ERROR ->
            "La configuración de Supabase no está disponible en esta versión."
        AuthErrorCode.PERMISSION_DENIED ->
            "No tenés permisos para realizar esta acción."
        AuthErrorCode.ACCOUNT_DELETION_FAILED ->
            "No se pudo eliminar la cuenta. Intentá más tarde."
        AuthErrorCode.PASSWORD_RESET_NOT_AVAILABLE ->
            "Abrí el enlace del email para continuar el restablecimiento."
        AuthErrorCode.LEGAL_CONSENT_REQUIRED ->
            "Debés aceptar los términos y la política de privacidad vigentes."
        AuthErrorCode.UNKNOWN_AUTH_ERROR ->
            "No pudimos completar el inicio de sesión. Intentá de nuevo."
    }
}
