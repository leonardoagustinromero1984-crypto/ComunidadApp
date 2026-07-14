package com.comunidapp.app.core.logging

import android.util.Log
import com.comunidapp.app.core.config.AppConfigProvider
import java.util.regex.Pattern

enum class LogLevel {
    DEBUG,
    INFO,
    WARNING,
    ERROR
}

interface AppLogger {
    fun debug(tag: String, message: String)
    fun info(tag: String, message: String)
    fun warning(tag: String, message: String, throwable: Throwable? = null)
    fun error(tag: String, message: String, throwable: Throwable? = null)
}

private val JWT_LIKE = Pattern.compile(
    "eyJ[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+"
)
private val BEARER = Pattern.compile(
    "(?i)(bearer\\s+)[a-zA-Z0-9._\\-]+"
)
private val EMAIL = Pattern.compile(
    "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"
)
private val PHONE = Pattern.compile(
    "(?<!\\d)(?:\\+?\\d[\\d\\s\\-]{7,}\\d)"
)
private val KEY_ASSIGN = Pattern.compile(
    "(?i)(password|passwd|pwd|token|api[_-]?key|anon[_-]?key|secret|authorization)\\s*[=:]\\s*[^\\s,;]+"
)
private val COORDS = Regex("(-?\\d{1,3}\\.\\d{3,})\\s*,\\s*(-?\\d{1,3}\\.\\d{3,})")

/** Sanitización básica para logs (tests unitarios la cubren). */
fun sanitizeLogMessage(message: String): String {
    var result = message
    result = JWT_LIKE.matcher(result).replaceAll("[REDACTED_TOKEN]")
    result = BEARER.matcher(result).replaceAll("$1[REDACTED_TOKEN]")
    result = KEY_ASSIGN.matcher(result).replaceAll("$1=[REDACTED]")
    result = EMAIL.matcher(result).replaceAll("[REDACTED_EMAIL]")
    result = PHONE.matcher(result).replaceAll("[REDACTED_PHONE]")
    result = COORDS.replace(result, "[REDACTED_COORDS]")
    return result
}

/**
 * Logger basado en [Log]. No registra secretos ni datos personales.
 * En release, solo warning/error (sin verbose).
 */
object AndroidAppLogger : AppLogger {

    override fun debug(tag: String, message: String) {
        if (!shouldLog(LogLevel.DEBUG)) return
        Log.d(sanitizeTag(tag), sanitizeLogMessage(message))
    }

    override fun info(tag: String, message: String) {
        if (!shouldLog(LogLevel.INFO)) return
        Log.i(sanitizeTag(tag), sanitizeLogMessage(message))
    }

    override fun warning(tag: String, message: String, throwable: Throwable?) {
        if (!shouldLog(LogLevel.WARNING)) return
        val msg = sanitizeLogMessage(message)
        if (throwable != null) Log.w(sanitizeTag(tag), msg, throwable) else Log.w(sanitizeTag(tag), msg)
    }

    override fun error(tag: String, message: String, throwable: Throwable?) {
        if (!shouldLog(LogLevel.ERROR)) return
        val msg = sanitizeLogMessage(message)
        if (throwable != null) Log.e(sanitizeTag(tag), msg, throwable) else Log.e(sanitizeTag(tag), msg)
    }

    private fun shouldLog(level: LogLevel): Boolean {
        val config = AppConfigProvider.get()
        if (!config.logging.enabled && level < LogLevel.WARNING) return false
        if (!config.isDebug && level == LogLevel.DEBUG) return false
        if (!config.isDebug && level == LogLevel.INFO && !config.logging.verbose) return false
        return true
    }

    private fun sanitizeTag(tag: String): String =
        tag.take(23).ifBlank { "Leover" }
}

/** Acceso de conveniencia para código nuevo. */
val AppLog: AppLogger get() = AndroidAppLogger
