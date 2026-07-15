package com.comunidapp.app.domain.auth

/**
 * Clasificación y consumo único de deep links de Auth (M01 Etapa 4).
 * No navega; solo interpreta y marca el URI como consumido.
 * API basada en String para poder testear en JVM sin Android.
 */
enum class AuthDeepLinkKind {
    EmailConfirmation,
    PasswordRecovery,
    Unknown
}

object AuthDeepLinkParser {

    private val consumedUris = mutableSetOf<String>()

    /** Solo tests. */
    fun resetConsumedForTests() {
        consumedUris.clear()
    }

    fun classify(uriString: String?): AuthDeepLinkKind? {
        if (uriString.isNullOrBlank()) return null
        val type = extractType(uriString)?.lowercase() ?: return null
        return when (type) {
            "recovery" -> AuthDeepLinkKind.PasswordRecovery
            "signup", "email", "magiclink", "invite" -> AuthDeepLinkKind.EmailConfirmation
            else -> AuthDeepLinkKind.Unknown
        }
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

    fun extractType(uriString: String): String? {
        val queryType = queryParam(uriString.substringBefore('#'), "type")
        if (!queryType.isNullOrBlank()) return queryType
        val fragment = uriString.substringAfter('#', missingDelimiterValue = "")
        if (fragment.isEmpty()) return null
        return fragment
            .split('&')
            .mapNotNull { part ->
                val eq = part.indexOf('=')
                if (eq <= 0) null
                else part.substring(0, eq) to part.substring(eq + 1)
            }
            .firstOrNull { it.first.equals("type", ignoreCase = true) }
            ?.second
            ?.takeIf { it.isNotBlank() }
    }

    private fun queryParam(base: String, name: String): String? {
        val query = base.substringAfter('?', missingDelimiterValue = "")
        if (query.isEmpty()) return null
        return query
            .split('&')
            .mapNotNull { part ->
                val eq = part.indexOf('=')
                if (eq <= 0) null
                else part.substring(0, eq) to part.substring(eq + 1)
            }
            .firstOrNull { it.first.equals(name, ignoreCase = true) }
            ?.second
    }
}
