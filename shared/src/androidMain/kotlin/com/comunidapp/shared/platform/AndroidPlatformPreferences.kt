package com.comunidapp.shared.platform

import android.content.Context

/**
 * Preferencias no sensibles vía SharedPreferences.
 * Android productivo de onboarding sigue en DataStore; este adapter es para shared vertical.
 */
class AndroidPlatformPreferences(
    context: Context,
    name: String = "leover_shared_prefs"
) : PlatformPreferences {
    private val prefs = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}
