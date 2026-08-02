package com.comunidapp.app.data.repository

import com.comunidapp.app.domain.m27.M27ScopePolicy
import com.comunidapp.app.domain.m27.M27SsrfValidator

object M27IntegrationValidators {
    private val urlPattern = Regex("^https://[\\w.-]+(/[\\w./?#&=-]*)?$", RegexOption.IGNORE_CASE)

    fun validateWebhook(label: String, targetUrl: String): String? = when {
        label.trim().length !in 3..80 || unsafe(label) -> "M27_INVALID_WEBHOOK"
        M27SsrfValidator.validateTargetUrl(targetUrl.trim()) != null -> "M27_UNSAFE_WEBHOOK_URL"
        !urlPattern.matches(targetUrl.trim()) -> "M27_INVALID_WEBHOOK"
        else -> null
    }

    fun validateOAuthApp(name: String, redirectUri: String, scopes: List<String>): String? = when {
        name.trim().length !in 3..80 || unsafe(name) -> "M27_INVALID_OAUTH"
        M27SsrfValidator.validateTargetUrl(redirectUri.trim()) != null -> "M27_UNSAFE_WEBHOOK_URL"
        !urlPattern.matches(redirectUri.trim()) -> "M27_INVALID_OAUTH"
        M27ScopePolicy.validateGrantList(scopes) != null -> "M27_INVALID_OAUTH"
        else -> null
    }

    fun validateApiKey(label: String, scopes: List<String>): String? = when {
        label.trim().length !in 3..80 || unsafe(label) -> "M27_INVALID_API_KEY"
        M27ScopePolicy.validateGrantList(scopes) != null -> "M27_INVALID_API_KEY"
        else -> null
    }

    private fun unsafe(value: String): Boolean =
        Regex("(?i)<script|javascript:|on\\w+\\s*=|<iframe").containsMatchIn(value)
}
