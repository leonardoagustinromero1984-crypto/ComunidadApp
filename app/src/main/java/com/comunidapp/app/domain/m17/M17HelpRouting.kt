package com.comunidapp.app.domain.m17

/**
 * Canonical Staging never shares the legacy m17_* remote.
 * LOCAL is the unit-test mock. LEGACY_M17 is only a non-canonical Supabase project.
 */
enum class M17HelpBackend {
    CANONICAL,
    LEGACY_M17,
    LOCAL
}

object M17HelpRouting {
    fun select(useSupabase: Boolean, legacyRemoteModules: Boolean): M17HelpBackend = when {
        useSupabase && !legacyRemoteModules -> M17HelpBackend.CANONICAL
        legacyRemoteModules -> M17HelpBackend.LEGACY_M17
        else -> M17HelpBackend.LOCAL
    }
}
