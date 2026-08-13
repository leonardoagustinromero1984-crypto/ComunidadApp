package com.comunidapp.shared.platform

/**
 * Preferencias no sensibles (onboarding intent, flags UI).
 * No usar para tokens — Keychain pendiente.
 */
interface PlatformPreferences {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
}

class InMemoryPlatformPreferences : PlatformPreferences {
    private val map = linkedMapOf<String, String>()
    override fun getString(key: String): String? = map[key]
    override fun putString(key: String, value: String) {
        map[key] = value
    }
    override fun remove(key: String) {
        map.remove(key)
    }
}
