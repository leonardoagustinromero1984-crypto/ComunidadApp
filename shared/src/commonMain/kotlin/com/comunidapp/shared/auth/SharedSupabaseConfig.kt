package com.comunidapp.shared.auth

/**
 * Configuración pública (URL + anon/publishable).
 * Nunca service_role. Inyectada por el host — no hardcodear en git.
 * Dev/QA: https://tobqbddfcyitwgbkthhy.supabase.co
 */
data class SharedSupabaseConfig(
    val url: String,
    val anonKey: String
) {
    val isUsable: Boolean
        get() = url.trim().startsWith("https://", ignoreCase = true) &&
            anonKey.isNotBlank() &&
            !anonKey.contains("service_role", ignoreCase = true)
}

fun SharedSupabaseConfig?.usableOrNull(): SharedSupabaseConfig? =
    this?.takeIf { it.isUsable }
