package com.comunidapp.app.domain.auth

import com.comunidapp.app.core.result.AppError

/**
 * Estados oficiales de autenticación (M01).
 * No contiene contraseñas ni tokens.
 */
sealed interface AuthState {
    /** Restauración inicial de sesión. */
    data object Initializing : AuthState

    data object Unauthenticated : AuthState

    data object Registering : AuthState

    data class EmailVerificationRequired(
        val emailHint: String? = null
    ) : AuthState

    data object Authenticating : AuthState

    data class Authenticated(
        val user: AuthUser
    ) : AuthState

    /**
     * Sesión válida sin consentimiento vigente de versiones legales actuales.
     * Bloquea el flujo principal hasta aceptar (RPC con auth.uid()).
     */
    data class LegalConsentRequired(
        val user: AuthUser
    ) : AuthState

    data object PasswordRecoveryRequested : AuthState

    /** Sesión de recovery establecida vía deep link; lista para nueva contraseña. */
    data object PasswordResetActive : AuthState

    data object SigningOut : AuthState

    data object AccountDeletionPending : AuthState

    data class ConfigurationError(
        val error: AppError
    ) : AuthState

    data class AuthError(
        val error: AppError,
        val previous: AuthState? = null
    ) : AuthState

    /** Impide envíos duplicados mientras hay trabajo en curso. */
    val isTransient: Boolean
        get() = when (this) {
            Initializing,
            Registering,
            Authenticating,
            SigningOut,
            PasswordRecoveryRequested,
            PasswordResetActive,
            AccountDeletionPending -> true
            else -> false
        }
}
