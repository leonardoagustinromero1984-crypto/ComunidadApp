package com.comunidapp.app.domain.user

import com.comunidapp.app.core.result.AppError
import com.comunidapp.app.core.result.AppErrorKind

enum class UsernameErrorCode {
    EMPTY,
    TOO_SHORT,
    TOO_LONG,
    INVALID_CHARS,
    MUST_START_ALNUM,
    MUST_NOT_END_DOT,
    CONSECUTIVE_DOTS,
    RESERVED,
    SPACES_NOT_ALLOWED
}

class UsernameValidationException(
    val error: AppError
) : Exception(error.technicalMessage)

object UsernameValidators {

    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 30

    private val ALLOWED = Regex("^[a-z0-9._]+$")

    /** Lista inicial configurable; ampliación en etapas posteriores. */
    val reservedWords: Set<String> = setOf(
        "admin", "administrator", "moderador", "moderator", "soporte", "support",
        "leover", "comunidapp", "root", "system", "null", "undefined",
        "login", "logout", "register", "api", "help", "oficial", "official"
    )

    fun normalize(raw: String): String =
        raw.trim().lowercase()

    fun validate(raw: String): Result<Username> {
        if (raw.any { it.isWhitespace() }) {
            return failure(UsernameErrorCode.SPACES_NOT_ALLOWED, "spaces not allowed")
        }
        val normalized = normalize(raw)
        when {
            normalized.isEmpty() ->
                return failure(UsernameErrorCode.EMPTY, "empty username")
            normalized.length < MIN_LENGTH ->
                return failure(UsernameErrorCode.TOO_SHORT, "too short")
            normalized.length > MAX_LENGTH ->
                return failure(UsernameErrorCode.TOO_LONG, "too long")
            !normalized.first().isLetterOrDigit() ->
                return failure(UsernameErrorCode.MUST_START_ALNUM, "must start alnum")
            normalized.endsWith('.') ->
                return failure(UsernameErrorCode.MUST_NOT_END_DOT, "ends with dot")
            normalized.contains("..") ->
                return failure(UsernameErrorCode.CONSECUTIVE_DOTS, "consecutive dots")
            !ALLOWED.matches(normalized) ->
                return failure(UsernameErrorCode.INVALID_CHARS, "invalid chars")
            reservedWords.contains(normalized) ->
                return failure(UsernameErrorCode.RESERVED, "reserved word")
        }
        return Result.success(Username.ofNormalized(normalized))
    }

    fun isSetupComplete(username: Username?, status: ProfileSetupStatus): Boolean =
        status == ProfileSetupStatus.COMPLETED && username != null

    fun deriveSetupStatus(username: Username?, explicit: ProfileSetupStatus?): ProfileSetupStatus {
        if (explicit != null && explicit != ProfileSetupStatus.NOT_STARTED) return explicit
        return if (username != null) ProfileSetupStatus.COMPLETED else ProfileSetupStatus.NOT_STARTED
    }

    private fun failure(code: UsernameErrorCode, technical: String): Result<Nothing> =
        Result.failure(
            UsernameValidationException(
                AppError(
                    kind = AppErrorKind.VALIDATION,
                    userMessage = userMessage(code),
                    technicalMessage = technical,
                    code = code.name
                )
            )
        )

    private fun userMessage(code: UsernameErrorCode): String = when (code) {
        UsernameErrorCode.EMPTY -> "Ingresá un nombre de usuario."
        UsernameErrorCode.TOO_SHORT -> "El usuario debe tener al menos $MIN_LENGTH caracteres."
        UsernameErrorCode.TOO_LONG -> "El usuario no puede superar $MAX_LENGTH caracteres."
        UsernameErrorCode.INVALID_CHARS ->
            "Usá solo letras minúsculas, números, punto y guion bajo."
        UsernameErrorCode.MUST_START_ALNUM -> "Debe comenzar con letra o número."
        UsernameErrorCode.MUST_NOT_END_DOT -> "No puede terminar con punto."
        UsernameErrorCode.CONSECUTIVE_DOTS -> "No uses puntos consecutivos."
        UsernameErrorCode.RESERVED -> "Ese nombre de usuario no está disponible."
        UsernameErrorCode.SPACES_NOT_ALLOWED -> "Sin espacios."
    }
}

object AccountStatusRules {
    fun canAccessMain(status: AccountStatus): Boolean = when (status) {
        AccountStatus.ACTIVE, AccountStatus.RESTRICTED -> true
        AccountStatus.SUSPENDED, AccountStatus.BANNED -> false
    }

    fun canMutateContent(status: AccountStatus): Boolean =
        status == AccountStatus.ACTIVE
}
