package com.comunidapp.shared.auth

import android.content.Context
import android.content.SharedPreferences

/**
 * Adapter Android para el módulo shared.
 * El Auth productivo de `:app` no se modifica (sigue en SupabaseAuthRepository).
 */
actual fun createSecureSessionStorage(): SecureSessionStorage {
    val context = AndroidSecureSessionStorage.appContext
    return if (context != null) {
        AndroidSecureSessionStorage(context)
    } else {
        InMemorySecureSessionStorage()
    }
}

class AndroidSecureSessionStorage(
    private val prefs: SharedPreferences
) : SecureSessionStorage {

    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    )

    override fun read(key: String): String? = prefs.getString(key, null)

    override fun write(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    companion object {
        private const val PREFS = "leover_secure_session"

        @Volatile
        var appContext: Context? = null
            private set

        fun install(context: Context) {
            appContext = context.applicationContext
        }
    }
}
