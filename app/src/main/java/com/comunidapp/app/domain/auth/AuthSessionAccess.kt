package com.comunidapp.app.domain.auth

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.user.UserInfo

/**
 * Session existence is independent of ActiveContext.
 *
 * Canonical auth source (supabase-kt 3.0.3, already used by login/TUS/profile):
 * `supabase.auth.currentUserOrNull()` → auth.users.id.
 *
 * A failed JWT refresh must not wipe a still-valid local session.
 */
object AuthSessionAccess {
    fun currentUser(auth: Auth): UserInfo? = auth.currentUserOrNull()

    /**
     * Use the live session first. Refresh only when there is no user.
     * A failed refresh must not replace a still-valid session with NOT_AUTHENTICATED.
     */
    suspend fun requireUser(auth: Auth): UserInfo {
        currentUser(auth)?.let { return it }
        runCatching { auth.refreshCurrentSession() }
        return currentUser(auth) ?: error("NOT_AUTHENTICATED")
    }
}
