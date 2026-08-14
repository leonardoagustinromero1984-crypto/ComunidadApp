package com.comunidapp.shared.auth

import com.comunidapp.shared.session.SessionState
import com.comunidapp.shared.session.SessionUser
import com.comunidapp.shared.ui.ErrorSanitizer
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Apple
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeout

/**
 * Gateway REAL_REMOTE sobre supabase-kt Auth.
 * internal: no exportar SupabaseClient al framework ObjC.
 */
internal class SupabaseAuthSessionGateway(
    private val client: SupabaseClient
) : AuthSessionGateway {

    override fun observeSession(): Flow<SessionState> =
        client.auth.sessionStatus.map { it.toSessionState() }

    override suspend fun currentSession(): SessionState =
        client.auth.sessionStatus.value.toSessionState()

    override suspend fun signIn(email: String, password: String): AuthResult {
        return try {
            withTimeout(30_000L) {
                client.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
            }
            val user = client.auth.currentUserOrNull()
            if (user == null) {
                AuthResult.Failure(AuthFailure.InvalidCredentials)
            } else {
                AuthResult.Success
            }
        } catch (t: Throwable) {
            AuthResult.Failure(AuthErrorMapper.fromThrowable(t))
        }
    }

    override suspend fun signInWithAppleIdToken(idToken: String, rawNonce: String?): AuthResult {
        if (idToken.isBlank()) {
            return AuthResult.Failure(AuthFailure.InvalidCredentials)
        }
        return try {
            withTimeout(30_000L) {
                client.auth.signInWith(IDToken) {
                    this.idToken = idToken
                    provider = Apple
                    nonce = rawNonce
                }
            }
            val user = client.auth.currentUserOrNull()
            if (user == null) {
                AuthResult.Failure(AuthFailure.InvalidCredentials)
            } else {
                AuthResult.Success
            }
        } catch (t: Throwable) {
            val mapped = AuthErrorMapper.fromThrowable(t)
            // Backend apple enabled=false → ConfigurationRequired
            if (mapped is AuthFailure.Unavailable || mapped is AuthFailure.Unknown) {
                val lower = t.message.orEmpty().lowercase()
                if (
                    "apple" in lower ||
                    "provider" in lower ||
                    "not enabled" in lower ||
                    "configuration" in lower
                ) {
                    return AuthResult.Failure(AuthFailure.ConfigurationRequired)
                }
            }
            AuthResult.Failure(mapped)
        }
    }

    override suspend fun restoreSession(): SessionState {
        return try {
            client.auth.sessionStatus.value.toSessionState()
        } catch (t: Throwable) {
            SessionState.Error(ErrorSanitizer.sanitize(t))
        }
    }

    override suspend fun refreshSession(): AuthResult {
        return try {
            withTimeout(30_000L) {
                client.auth.refreshCurrentSession()
            }
            AuthResult.Success
        } catch (t: Throwable) {
            AuthResult.Failure(AuthErrorMapper.fromThrowable(t))
        }
    }

    override suspend fun signOut() {
        try {
            client.auth.signOut()
        } catch (_: Throwable) {
            // Best-effort remoto; storage se limpia vía SessionManager delete en signOut SDK.
        }
    }

}

internal fun SessionStatus.toSessionState(): SessionState = when (this) {
    is SessionStatus.Authenticated -> {
        val user = session.user
        if (user == null) {
            SessionState.Unauthenticated
        } else {
            SessionState.Authenticated(
                SessionUser(
                    userId = user.id,
                    email = user.email,
                    displayName = user.userMetadata
                        ?.get("name")
                        ?.toString()
                        ?.trim('"')
                        ?.takeIf { it.isNotBlank() }
                )
            )
        }
    }
    is SessionStatus.NotAuthenticated -> SessionState.Unauthenticated
    SessionStatus.Initializing -> SessionState.Unknown
    is SessionStatus.RefreshFailure -> SessionState.Expired
}

/**
 * Factory legacy → delega en [com.comunidapp.shared.remote.SharedRemoteRuntime]
 * para un solo cliente Auth+Postgrest.
 */
internal fun createAuthRepository(
    config: SharedSupabaseConfig?,
    storage: SecureSessionStorage
): AuthRepository =
    com.comunidapp.shared.remote.SharedRemoteRuntime.create(config, storage).authRepository
