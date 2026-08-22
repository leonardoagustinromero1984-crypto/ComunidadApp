package com.comunidapp.app.domain.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Clasificación y consumo único de deep links de Auth (M01 Etapa 4).
 * No navega; solo interpreta y marca el URI como consumido.
 * API basada en String para poder testear en JVM sin Android.
 */
enum class AuthDeepLinkKind {
    EmailConfirmation,
    PasswordRecovery,
    SessionCallback,
    LinkError,
    Unknown
}

data class AuthLinkNotice(
    val kind: AuthDeepLinkKind,
    val userMessage: String? = null
)

/**
 * Entrega cold-start y onNewIntent al SessionViewModel / pantallas de verificación.
 * No transporta tokens.
 */
object AuthLinkNoticeStore {
    private val _notice = MutableStateFlow<AuthLinkNotice?>(null)
    val notice: StateFlow<AuthLinkNotice?> = _notice.asStateFlow()

    fun publish(kind: AuthDeepLinkKind, userMessage: String? = null) {
        _notice.value = AuthLinkNotice(kind = kind, userMessage = userMessage)
    }

    fun consume(): AuthLinkNotice? {
        val current = _notice.value
        _notice.value = null
        return current
    }

    fun resetForTests() {
        _notice.value = null
    }
}

object AuthDeepLinkParser {

    private val consumedUris = mutableSetOf<String>()

    /** Solo tests. */
    fun resetConsumedForTests() {
        consumedUris.clear()
        AuthLinkNoticeStore.resetForTests()
    }

    fun classify(uriString: String?): AuthDeepLinkKind? {
        if (uriString.isNullOrBlank()) return null
        if (isForbiddenCallback(uriString)) return AuthDeepLinkKind.LinkError
        extractError(uriString)?.let { return AuthDeepLinkKind.LinkError }
        val type = extractType(uriString)?.lowercase()
        return when (type) {
            "recovery" -> AuthDeepLinkKind.PasswordRecovery
            "signup", "email", "magiclink", "invite", "email_change" ->
                AuthDeepLinkKind.EmailConfirmation
            else -> {
                if (hasAuthPayload(uriString)) AuthDeepLinkKind.SessionCallback
                else AuthDeepLinkKind.Unknown
            }
        }
    }

    fun userMessageFor(uriString: String?): String? {
        if (uriString.isNullOrBlank()) return null
        val error = extractError(uriString) ?: return null
        return messageForAuthCallbackError(error.code, error.description)
    }

    /**
     * Devuelve el kind solo la primera vez para ese URI exacto.
     * Llamadas posteriores con el mismo URI → null (consumido).
     */
    fun consumeOnce(uriString: String?): AuthDeepLinkKind? {
        if (uriString.isNullOrBlank()) return null
        if (!consumedUris.add(uriString)) return null
        return classify(uriString) ?: AuthDeepLinkKind.Unknown
    }

    fun extractType(uriString: String): String? =
        paramsOf(uriString)["type"]?.takeIf { it.isNotBlank() }

    fun extractError(uriString: String): AuthCallbackError? {
        val params = paramsOf(uriString)
        val code = params["error_code"] ?: params["error"]
        val description = params["error_description"]?.replace('+', ' ')
        if (code.isNullOrBlank() && description.isNullOrBlank()) return null
        return AuthCallbackError(
            code = code.orEmpty(),
            description = description.orEmpty()
        )
    }

    fun isForbiddenCallback(uriString: String): Boolean {
        val lower = uriString.trim().lowercase()
        if (lower.isEmpty()) return true
        if (lower == "null" || lower == "anull" || lower == "undefined") return true
        val scheme = lower.substringBefore("://", missingDelimiterValue = "")
        val rest = lower.substringAfter("://", missingDelimiterValue = "")
        val host = rest.substringBefore('/').substringBefore('?').substringBefore('#')
        if (scheme == "null" || host == "null" || host == "anull") return true
        return false
    }

    fun messageForAuthCallbackError(code: String, description: String): String {
        val haystack = "$code $description".lowercase()
        return if (
            haystack.contains("otp_expired") ||
            haystack.contains("expired") ||
            haystack.contains("already") ||
            haystack.contains("used")
        ) {
            "El enlace venció o ya fue utilizado."
        } else if (
            haystack.contains("access_denied") ||
            haystack.contains("user_cancelled") ||
            haystack.contains("canceled") ||
            haystack.contains("cancelled")
        ) {
            "Se canceló el inicio de sesión."
        } else if (
            haystack.contains("invalid") ||
            haystack.contains("malformed")
        ) {
            "El enlace venció o ya fue utilizado."
        } else {
            "No pudimos completar el inicio de sesión. Intentá de nuevo."
        }
    }

    private fun hasAuthPayload(uriString: String): Boolean {
        val params = paramsOf(uriString)
        return !params["code"].isNullOrBlank() || !params["access_token"].isNullOrBlank()
    }

    private fun paramsOf(uriString: String): Map<String, String> {
        val query = uriString.substringAfter('?', missingDelimiterValue = "")
            .substringBefore('#')
        val fragment = uriString.substringAfter('#', missingDelimiterValue = "")
        return parsePairs(query) + parsePairs(fragment)
    }

    private fun parsePairs(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return raw.split('&').mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq <= 0) null
            else part.substring(0, eq).lowercase() to part.substring(eq + 1)
        }.toMap()
    }
}

data class AuthCallbackError(
    val code: String,
    val description: String
)
