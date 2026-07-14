package com.comunidapp.app.domain.auth.validation

import com.comunidapp.app.core.result.AppError
import com.comunidapp.app.core.result.AppErrorKind
import com.comunidapp.app.domain.auth.AuthErrorCode

data class EmailValidation(
    val normalized: String
)

object AuthValidators {

    const val MIN_PASSWORD_LENGTH = 8
    /** Compatible con límites habituales de Supabase Auth / GoTrue. */
    const val MAX_PASSWORD_LENGTH = 72
    const val MAX_EMAIL_LENGTH = 254

    private val EMAIL_REGEX = Regex(
        "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    )

    fun normalizeEmail(raw: String): String = raw.trim().lowercase()

    fun validateEmail(raw: String): Result<EmailValidation> {
        val normalized = normalizeEmail(raw)
        return when {
            normalized.isEmpty() -> failure(AuthErrorCode.INVALID_EMAIL, "Email obligatorio")
            normalized.length > MAX_EMAIL_LENGTH ->
                failure(AuthErrorCode.INVALID_EMAIL, "Email demasiado largo")
            !EMAIL_REGEX.matches(normalized) ->
                failure(AuthErrorCode.INVALID_EMAIL, "Email con formato inválido")
            else -> Result.success(EmailValidation(normalized))
        }
    }

    fun validatePassword(password: String): Result<Unit> {
        return when {
            password.isEmpty() -> failure(AuthErrorCode.WEAK_PASSWORD, "Contraseña obligatoria")
            password.length < MIN_PASSWORD_LENGTH ->
                failure(
                    AuthErrorCode.WEAK_PASSWORD,
                    "La contraseña debe tener al menos $MIN_PASSWORD_LENGTH caracteres"
                )
            password.length > MAX_PASSWORD_LENGTH ->
                failure(AuthErrorCode.WEAK_PASSWORD, "Contraseña demasiado larga")
            else -> Result.success(Unit)
        }
    }

    fun validatePasswordConfirmation(password: String, confirmPassword: String): Result<Unit> {
        validatePassword(password).getOrElse { return Result.failure(it) }
        return if (password != confirmPassword) {
            failure(AuthErrorCode.WEAK_PASSWORD, "Las contraseñas no coinciden")
        } else {
            Result.success(Unit)
        }
    }

    fun validateConsents(
        acceptedTerms: Boolean,
        acceptedPrivacy: Boolean,
        termsVersion: String,
        privacyVersion: String
    ): Result<Unit> {
        if (!acceptedTerms || !acceptedPrivacy) {
            return failure(
                AuthErrorCode.UNKNOWN_AUTH_ERROR,
                "Debés aceptar términos y política de privacidad",
                kind = AppErrorKind.VALIDATION,
                codeOverride = "CONSENTS_REQUIRED"
            )
        }
        if (termsVersion.isBlank() || privacyVersion.isBlank()) {
            return failure(
                AuthErrorCode.CONFIGURATION_ERROR,
                "Versiones legales no configuradas",
                kind = AppErrorKind.CONFIGURATION
            )
        }
        return Result.success(Unit)
    }

    private fun failure(
        code: AuthErrorCode,
        technical: String,
        kind: AppErrorKind = AppErrorKind.VALIDATION,
        codeOverride: String? = null
    ): Result<Nothing> = Result.failure(
        AuthValidationException(
            AppError(
                kind = kind,
                userMessage = when (code) {
                    AuthErrorCode.INVALID_EMAIL -> "Revisá el email ingresado."
                    AuthErrorCode.WEAK_PASSWORD ->
                        if (technical.contains("coinciden")) {
                            "Las contraseñas no coinciden."
                        } else {
                            "La contraseña debe tener al menos $MIN_PASSWORD_LENGTH caracteres."
                        }
                    AuthErrorCode.CONFIGURATION_ERROR ->
                        "La aplicación no está configurada correctamente."
                    else -> "Revisá los datos ingresados."
                },
                technicalMessage = technical,
                code = codeOverride ?: code.name
            )
        )
    )
}

class AuthValidationException(
    val error: AppError
) : Exception(error.technicalMessage)
