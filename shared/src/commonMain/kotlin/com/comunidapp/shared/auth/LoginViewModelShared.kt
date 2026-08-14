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
    private val appleSignInController: AppleSignInController? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val _ui = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _ui.asStateFlow()

    val appleSignInVisible: Boolean =
        isAppleSignInAvailable() && appleSignInController != null

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

    fun signInWithApple() {
        if (_ui.value is LoginUiState.Loading) return
        val controller = appleSignInController
        if (controller == null || !isAppleSignInAvailable()) {
            _ui.value = LoginUiState.Error(
                AuthFailureMessages.message(AuthFailure.ConfigurationRequired)
            )
            return
        }
        scope.launch {
            _ui.value = LoginUiState.Loading
            when (val platform = controller.requestCredential()) {
                is AppleSignInPlatformResult.Success -> {
                    when (
                        val result = authRepository.signInWithAppleIdToken(
                            platform.idToken,
                            platform.rawNonce
                        )
                    ) {
                        AuthResult.Success -> _ui.value = LoginUiState.Authenticated
                        is AuthResult.Failure -> _ui.value = LoginUiState.Error(
                            AuthFailureMessages.message(result.failure)
                        )
                    }
                }
                AppleSignInPlatformResult.Cancelled ->
                    _ui.value = LoginUiState.Error(AuthFailureMessages.message(AuthFailure.Cancelled))
                AppleSignInPlatformResult.ConfigurationRequired ->
                    _ui.value = LoginUiState.Error(
                        AuthFailureMessages.message(AuthFailure.ConfigurationRequired)
                    )
                is AppleSignInPlatformResult.Failed ->
                    _ui.value = LoginUiState.Error(
                        AuthFailureMessages.message(AuthFailure.Unknown(platform.message))
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
