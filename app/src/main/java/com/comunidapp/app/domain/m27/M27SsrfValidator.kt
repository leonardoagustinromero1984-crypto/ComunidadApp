package com.comunidapp.app.domain.m27

import java.net.URI

object M27SsrfValidator {
    private val allowedSchemes = setOf("https")
    private val blockedHostPatterns = listOf(
        Regex("^localhost$", RegexOption.IGNORE_CASE),
        Regex("^127\\.", RegexOption.IGNORE_CASE),
        Regex("^0\\.0\\.0\\.0$"),
        Regex("^10\\.", RegexOption.IGNORE_CASE),
        Regex("^172\\.(1[6-9]|2\\d|3[01])\\.", RegexOption.IGNORE_CASE),
        Regex("^192\\.168\\.", RegexOption.IGNORE_CASE),
        Regex("^169\\.254\\.", RegexOption.IGNORE_CASE),
        Regex("^\\[::1\\]$"),
        Regex("^\\[fc", RegexOption.IGNORE_CASE),
        Regex("^\\[fd", RegexOption.IGNORE_CASE),
        Regex("^\\[fe80:", RegexOption.IGNORE_CASE)
    )

    fun validateTargetUrl(raw: String, allowFakeLocal: Boolean = false): String? {
        val trimmed = raw.trim()
        if (trimmed.contains("@")) return "M27_UNSAFE_WEBHOOK_URL"
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return "M27_UNSAFE_WEBHOOK_URL"
        val scheme = uri.scheme?.lowercase()
        if (scheme !in allowedSchemes && !(allowFakeLocal && scheme == "https")) return "M27_UNSAFE_WEBHOOK_URL"
        if (scheme in setOf("file", "ftp", "content", "javascript")) return "M27_UNSAFE_WEBHOOK_URL"
        val host = uri.host?.lowercase() ?: return "M27_UNSAFE_WEBHOOK_URL"
        if (uri.userInfo != null) return "M27_UNSAFE_WEBHOOK_URL"
        if (blockedHostPatterns.any { it.containsMatchIn(host) }) return "M27_UNSAFE_WEBHOOK_URL"
        val port = uri.port
        if (port != -1 && port !in setOf(443, 8443)) return "M27_UNSAFE_WEBHOOK_URL"
        if (!trimmed.startsWith("https://")) return "M27_UNSAFE_WEBHOOK_URL"
        return null
    }
}
