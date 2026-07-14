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

    data object PasswordRecoveryRequested : AuthState

    data object PasswordResetActive : AuthState

    data object SigningOut : AuthState

    /** Reservado para Etapa 4; no se usa todavía en flujo activo. */
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
