package com.comunidapp.shared.auth

import com.comunidapp.shared.session.SessionState
import com.comunidapp.shared.session.SessionUser
import com.comunidapp.shared.ui.ErrorSanitizer
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
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

    companion object {
        fun createClient(
            config: SharedSupabaseConfig,
            storage: SecureSessionStorage
        ): SupabaseClient {
            require(config.isUsable) { "AUTH_UNAVAILABLE" }
            return createSupabaseClient(
                supabaseUrl = config.url.trim(),
                supabaseKey = config.anonKey.trim()
            ) {
                install(Auth) {
                    sessionManager = SecureStorageSessionManager(storage)
                    autoLoadFromStorage = true
                    autoSaveToStorage = true
                    alwaysAutoRefresh = true
                }
            }
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
 * Factory usada solo desde Kotlin (PocIosEntry). No exportar a ObjC.
 */
internal fun createAuthRepository(
    config: SharedSupabaseConfig?,
    storage: SecureSessionStorage
): AuthRepository {
    val usable = config.usableOrNull() ?: return UnconfiguredAuthSessionRepository()
    val client = SupabaseAuthSessionGateway.createClient(usable, storage)
    return GatewayAuthRepository(SupabaseAuthSessionGateway(client))
}
