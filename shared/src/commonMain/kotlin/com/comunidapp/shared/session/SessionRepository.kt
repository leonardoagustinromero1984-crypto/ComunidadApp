package com.comunidapp.shared.session

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface SessionRepository {
    val dataMode: SessionDataMode
    fun observeSession(): Flow<SessionState>
    suspend fun currentSession(): SessionState
    suspend fun signOut()
}

class ObserveSessionUseCase(private val repository: SessionRepository) {
    operator fun invoke(): Flow<SessionState> = repository.observeSession()
}

class GetCurrentSessionUseCase(private val repository: SessionRepository) {
    suspend operator fun invoke(): SessionState = repository.currentSession()
}

class SignOutUseCase(private val repository: SessionRepository) {
    suspend operator fun invoke() = repository.signOut()
}

/**
 * Stub determinista para iOS / demos.
 * SESSION_STUB — no Auth productivo.
 */
class FakeSessionRepository(
    initial: SessionState = SessionState.Authenticated(
        SessionUser(
            userId = "demo-user",
            email = "demo@leover.test",
            displayName = "Demo LeoVer"
        )
    )
) : SessionRepository {
    override val dataMode: SessionDataMode = SessionDataMode.SESSION_STUB

    private val state = MutableStateFlow(initial)

    override fun observeSession(): Flow<SessionState> = state.asStateFlow()

    override suspend fun currentSession(): SessionState = state.value

    override suspend fun signOut() {
        state.update { SessionState.Unauthenticated }
    }

    fun setState(next: SessionState) {
        state.value = next
    }
}
