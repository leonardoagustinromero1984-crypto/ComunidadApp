package com.comunidapp.app.domain.organization

import com.comunidapp.app.core.result.AppError
import com.comunidapp.app.core.result.AppErrorKind

enum class OrganizationSlugErrorCode {
    EMPTY,
    TOO_SHORT,
    TOO_LONG,
    INVALID_CHARS,
    MUST_START_ALNUM,
    MUST_END_ALNUM,
    CONSECUTIVE_HYPHENS,
    SPACES_NOT_ALLOWED,
    RESERVED
}

class OrganizationSlugValidationException(
    val error: AppError
) : Exception(error.technicalMessage)

object OrganizationSlugValidators {

    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 50

    private val ALLOWED = Regex("^[a-z0-9-]+$")

    val reservedWords: Set<String> = setOf(
        "admin", "administrator", "api", "www", "app", "leover", "comunidapp",
        "login", "logout", "register", "support", "soporte", "system", "root",
        "null", "undefined", "org", "organization", "organizations", "me", "settings"
    )

    fun normalize(raw: String): String =
        raw.trim().lowercase()

    fun validate(raw: String): Result<OrganizationSlug> {
        if (raw.any { it.isWhitespace() }) {
            return failure(OrganizationSlugErrorCode.SPACES_NOT_ALLOWED, "spaces")
        }
        val normalized = normalize(raw)
        when {
            normalized.isEmpty() ->
                return failure(OrganizationSlugErrorCode.EMPTY, "empty")
            normalized.length < MIN_LENGTH ->
                return failure(OrganizationSlugErrorCode.TOO_SHORT, "short")
            normalized.length > MAX_LENGTH ->
                return failure(OrganizationSlugErrorCode.TOO_LONG, "long")
            !normalized.first().isLetterOrDigit() ->
                return failure(OrganizationSlugErrorCode.MUST_START_ALNUM, "start")
            !normalized.last().isLetterOrDigit() ->
                return failure(OrganizationSlugErrorCode.MUST_END_ALNUM, "end")
            normalized.contains("--") ->
                return failure(OrganizationSlugErrorCode.CONSECUTIVE_HYPHENS, "hyphens")
            !ALLOWED.matches(normalized) ->
                return failure(OrganizationSlugErrorCode.INVALID_CHARS, "chars")
            reservedWords.contains(normalized) ->
                return failure(OrganizationSlugErrorCode.RESERVED, "reserved")
        }
        return Result.success(OrganizationSlug.ofNormalized(normalized))
    }

    private fun failure(code: OrganizationSlugErrorCode, technical: String): Result<Nothing> =
        Result.failure(
            OrganizationSlugValidationException(
                AppError(
                    kind = AppErrorKind.VALIDATION,
                    userMessage = userMessage(code),
                    technicalMessage = technical,
                    code = code.name
                )
            )
        )

    private fun userMessage(code: OrganizationSlugErrorCode): String = when (code) {
        OrganizationSlugErrorCode.EMPTY -> "Ingresá un identificador público."
        OrganizationSlugErrorCode.TOO_SHORT ->
            "Debe tener al menos $MIN_LENGTH caracteres."
        OrganizationSlugErrorCode.TOO_LONG ->
            "No puede superar $MAX_LENGTH caracteres."
        OrganizationSlugErrorCode.INVALID_CHARS ->
            "Usá solo minúsculas, números y guiones."
        OrganizationSlugErrorCode.MUST_START_ALNUM ->
            "Debe comenzar con letra o número."
        OrganizationSlugErrorCode.MUST_END_ALNUM ->
            "Debe terminar con letra o número."
        OrganizationSlugErrorCode.CONSECUTIVE_HYPHENS ->
            "No uses guiones consecutivos."
        OrganizationSlugErrorCode.SPACES_NOT_ALLOWED -> "Sin espacios."
        OrganizationSlugErrorCode.RESERVED -> "Ese identificador no está disponible."
    }
}
