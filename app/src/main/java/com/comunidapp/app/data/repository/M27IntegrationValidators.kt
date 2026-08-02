package com.comunidapp.app.data.repository

object M27IntegrationValidators {
    private val urlPattern = Regex("^https://[\\w.-]+(/[\\w./?#&=-]*)?$", RegexOption.IGNORE_CASE)
    private val scopePattern = Regex("^[a-z][a-z0-9_]{1,31}$")

    fun validateWebhook(label: String, targetUrl: String): String? = when {
        label.trim().length !in 3..80 || unsafe(label) -> "M27_INVALID_WEBHOOK"
        !urlPattern.matches(targetUrl.trim()) -> "M27_INVALID_WEBHOOK"
        else -> null
    }

    fun validateOAuthApp(name: String, redirectUri: String, scopes: List<String>): String? = when {
        name.trim().length !in 3..80 || unsafe(name) -> "M27_INVALID_OAUTH"
        !urlPattern.matches(redirectUri.trim()) -> "M27_INVALID_OAUTH"
        scopes.isEmpty() || scopes.any { !scopePattern.matches(it) } -> "M27_INVALID_OAUTH"
        else -> null
    }

    fun validateApiKey(label: String, scopes: List<String>): String? = when {
        label.trim().length !in 3..80 || unsafe(label) -> "M27_INVALID_API_KEY"
        scopes.isEmpty() || scopes.any { !scopePattern.matches(it) } -> "M27_INVALID_API_KEY"
        else -> null
    }

    private fun unsafe(value: String): Boolean =
        Regex("(?i)<script|javascript:|on\\w+\\s*=|<iframe").containsMatchIn(value)
}
