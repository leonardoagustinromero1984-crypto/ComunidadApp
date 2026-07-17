package com.comunidapp.app.core.logging

import android.util.Log
import com.comunidapp.app.core.config.AppConfigProvider
import com.comunidapp.app.domain.observability.correlation.CorrelationId
import com.comunidapp.app.domain.observability.sanitization.SensitiveDataSanitizer
import com.comunidapp.app.domain.observability.sanitization.StructuredLogSanitizer
import com.comunidapp.app.domain.observability.sanitization.ThrowableSanitizer
import java.util.regex.Pattern

enum class LogLevel {
    DEBUG,
    INFO,
    WARNING,
    ERROR
}

interface AppLogger {
    fun debug(tag: String, message: String, correlationId: CorrelationId? = null)
    fun info(tag: String, message: String, correlationId: CorrelationId? = null)
    fun warning(tag: String, message: String, throwable: Throwable? = null, correlationId: CorrelationId? = null)
    fun error(tag: String, message: String, throwable: Throwable? = null, correlationId: CorrelationId? = null)
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

/**
 * Sanitización básica para logs (tests unitarios la cubren).
 * Delega en [SensitiveDataSanitizer] y conserva patrones históricos para regresión.
 */
fun sanitizeLogMessage(message: String): String {
    var result = SensitiveDataSanitizer.sanitize(message, maxLength = 4000)
    // Historical regex pass for AppLoggerSanitizeTest compatibility on edge cases
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
 * Throwable se sanitiza: no se envía el objeto raw a Log cuando hay riesgo de fuga;
 * se anexa fingerprint/clase segura al mensaje.
 * Remoto: solo errores allowlisted (no DEBUG), vía ObservabilityInstrumentation sin loops.
 * No crash handler; no breadcrumbs; no proveedores externos.
 */
object AndroidAppLogger : AppLogger {

    override fun debug(tag: String, message: String, correlationId: CorrelationId?) {
        if (!shouldLog(LogLevel.DEBUG)) return
        Log.d(sanitizeTag(tag), formatMessage(message, correlationId))
    }

    override fun info(tag: String, message: String, correlationId: CorrelationId?) {
        if (!shouldLog(LogLevel.INFO)) return
        Log.i(sanitizeTag(tag), formatMessage(message, correlationId))
    }

    override fun warning(
        tag: String,
        message: String,
        throwable: Throwable?,
        correlationId: CorrelationId?
    ) {
        if (!shouldLog(LogLevel.WARNING)) return
        val msg = formatMessage(message, correlationId, throwable)
        Log.w(sanitizeTag(tag), msg)
        // Consent gate unavailable is logged from AuthRepository without modifying it:
        // observe the safe message pattern and report allowlisted remote error.
        if (message.contains("skipping gate until migration", ignoreCase = true)) {
            maybeReportRemote("M01_CONSENT_GATE_UNAVAILABLE", "M01", msg, correlationId)
        }
    }

    override fun error(
        tag: String,
        message: String,
        throwable: Throwable?,
        correlationId: CorrelationId?
    ) {
        if (!shouldLog(LogLevel.ERROR)) return
        val msg = formatMessage(message, correlationId, throwable)
        Log.e(sanitizeTag(tag), msg)
        // DEBUG never remotes; ERROR only for allowlisted codes embedded as OBS_* / M0x_*
        val code = extractAllowlistedCode(message) ?: return
        maybeReportRemote(code, "M07", msg, correlationId)
    }

    private fun extractAllowlistedCode(message: String): String? {
        val allow = setOf(
            "OBS_UNKNOWN", "OBS_WRITE_FAILED", "OBS_CORRELATION_INVALID",
            "OBS_METADATA_DENIED", "OBS_EVENT_UNKNOWN", "OBS_PERMISSION_DENIED",
            "M01_CONSENT_GATE_UNAVAILABLE", "M05_STORAGE_ERROR",
            "M05_SIGNED_URL_ERROR", "M06_DEEP_LINK_DENIED"
        )
        return allow.firstOrNull { message.contains(it) }
    }

    private fun maybeReportRemote(
        code: String,
        module: String,
        sanitizedMessage: String,
        correlationId: CorrelationId?
    ) {
        // Never from DEBUG path; never recurse into reporter failures.
        runCatching {
            com.comunidapp.app.domain.observability.ObservabilityInstrumentation
                .reportAllowlistedRemoteError(code, module, sanitizedMessage, correlationId)
        }
    }

    private fun formatMessage(
        message: String,
        correlationId: CorrelationId?,
        throwable: Throwable? = null
    ): String {
        val (safeMsg, sanitized) = StructuredLogSanitizer.sanitizeForLog(message, throwable)
        val withCorr = if (correlationId != null) {
            "cid=${correlationId.value} $safeMsg"
        } else {
            safeMsg
        }
        return if (sanitized != null) {
            sanitizeLogMessage(
                "$withCorr | err=${sanitized.errorClass} fp=${sanitized.fingerprint} msg=${sanitized.safeMessage}"
            )
        } else {
            sanitizeLogMessage(withCorr)
        }
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

/** Expuesto para tests: sanitiza Throwable sin stack raw. */
fun sanitizeThrowableForLog(throwable: Throwable) = ThrowableSanitizer.sanitize(throwable)
