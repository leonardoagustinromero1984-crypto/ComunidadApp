package com.comunidapp.shared.auth

/**
 * Credenciales de entrada — no persistir en UI state.
 */
data class SignInRequest(
    val email: String,
    val password: String
)

sealed class AuthResult {
    data object Success : AuthResult()
    data class Failure(val failure: AuthFailure) : AuthResult()
}

sealed class AuthFailure {
    data object InvalidCredentials : AuthFailure()
    data object Network : AuthFailure()
    data object SessionExpired : AuthFailure()
    data object Unavailable : AuthFailure()
    data class Unknown(val message: String) : AuthFailure()
}

/**
 * Estado de pantalla login — nunca incluye password ni tokens.
 */
sealed class LoginUiState {
    data object Idle : LoginUiState()
    data object Loading : LoginUiState()
    data class Error(val message: String) : LoginUiState()
    data object Authenticated : LoginUiState()
}

object AuthFailureMessages {
    fun message(failure: AuthFailure): String = when (failure) {
        AuthFailure.InvalidCredentials -> "Email o contraseña incorrectos."
        AuthFailure.Network -> "Problema de conexión. Intentá nuevamente."
        AuthFailure.SessionExpired -> "Tu sesión expiró. Volvé a iniciar sesión."
        AuthFailure.Unavailable -> "La autenticación no está disponible en este momento."
        is AuthFailure.Unknown -> failure.message.ifBlank {
            "No pudimos iniciar sesión. Intentá nuevamente."
        }
    }
}

object AuthErrorMapper {
    fun fromThrowable(error: Throwable): AuthFailure {
        val raw = error.message.orEmpty()
        val lower = raw.lowercase()
        return when {
            "invalid login" in lower ||
                "invalid_credentials" in lower ||
                "invalid credentials" in lower ||
                "email not confirmed" in lower ||
                lower.contains("400") && lower.contains("password") -> AuthFailure.InvalidCredentials
            "unable to resolve" in lower ||
                "network" in lower ||
                "timeout" in lower ||
                "connection" in lower -> AuthFailure.Network
            "jwt" in lower && ("expired" in lower || "exp" in lower) ||
                "session" in lower && "expired" in lower ||
                "refresh" in lower && ("fail" in lower || "error" in lower) ->
                AuthFailure.SessionExpired
            "configuration" in lower ||
                "not configured" in lower ||
                "unavailable" in lower ||
                "AUTH_UNAVAILABLE" in raw -> AuthFailure.Unavailable
            else -> AuthFailure.Unknown(
                com.comunidapp.shared.ui.ErrorSanitizer.sanitize(error)
            )
        }
    }
}
