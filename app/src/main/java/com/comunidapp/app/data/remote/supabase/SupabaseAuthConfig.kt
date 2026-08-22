package com.comunidapp.app.data.remote.supabase

import com.comunidapp.app.BuildConfig

/**
 * Unique non-null Android Auth callback for LeoVer.
 * Must match AndroidManifest, Auth plugin scheme/host, signup redirectUrl,
 * and the Staging Redirect URL allowlist.
 */
object SupabaseAuthConfig {
    const val SCHEME = "com.comunidapp.app"
    const val HOST = "login-callback"
    const val REDIRECT_URL = "$SCHEME://$HOST"

    /**
     * Android returns via custom scheme, not the public Auth host.
     * Google's consent screen shows the Supabase Auth origin (Custom Domain when active).
     */
    const val STAGING_AUTH_DOMAIN = com.comunidapp.app.domain.auth.LeoVerAuthDomains.STAGING_AUTH_DOMAIN
    const val PROD_AUTH_DOMAIN = com.comunidapp.app.domain.auth.LeoVerAuthDomains.PROD_AUTH_DOMAIN
    const val STAGING_CUSTOM_AUTH_DOMAIN_ACTIVE = false
    const val STAGING_SUPABASE_AUTH_DOMAIN_ALLOWED = true
    const val PROD_AUTH_BRANDING_PENDING = true

    fun requireRedirectUrl(): String {
        val url = REDIRECT_URL
        if (isForbiddenRedirect(url)) {
            val message = "AUTH_REDIRECT_FORBIDDEN"
            if (BuildConfig.DEBUG) {
                error("$message: canonical Android auth redirect is missing or null-like")
            }
            throw IllegalStateException(message)
        }
        return url
    }

    fun isForbiddenRedirect(value: String?): Boolean {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isEmpty()) return true
        val lower = trimmed.lowercase()
        if (lower == "null" || lower == "anull" || lower == "undefined") return true
        if (lower.contains("://null") || lower.contains("://anull")) return true
        if (!trimmed.contains("://")) return true
        val scheme = trimmed.substringBefore("://")
        val host = trimmed.substringAfter("://").substringBefore('/').substringBefore('?')
        if (scheme.isBlank() || host.isBlank()) return true
        if (scheme.equals("null", ignoreCase = true) || host.equals("null", ignoreCase = true)) {
            return true
        }
        return false
    }
}
