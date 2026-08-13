package com.comunidapp.shared.auth

import com.comunidapp.shared.session.SessionState
import com.comunidapp.shared.session.SessionUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Gateway de auth — permite fakes en tests sin Supabase real.
 */
interface AuthSessionGateway {
    fun observeSession(): Flow<SessionState>
    suspend fun currentSession(): SessionState
    suspend fun signIn(email: String, password: String): AuthResult
    suspend fun restoreSession(): SessionState
    suspend fun refreshSession(): AuthResult
    suspend fun signOut()
}

/**
 * Gateway determinista para tests (no tokens).
 */
class FakeAuthSessionGateway(
    initial: SessionState = SessionState.Unknown
) : AuthSessionGateway {
    private val state = MutableStateFlow(initial)
    var failSignInWith: AuthFailure? = null
    var failRefreshWith: AuthFailure? = null
    var restoreTo: SessionState? = null

    override fun observeSession(): Flow<SessionState> = state.asStateFlow()

    override suspend fun currentSession(): SessionState = state.value

    override suspend fun signIn(email: String, password: String): AuthResult {
        failSignInWith?.let {
            return AuthResult.Failure(it)
        }
        if (email.isBlank() || password.isBlank()) {
            return AuthResult.Failure(AuthFailure.InvalidCredentials)
        }
        state.value = SessionState.Authenticated(
            SessionUser(
                userId = "user-${email.hashCode().toUInt()}",
                email = email.trim().lowercase(),
                displayName = email.substringBefore("@")
            )
        )
        return AuthResult.Success
    }

    override suspend fun restoreSession(): SessionState {
        val next = restoreTo ?: when (val current = state.value) {
            is SessionState.Authenticated -> current
            SessionState.Unknown -> SessionState.Unauthenticated
            else -> current
        }
        state.value = next
        return next
    }

    override suspend fun refreshSession(): AuthResult {
        failRefreshWith?.let {
            if (it is AuthFailure.SessionExpired) {
                state.value = SessionState.Expired
            }
            return AuthResult.Failure(it)
        }
        val current = state.value
        if (current is SessionState.Authenticated) {
            return AuthResult.Success
        }
        if (current is SessionState.Expired) {
            // simulate successful refresh restoring user
            state.value = SessionState.Authenticated(
                SessionUser(userId = "refreshed-user", email = "demo@leover.test", displayName = "Demo")
            )
            return AuthResult.Success
        }
        return AuthResult.Failure(AuthFailure.SessionExpired)
    }

    override suspend fun signOut() {
        state.value = SessionState.Unauthenticated
    }

    fun setState(next: SessionState) {
        state.value = next
    }
}

class GatewayAuthRepository(
    private val gateway: AuthSessionGateway
) : AuthRepository {
    override val dataMode = com.comunidapp.shared.session.SessionDataMode.REAL_REMOTE

    override fun observeSession(): Flow<SessionState> = gateway.observeSession()

    override suspend fun currentSession(): SessionState = gateway.currentSession()

    override suspend fun signOut() = gateway.signOut()

    override suspend fun signInWithEmailPassword(request: SignInRequest): AuthResult {
        val email = request.email.trim().lowercase()
        if (email.isBlank() || '@' !in email || request.password.isEmpty()) {
            return AuthResult.Failure(AuthFailure.InvalidCredentials)
        }
        return gateway.signIn(email, request.password)
    }

    override suspend fun restoreSession(): SessionState = gateway.restoreSession()

    override suspend fun refreshSession(): AuthResult = gateway.refreshSession()
}

/**
 * REAL_REMOTE sin credenciales de host — no usa FakeSession.
 */
class UnconfiguredAuthSessionRepository : AuthRepository {
    override val dataMode = com.comunidapp.shared.session.SessionDataMode.REAL_REMOTE

    private val state = MutableStateFlow<SessionState>(SessionState.Unauthenticated)

    override fun observeSession(): Flow<SessionState> = state.asStateFlow()

    override suspend fun currentSession(): SessionState = state.value

    override suspend fun signOut() {
        state.update { SessionState.Unauthenticated }
    }

    override suspend fun signInWithEmailPassword(request: SignInRequest): AuthResult =
        AuthResult.Failure(AuthFailure.Unavailable)

    override suspend fun restoreSession(): SessionState {
        state.value = SessionState.Unauthenticated
        return SessionState.Unauthenticated
    }

    override suspend fun refreshSession(): AuthResult =
        AuthResult.Failure(AuthFailure.Unavailable)
}
