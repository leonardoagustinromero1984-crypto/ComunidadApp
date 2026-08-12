package com.comunidapp.shared.poc.m22.data

/**
 * Injected configuration — never hardcode secrets.
 * Android host passes BuildConfig; iOS host passes Info.plist / env.
 */
data class PocSupabaseConfig(
    val url: String,
    val anonKey: String
) {
    val isUsable: Boolean
        get() = url.startsWith("https://", ignoreCase = true) &&
            anonKey.isNotBlank() &&
            !anonKey.contains("service_role", ignoreCase = true)
}
