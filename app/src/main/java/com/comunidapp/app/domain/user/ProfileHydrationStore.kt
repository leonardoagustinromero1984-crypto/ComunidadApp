package com.comunidapp.app.domain.user

import android.content.Context
import com.comunidapp.app.LeoverApplication

/**
 * Last known complete PERSON profile. Prevents flashing Completar perfil
 * while session/PERSON hydrate after background or process death.
 */
object ProfileHydrationStore {
    private const val PREFS = "leover_profile_hydration"
    private const val KEY_USER = "ready_user_id"

    fun markReady(userId: String) {
        if (userId.isBlank()) return
        prefs()?.edit()?.putString(KEY_USER, userId)?.apply()
    }

    fun isReady(userId: String): Boolean {
        if (userId.isBlank()) return false
        return prefs()?.getString(KEY_USER, null) == userId
    }

    fun clear() {
        prefs()?.edit()?.clear()?.apply()
    }

    private fun prefs() = runCatching {
        LeoverApplication.instance.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }.getOrNull()
}
