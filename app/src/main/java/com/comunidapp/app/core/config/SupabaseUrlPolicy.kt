package com.comunidapp.app.core.config

import com.comunidapp.app.BuildConfig
import java.net.URI

/**
 * Validación segura de URL Supabase para LeoVer (sin exponer keys).
 * Rechaza localhost / emulador / cleartext — no sirven en APK físico.
 */
object SupabaseUrlPolicy {

    fun isForbiddenHost(url: String?): Boolean {
        if (url.isNullOrBlank()) return true
        val lower = url.trim().lowercase()
        return lower.contains("localhost") ||
            lower.contains("127.0.0.1") ||
            lower.contains("10.0.2.2") ||
            lower.startsWith("http://")
    }

    fun isUsableRemoteUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val trimmed = url.trim()
        if (!trimmed.startsWith("https://", ignoreCase = true)) return false
        if (isForbiddenHost(trimmed)) return false
        return runCatching {
            val host = URI(trimmed).host.orEmpty()
            host.contains('.') && host.isNotBlank()
        }.getOrDefault(false)
    }

    fun hostOf(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return runCatching { URI(url.trim()).host }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    fun credentialsPresent(
        url: String? = BuildConfig.SUPABASE_URL,
        anonKey: String? = BuildConfig.SUPABASE_ANON_KEY,
        enabledFlag: Boolean = BuildConfig.SUPABASE_ENABLED
    ): Boolean {
        val keyOk = !anonKey.isNullOrBlank()
        return enabledFlag && isUsableRemoteUrl(url) && keyOk
    }
}
