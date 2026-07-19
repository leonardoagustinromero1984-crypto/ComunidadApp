package com.comunidapp.app.domain.auth

/**
 * Proyección segura de identidad para M01.
 * Sin roles de negocio ni perfil social (eso es M02).
 */
data class AuthUser(
    val id: String,
    val email: String? = null,
    val emailVerified: Boolean = false,
    /** Instant epoch millis si está disponible; opcional. */
    val sessionStartedAtEpochMs: Long? = null
)
