package com.comunidapp.shared.session

/**
 * Bridge puro Android → shared (sin tokens).
 * El AuthRepository productivo permanece en :app.
 */
object AndroidSessionMapper {
    fun toSessionUser(
        userId: String,
        email: String?,
        displayName: String? = null
    ): SessionUser = SessionUser(
        userId = userId,
        email = email,
        displayName = displayName
    )

    fun authenticated(
        userId: String,
        email: String?,
        displayName: String? = null
    ): SessionState = SessionState.Authenticated(
        toSessionUser(userId, email, displayName)
    )
}
