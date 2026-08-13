package com.comunidapp.shared.auth

/**
 * Almacenamiento de secretos de sesión (tokens).
 * No usar [com.comunidapp.shared.platform.PlatformPreferences].
 */
interface SecureSessionStorage {
    fun read(key: String): String?
    fun write(key: String, value: String)
    fun remove(key: String)
}

/**
 * Factory de plataforma — no exportar a ObjC (Swift no la llama).
 */
internal expect fun createSecureSessionStorage(): SecureSessionStorage

/** Solo tests / memoria — no persistente. */
class InMemorySecureSessionStorage : SecureSessionStorage {
    private val map = linkedMapOf<String, String>()
    override fun read(key: String): String? = map[key]
    override fun write(key: String, value: String) {
        map[key] = value
    }
    override fun remove(key: String) {
        map.remove(key)
    }
}
