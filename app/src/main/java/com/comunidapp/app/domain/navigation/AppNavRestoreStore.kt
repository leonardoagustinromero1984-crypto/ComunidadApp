package com.comunidapp.app.domain.navigation

import android.content.Context
import com.comunidapp.app.LeoverApplication
import com.comunidapp.app.navigation.NavRoutes

/**
 * Last in-app route across Activity recreation, lock screen, and process death.
 * Login is never restored; session validation happens before apply.
 */
object AppNavRestoreStore {
    private const val PREFS = "leover_nav_restore"
    private const val KEY_ROUTE = "last_route"
    private const val KEY_LOGGED_IN = "logged_in"

    private val blockedPrefixes = listOf(
        NavRoutes.LOGIN,
        NavRoutes.REGISTER,
        NavRoutes.FORGOT_PASSWORD,
        "email_verification"
    )

    fun write(route: String?, loggedIn: Boolean) {
        val value = route?.trim().orEmpty()
        if (value.isEmpty() || !loggedIn) return
        if (blockedPrefixes.any { value == it || value.startsWith("$it/") || value.startsWith("$it?") }) {
            return
        }
        prefs()?.edit()
            ?.putString(KEY_ROUTE, value)
            ?.putBoolean(KEY_LOGGED_IN, true)
            ?.apply()
    }

    fun read(): String? {
        val stored = prefs() ?: return null
        if (!stored.getBoolean(KEY_LOGGED_IN, false)) return null
        return stored.getString(KEY_ROUTE, null)?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun clear() {
        prefs()?.edit()?.clear()?.apply()
    }

    private fun prefs() = runCatching {
        LeoverApplication.instance.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }.getOrNull()
}
