package com.comunidapp.shared.session

/**
 * Sesión multiplataforma — sin tokens ni secretos.
 */
data class SessionUser(
    val userId: String,
    val email: String? = null,
    val displayName: String? = null
) {
    init {
        require(userId.isNotBlank()) { "SESSION_USER_ID_BLANK" }
    }
}

sealed class SessionState {
    data object Unknown : SessionState()
    data object Unauthenticated : SessionState()
    data class Authenticated(val user: SessionUser) : SessionState()
    data object Expired : SessionState()
    data class Error(val message: String) : SessionState()
}

enum class SessionDataMode {
    /** Auth productivo Android (u otro adapter real). */
    REAL_REMOTE,
    /** Sesión determinista de desarrollo / iOS gate. */
    SESSION_STUB
}
