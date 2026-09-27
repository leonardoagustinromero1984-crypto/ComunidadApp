package com.comunidapp.app.domain.publish

/**
 * Stable FOUND/LOST publish codes for QA/staging.
 * Human copy stays generic; the code is always appended.
 */
object LostFoundPublishError {
    const val AUTH = "LF-CREATE-AUTH"
    const val KIND = "LF-CREATE-KIND"
    const val FORBIDDEN = "LF-CREATE-FORBIDDEN"
    const val LOCATION = "LF-CREATE-LOCATION"
    const val IDENTITY = "LF-CREATE-IDENTITY"
    const val ALERT = "LF-CREATE-ALERT"
    const val VALIDATION = "LF-CREATE-VALIDATION"
    const val SESSION = "LF-CREATE-SESSION"
    const val DB = "LF-CREATE-DB"
    const val UNKNOWN = "LF-CREATE-UNKNOWN"

    private val staged = Regex("""\b(LF-CREATE-[A-Z0-9_]+)\b""")

    fun rawBlob(error: Throwable?): String = buildString {
        append(error?.message.orEmpty())
        append(' ')
        append(error?.javaClass?.simpleName.orEmpty())
        generateSequence(error?.cause) { it.cause }.forEach { cause ->
            append(' ')
            append(cause.message.orEmpty())
            append(' ')
            append(cause.javaClass.simpleName)
        }
        append(' ')
        append(error?.toString().orEmpty())
    }

    fun codeOf(error: Throwable?): String {
        val raw = rawBlob(error)
        staged.find(raw)?.groupValues?.getOrNull(1)?.let { return it }
        val lower = raw.lowercase()
        return when {
            lower.contains("not_authenticated") || lower.contains("iniciar sesión") -> AUTH
            lower.contains("kind_invalid") -> KIND
            lower.contains("forbidden") -> FORBIDDEN
            lower.contains("location_invalid") ||
                lower.contains("st_makepoint") ||
                lower.contains("st_setsrid") ||
                lower.contains("_canon_geo_point") -> LOCATION
            lower.contains("pgrst202") || lower.contains("could not find the function") -> ALERT
            lower.contains("42883") ||
                lower.contains("sqlstate") ||
                lower.contains("does not exist") -> DB
            else -> UNKNOWN
        }
    }

    fun userMessage(error: Throwable? = null): String =
        PublishUiErrorMapper.LOST_FOUND_USER_MESSAGE + "\n" + codeOf(error)
}
