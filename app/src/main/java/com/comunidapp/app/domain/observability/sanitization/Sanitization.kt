package com.comunidapp.app.domain.observability.sanitization

/**
 * Modelo seguro de Throwable. Nunca incluye stack raw ni PII.
 */
data class SanitizedThrowable(
    val errorClass: String,
    val safeMessage: String,
    val fingerprint: String,
    val causeDepth: Int,
    val isRetryable: Boolean
)

object SensitiveDataSanitizer {

    private val jwt = Regex("eyJ[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+")
    private val bearer = Regex("(?i)(bearer\\s+)[a-zA-Z0-9._\\-]+")
    private val email = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
    private val phone = Regex("(?<!\\d)(?:\\+?\\d[\\d\\s\\-]{7,}\\d)")
    private val keyAssign = Regex(
        "(?i)(password|passwd|pwd|token|access[_-]?token|refresh[_-]?token|api[_-]?key|anon[_-]?key|secret|authorization|service[_-]?role|fcm[_-]?token)\\s*[=:]\\s*[^\\s,;\"']+"
    )
    private val signedUrl = Regex("(?i)(signed[-_]?url\\s*[=:]\\s*\\S+|signed[-_]?url|https?://\\S+|x-amz-|X-Goog-)")
    private val sql = Regex("(?i)\\b(select|insert|update|delete|drop|alter|grant|revoke)\\b[^\\n]{0,200}")
    private val stack = Regex("(?i)(at\\s+[\\w.$]+\\([\\w.]+:\\d+\\)|Caused by:)")
    private val localPath = Regex("(?i)([A-Z]:\\\\[^\\s\"']+|/(?:Users|home|data|var)/[^\\s\"']+)")
    private val headers = Regex("(?i)(authorization|x-api-key|cookie)\\s*[:=]\\s*[^\\s,;]+")
    private val base64 = Regex("(?i)(?:[A-Za-z0-9+/]{64,}={0,2})")
    private val chat = Regex("(?i)(chat[_-]?body|message[_-]?body|conversation[_-]?text)\\s*[=:]\\s*[^\\n]+")
    private val internal = Regex("(?i)\\b(INTERNAL|internal[_-]?note|internal[_-]?body)\\b[^\\n]{0,120}")
    private val coords = Regex("(-?\\d{1,3}\\.\\d{3,})\\s*,\\s*(-?\\d{1,3}\\.\\d{3,})")
    private val providerMsg = Regex("(?i)(provider[_-]?message[_-]?id|fcm[_-]?message[_-]?id)\\s*[=:]\\s*[^\\s,;]+")

    const val MAX_LENGTH = 512

    fun sanitize(input: String?, maxLength: Int = MAX_LENGTH): String {
        if (input.isNullOrBlank()) return ""
        var result = input
        result = jwt.replace(result, "[REDACTED_TOKEN]")
        result = bearer.replace(result, "$1[REDACTED_TOKEN]")
        result = keyAssign.replace(result, "$1=[REDACTED]")
        result = signedUrl.replace(result, "[REDACTED_URL]")
        result = headers.replace(result, "$1=[REDACTED]")
        result = providerMsg.replace(result, "$1=[REDACTED]")
        result = email.replace(result, "[REDACTED_EMAIL]")
        result = phone.replace(result, "[REDACTED_PHONE]")
        result = coords.replace(result, "[REDACTED_COORDS]")
        result = sql.replace(result, "[REDACTED_SQL]")
        result = stack.replace(result, "[REDACTED_STACK]")
        result = localPath.replace(result, "[REDACTED_PATH]")
        result = base64.replace(result, "[REDACTED_BASE64]")
        result = chat.replace(result, "$1=[REDACTED_CHAT]")
        result = internal.replace(result, "[REDACTED_INTERNAL]")
        if (result.length > maxLength) {
            result = result.take(maxLength) + "…"
        }
        return result
    }

    fun containsForbiddenLiteral(raw: String): Boolean {
        val lower = raw.lowercase()
        return listOf(
            "password", "bearer ", "eyj", "service_role", "signed_url",
            "access_token", "refresh_token", "begin private key", "select * from",
            "internal_note", "chat_body"
        ).any { lower.contains(it) }
    }
}

object ThrowableSanitizer {

    const val MAX_DEPTH = 5
    const val MAX_MESSAGE = 240

    private val retryableHints = setOf(
        "timeout", "unavailable", "connection", "network", "429", "503", "504", "retry"
    )

    fun sanitize(throwable: Throwable?, maxDepth: Int = MAX_DEPTH): SanitizedThrowable {
        if (throwable == null) {
            return SanitizedThrowable(
                errorClass = "None",
                safeMessage = "",
                fingerprint = "none",
                causeDepth = 0,
                isRetryable = false
            )
        }
        val visited = mutableSetOf<Int>()
        var current: Throwable? = throwable
        var depth = 0
        val classNames = mutableListOf<String>()
        var lastMessage = ""
        while (current != null && depth < maxDepth) {
            val id = System.identityHashCode(current)
            if (!visited.add(id)) {
                classNames.add("Cycle")
                break
            }
            classNames.add(current::class.java.simpleName.ifBlank { "Throwable" })
            lastMessage = current.message.orEmpty()
            current = current.cause
            depth++
        }
        val safeMessage = SensitiveDataSanitizer.sanitize(lastMessage, MAX_MESSAGE)
        val errorClass = classNames.first()
        val fingerprint = fingerprintOf(classNames, safeMessage)
        val isRetryable = retryableHints.any { safeMessage.lowercase().contains(it) } ||
            classNames.any { it.contains("Timeout", ignoreCase = true) || it.contains("IOException") }
        return SanitizedThrowable(
            errorClass = errorClass,
            safeMessage = safeMessage,
            fingerprint = fingerprint,
            causeDepth = depth,
            isRetryable = isRetryable
        )
    }

    fun fingerprintOf(classNames: List<String>, safeMessage: String): String {
        val normalized = buildString {
            append(classNames.joinToString(">"))
            append('|')
            append(safeMessage.replace(Regex("\\d+"), "#").take(80))
        }
        var hash = 0L
        for (ch in normalized) {
            hash = (hash * 31 + ch.code) and 0x7fffffffL
        }
        return "fp_%08x".format(hash)
    }
}

object MetadataSanitizer {
    fun sanitizeValues(metadata: Map<String, String>): Map<String, String> =
        metadata.mapValues { (_, v) -> SensitiveDataSanitizer.sanitize(v, 256) }
}

object StructuredLogSanitizer {
    fun sanitizeMessage(message: String): String = SensitiveDataSanitizer.sanitize(message)

    fun sanitizeForLog(message: String, throwable: Throwable?): Pair<String, SanitizedThrowable?> {
        val msg = SensitiveDataSanitizer.sanitize(message)
        val st = throwable?.let { ThrowableSanitizer.sanitize(it) }
        return msg to st
    }
}

object ObservabilityErrorSanitizer {
    fun safeTechnicalMessage(code: String, detail: String? = null): String {
        val safeCode = code.take(64).ifBlank { "OBS_UNKNOWN" }
        val safeDetail = detail?.let { SensitiveDataSanitizer.sanitize(it, 120) }.orEmpty()
        return if (safeDetail.isBlank()) safeCode else "$safeCode:$safeDetail"
    }
}
