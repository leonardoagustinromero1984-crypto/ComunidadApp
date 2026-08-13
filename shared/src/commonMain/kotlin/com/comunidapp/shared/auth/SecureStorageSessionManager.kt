package com.comunidapp.shared.auth

import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.json.Json

/**
 * SessionManager supabase-kt respaldado por [SecureSessionStorage] (Keychain en iOS).
 * internal: no exportar SessionManager/UserSession al framework ObjC.
 */
internal class SecureStorageSessionManager(
    private val storage: SecureSessionStorage,
    private val storageKey: String = SESSION_KEY
) : SessionManager {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    override suspend fun saveSession(session: UserSession) {
        val encoded = json.encodeToString(UserSession.serializer(), session)
        storage.write(storageKey, encoded)
    }

    override suspend fun loadSession(): UserSession? {
        val raw = storage.read(storageKey) ?: return null
        return runCatching { json.decodeFromString(UserSession.serializer(), raw) }.getOrNull()
    }

    override suspend fun deleteSession() {
        storage.remove(storageKey)
    }

    companion object {
        const val SESSION_KEY = "leover.supabase.user_session"
    }
}
