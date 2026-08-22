package com.comunidapp.app.domain.auth

import com.comunidapp.app.core.config.SupabaseUrlPolicy

/**
 * Official LeoVer public domain (already acquired). Do not invent hosts.
 *
 * Custom Auth DNS is not live yet:
 * - auth-staging.leover.com.ar → NXDOMAIN
 * - auth.leover.com.ar → NXDOMAIN
 *
 * Until Cloudflare CNAME + Supabase Custom Domain are verified, the app keeps
 * API/database/storage on the environment SUPABASE_URL and does not switch Auth.
 */
object LeoVerOfficialDomain {
    const val APEX = "leover.com.ar"
    const val STAGING_AUTH_HOST = "auth-staging.leover.com.ar"
    const val PROD_AUTH_HOST = "auth.leover.com.ar"
}

object LeoVerAuthDomains {
    const val STAGING_AUTH_DOMAIN = LeoVerOfficialDomain.STAGING_AUTH_HOST
    const val PROD_AUTH_DOMAIN = LeoVerOfficialDomain.PROD_AUTH_HOST

    const val GOOGLE_AUTH_APP_NAME = "LeoVer"
    const val GOOGLE_AUTH_LOGO_ASSET = "app/src/main/res/drawable-nodpi/leover_logo_official.png"

    fun httpsOrigin(hostOrUrl: String): String {
        val trimmed = hostOrUrl.trim()
        if (trimmed.startsWith("https://", ignoreCase = true)) return trimmed.trimEnd('/')
        return "https://${trimmed.trimStart('/').trimEnd('/')}"
    }

    fun hostOf(hostOrUrl: String): String =
        SupabaseUrlPolicy.hostOf(httpsOrigin(hostOrUrl)).orEmpty()

    fun googleOAuthCallbackUri(authHostOrUrl: String): String =
        "${httpsOrigin(authHostOrUrl)}/auth/v1/callback"

    fun stagingGoogleRedirectCustomDomain(): String =
        googleOAuthCallbackUri(STAGING_AUTH_DOMAIN)

    /**
     * Transitional Google Cloud redirect while Custom Domain DNS is missing.
     * Pass the environment API URL — do not hardcode a project-ref in Auth code.
     */
    fun googleRedirectForApiUrl(apiUrl: String): String =
        googleOAuthCallbackUri(apiUrl)

    /**
     * API URL stays on the environment endpoint until Custom Domain is opted-in.
     * When active, Auth/OAuth (and the client host Google shows) uses the custom origin.
     * Do not activate while DNS is missing — OAuth would fail.
     */
    fun supabaseUrlForClient(
        apiUrl: String,
        customAuthUrl: String?,
        customActive: Boolean
    ): String {
        val api = apiUrl.trim().trimEnd('/')
        if (!customActive) return api
        val custom = customAuthUrl?.trim().orEmpty().let { raw ->
            if (raw.isBlank()) "" else httpsOrigin(raw)
        }
        if (!SupabaseUrlPolicy.isUsableRemoteUrl(custom)) return api
        return custom.trimEnd('/')
    }

    fun isTechnicalSupabaseHost(hostOrUrl: String?): Boolean {
        val host = hostOrUrl?.let { hostOf(it) }.orEmpty().lowercase()
        return host.endsWith(".supabase.co")
    }

    fun isOfficialStagingAuthHost(hostOrUrl: String?): Boolean {
        if (hostOrUrl.isNullOrBlank()) return false
        return hostOf(hostOrUrl).equals(STAGING_AUTH_DOMAIN, ignoreCase = true)
    }
}
