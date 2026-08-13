package com.comunidapp.shared.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Presenter de login — el password no se guarda en StateFlow.
 */
class LoginViewModelShared(
    private val authRepository: AuthRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val _ui = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _ui.asStateFlow()

    fun signIn(email: String, password: String) {
        if (_ui.value is LoginUiState.Loading) return
        scope.launch {
            _ui.value = LoginUiState.Loading
            when (val result = authRepository.signInWithEmailPassword(SignInRequest(email, password))) {
                AuthResult.Success -> _ui.value = LoginUiState.Authenticated
                is AuthResult.Failure -> _ui.value = LoginUiState.Error(
                    AuthFailureMessages.message(result.failure)
                )
            }
        }
    }

    fun resetError() {
        if (_ui.value is LoginUiState.Error) {
            _ui.value = LoginUiState.Idle
        }
    }

    fun clear() {
        scope.cancel()
    }
}
