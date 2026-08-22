package com.comunidapp.app.domain.publish

import com.comunidapp.app.domain.observability.sanitization.SensitiveDataSanitizer

/**
 * Mensajes de publicación seguros para UI.
 * El detalle técnico (PostgREST, URL, headers, tokens) no se muestra al usuario.
 */
object PublishUiErrorMapper {

    const val LOST_FOUND_USER_MESSAGE =
        "No pudimos publicar la alerta.\nIntentá nuevamente."

    fun lostFoundUserMessage(): String = LOST_FOUND_USER_MESSAGE

    fun userFacing(raw: String?, fallback: String): String {
        val text = raw?.trim().orEmpty()
        return if (text.isNotEmpty() && !isUnsafeToShow(text)) text else fallback
    }

    fun userFacing(error: Throwable, fallback: String): String =
        userFacing(error.message, fallback)

    fun sanitizeTechnical(error: Throwable): String {
        val blob = buildString {
            append(error.message.orEmpty())
            generateSequence(error.cause) { it.cause }.forEach { cause ->
                append(' ')
                append(cause.message.orEmpty())
            }
        }
        return SensitiveDataSanitizer.sanitize(blob, maxLength = 400)
    }

    fun isUnsafeToShow(raw: String?): Boolean {
        val text = raw.orEmpty()
        if (text.isBlank()) return true
        val lower = text.lowercase()
        return listOf(
            "http://",
            "https://",
            "authorization",
            "bearer",
            "apikey",
            "api-key",
            "supabase",
            "postgrest",
            "pgrst",
            "gen_random_bytes",
            "content-type",
            "headers",
            "stacktrace",
            "function",
            "does not exist",
            "hint:",
            "code:",
            "status code"
        ).any { lower.contains(it) } || '\n' in text || text.length > 140
    }
}
