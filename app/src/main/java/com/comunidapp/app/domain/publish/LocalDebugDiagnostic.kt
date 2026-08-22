package com.comunidapp.app.domain.publish

import com.comunidapp.app.BuildConfig
import com.comunidapp.app.domain.observability.sanitization.SensitiveDataSanitizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Diagnóstico técnico sanitizado para builds local/debug.
 * Nunca se muestra en release/production.
 */
object LocalDebugDiagnostic {

    fun isCopyEnabled(
        debug: Boolean = BuildConfig.DEBUG,
        env: String = BuildConfig.LEOVER_ENV
    ): Boolean = debug && env.equals("local", ignoreCase = true)

    fun forOperation(
        operation: String,
        error: Throwable,
        extra: Map<String, String> = emptyMap(),
        appVersion: String = BuildConfig.VERSION_NAME,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        val raw = buildString {
            append(error.message.orEmpty())
            generateSequence(error.cause) { it.cause }.forEach { cause ->
                append(' ')
                append(cause.message.orEmpty())
            }
        }
        val safeHint = SensitiveDataSanitizer.sanitize(extractBackendHint(raw), maxLength = 220)
        val code = extractErrorCode(raw)
        return buildString {
            appendLine("LEOVER_DIAGNOSTIC")
            appendLine("operation=$operation")
            extra.forEach { (k, v) ->
                if (k.equals("authorization", true) || k.equals("token", true)) return@forEach
                appendLine("$k=${SensitiveDataSanitizer.sanitize(v, maxLength = 80)}")
            }
            appendLine("errorCode=$code")
            appendLine("message=$safeHint")
            appendLine("timestamp=${isoUtc(nowMillis)}")
            append("appVersion=$appVersion")
        }
    }

    fun lostFoundCreate(
        type: String,
        error: Throwable,
        appVersion: String = BuildConfig.VERSION_NAME,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        val raw = buildString {
            append(error.message.orEmpty())
            generateSequence(error.cause) { it.cause }.forEach { cause ->
                append(' ')
                append(cause.message.orEmpty())
            }
        }
        val hint = extractBackendHint(raw)
        val safeHint = SensitiveDataSanitizer.sanitize(hint, maxLength = 220)
        val code = extractErrorCode(raw)
        val stamp = isoUtc(nowMillis)
        return buildString {
            appendLine("LEOVER_DIAGNOSTIC")
            appendLine("operation=lost_found_create")
            appendLine("type=${type.uppercase(Locale.US)}")
            appendLine("errorCode=$code")
            appendLine("message=$safeHint")
            appendLine("timestamp=$stamp")
            append("appVersion=$appVersion")
        }
    }

    internal fun extractBackendHint(raw: String): String {
        val lines = raw.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val interesting = lines.filter { line ->
            val lower = line.lowercase(Locale.US)
            if (lower.contains("authorization") || lower.contains("bearer") || lower.contains("apikey")) {
                return@filter false
            }
            if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("url:")) {
                return@filter false
            }
            lower.contains("gen_random_bytes") ||
                lower.contains("does not exist") ||
                lower.contains("sqlstate") ||
                lower.contains("42883") ||
                lower.contains("pgrst") ||
                lower.contains("function")
        }
        val safeFallback = lines.filter { line ->
            val lower = line.lowercase(Locale.US)
            !lower.contains("authorization") &&
                !lower.contains("bearer") &&
                !lower.contains("apikey") &&
                !lower.contains("cookie")
        }
        val picked = interesting.ifEmpty { safeFallback.take(1) }
        return picked.joinToString(" | ").take(240)
    }

    internal fun extractErrorCode(raw: String): String {
        val match = Regex("""\b(42883|PGRST\d{3}|\d{5})\b""", RegexOption.IGNORE_CASE)
            .find(raw)
        return match?.value ?: "UNKNOWN"
    }

    private fun isoUtc(nowMillis: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date(nowMillis))
    }
}
