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
    SPACES_NOT_ALLOWED,
    LOOKS_LIKE_UUID,
    LOOKS_TECHNICAL
}

class UsernameValidationException(
    val error: AppError
) : Exception(error.technicalMessage)

object UsernameValidators {

    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 30

    private val ALLOWED = Regex("^[a-z0-9._]+$")
    private val UUID_LIKE = Regex(
        "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
    )

    /**
     * Lista reutilizable alineada con `reserved_usernames` (migración 079).
     * No colocar reglas distintas en pantallas.
     */
    val reservedWords: Set<String> = setOf(
        "admin", "administrator", "administrador",
        "soporte", "support",
        "seguridad", "security",
        "leover", "oficial", "official",
        "sistema", "system",
        "moderacion", "moderation", "moderador", "moderator",
        "root", "null", "undefined",
        "api", "auth", "login", "logout", "register",
        "user", "usuario", "sistemas",
        "comunidapp", "www", "help"
    )

    fun normalize(raw: String): String {
        var value = raw.trim().lowercase()
        while (value.startsWith("@")) {
            value = value.removePrefix("@").trim()
        }
        return value
    }

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
            UUID_LIKE.matches(normalized) ->
                return failure(UsernameErrorCode.LOOKS_LIKE_UUID, "uuid-like")
            normalized.all { it.isDigit() } ->
                return failure(UsernameErrorCode.LOOKS_TECHNICAL, "digits-only")
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

    fun userMessage(code: UsernameErrorCode): String = when (code) {
        UsernameErrorCode.EMPTY -> "Escribí un nombre de usuario."
        UsernameErrorCode.TOO_SHORT -> "El nombre debe tener al menos $MIN_LENGTH caracteres."
        UsernameErrorCode.TOO_LONG -> "El nombre no puede superar $MAX_LENGTH caracteres."
        UsernameErrorCode.INVALID_CHARS ->
            "Solo puede contener letras, números, punto y guion bajo."
        UsernameErrorCode.MUST_START_ALNUM -> "Debe comenzar con letra o número."
        UsernameErrorCode.MUST_NOT_END_DOT -> "No puede terminar con punto."
        UsernameErrorCode.CONSECUTIVE_DOTS -> "No uses puntos consecutivos."
        UsernameErrorCode.RESERVED -> "Este nombre está reservado."
        UsernameErrorCode.SPACES_NOT_ALLOWED -> "Sin espacios."
        UsernameErrorCode.LOOKS_LIKE_UUID -> "Elegí un nombre legible, no un identificador técnico."
        UsernameErrorCode.LOOKS_TECHNICAL -> "Elegí un nombre legible."
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
}

object AccountStatusRules {
    fun canAccessMain(status: AccountStatus): Boolean = when (status) {
        AccountStatus.ACTIVE, AccountStatus.RESTRICTED -> true
        AccountStatus.SUSPENDED, AccountStatus.BANNED -> false
    }

    fun canMutateContent(status: AccountStatus): Boolean =
        status == AccountStatus.ACTIVE
}
