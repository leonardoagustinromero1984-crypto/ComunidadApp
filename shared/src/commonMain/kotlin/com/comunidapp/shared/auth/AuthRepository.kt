package com.comunidapp.shared.auth

import com.comunidapp.shared.session.SessionRepository
import com.comunidapp.shared.session.SessionState

/**
 * Auth multiplataforma — tokens nunca en [com.comunidapp.shared.session.SessionUser].
 */
interface AuthRepository : SessionRepository {
    suspend fun signInWithEmailPassword(request: SignInRequest): AuthResult
    suspend fun signInWithAppleIdToken(idToken: String, rawNonce: String?): AuthResult
    suspend fun restoreSession(): SessionState
    suspend fun refreshSession(): AuthResult
}

class SignInWithEmailPasswordUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(request: SignInRequest): AuthResult =
        repository.signInWithEmailPassword(request)
}

class RestoreSessionUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(): SessionState = repository.restoreSession()
}

class RefreshSessionUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(): AuthResult = repository.refreshSession()
}
