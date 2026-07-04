package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.AccountType
import com.comunidapp.app.data.mock.MockAuthDatabase
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.EmailNotVerifiedException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isLoggedIn: Boolean = false,
    val needsEmailVerification: String? = null
)

class LoginViewModel(
    private val authRepository: AuthRepository = AuthProvider.repository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(email = email, errorMessage = null) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, errorMessage = null) }
    }

    fun login() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, needsEmailVerification = null) }
            authRepository.login(_uiState.value.email, _uiState.value.password)
                .onSuccess {
                    _uiState.update { state -> state.copy(isLoading = false, isLoggedIn = true) }
                }
                .onFailure { error ->
                    when (error) {
                        is EmailNotVerifiedException -> {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    needsEmailVerification = _uiState.value.email.trim().lowercase(),
                                    errorMessage = error.message
                                )
                            }
                        }
                        else -> {
                            _uiState.update {
                                it.copy(isLoading = false, errorMessage = error.message)
                            }
                        }
                    }
                }
        }
    }

    fun clearLoginState() {
        _uiState.update { LoginUiState() }
    }

    fun clearEmailVerificationRedirect() {
        _uiState.update { it.copy(needsEmailVerification = null) }
    }
}

data class RegisterUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val accountType: AccountType = AccountType.PERSON,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val registeredEmail: String? = null
)

class RegisterViewModel(
    private val authRepository: AuthRepository = AuthProvider.repository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun onNameChange(name: String) {
        _uiState.update { it.copy(name = name, errorMessage = null) }
    }

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(email = email, errorMessage = null) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, errorMessage = null) }
    }

    fun onConfirmPasswordChange(confirmPassword: String) {
        _uiState.update { it.copy(confirmPassword = confirmPassword, errorMessage = null) }
    }

    fun onAccountTypeChange(accountType: AccountType) {
        _uiState.update { it.copy(accountType = accountType, errorMessage = null) }
    }

    fun register() {
        val state = _uiState.value
        if (state.isLoading) return
        if (state.password != state.confirmPassword) {
            _uiState.update { it.copy(errorMessage = "Las contraseñas no coinciden") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            authRepository.register(state.name, state.email, state.password, state.accountType)
                .onSuccess {
                    _uiState.update { s ->
                        s.copy(
                            isLoading = false,
                            registeredEmail = state.email.trim().lowercase()
                        )
                    }
                }
                .onFailure { error ->
                    val normalizedEmail = state.email.trim().lowercase()
                    val alreadyExists = error.message
                        ?.contains("Ya existe una cuenta", ignoreCase = true) == true
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = if (alreadyExists) null else error.message,
                            registeredEmail = if (alreadyExists) normalizedEmail else null
                        )
                    }
                }
        }
    }
}

data class ForgotPasswordUiState(
    val email: String = "",
    val token: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val emailSent: Boolean = false,
    val resetSuccess: Boolean = false,
    val mockToken: String? = null
)

class ForgotPasswordViewModel(
    private val authRepository: AuthRepository = AuthProvider.repository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(email = email, errorMessage = null) }
    }

    fun onTokenChange(token: String) {
        _uiState.update { it.copy(token = token, errorMessage = null) }
    }

    fun onNewPasswordChange(password: String) {
        _uiState.update { it.copy(newPassword = password, errorMessage = null) }
    }

    fun onConfirmPasswordChange(password: String) {
        _uiState.update { it.copy(confirmPassword = password, errorMessage = null) }
    }

    fun sendResetEmail() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            authRepository.sendPasswordResetEmail(_uiState.value.email)
                .onSuccess {
                    val mockToken = if (!AuthProvider.isRemoteBackendEnabled) {
                        MockAuthDatabase.findByEmail(_uiState.value.email)?.resetToken
                    } else null
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            emailSent = true,
                            mockToken = mockToken,
                            token = mockToken ?: ""
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.message) }
                }
        }
    }

    fun resetPassword() {
        val state = _uiState.value
        if (state.newPassword != state.confirmPassword) {
            _uiState.update { it.copy(errorMessage = "Las contraseñas no coinciden") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            authRepository.resetPassword(state.email, state.token, state.newPassword)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, resetSuccess = true) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.message) }
                }
        }
    }
}

data class EmailVerificationUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val isVerified: Boolean = false,
    val resendCooldownSeconds: Int = 0
)

class EmailVerificationViewModel(
    private val authRepository: AuthRepository = AuthProvider.repository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EmailVerificationUiState())
    val uiState: StateFlow<EmailVerificationUiState> = _uiState.asStateFlow()

    private var lastResendAtMs: Long = 0L

    fun checkVerification(email: String) {
        viewModelScope.launch {
            val verified = authRepository.isEmailVerified(email)
            _uiState.update { it.copy(isVerified = verified) }
        }
    }

    fun resendVerification(email: String) {
        if (_uiState.value.isLoading || _uiState.value.resendCooldownSeconds > 0) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
            authRepository.sendEmailVerification(email)
                .onSuccess {
                    lastResendAtMs = System.currentTimeMillis()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            successMessage = "Email de confirmación reenviado",
                            resendCooldownSeconds = RESEND_COOLDOWN_SECONDS
                        )
                    }
                    startResendCooldown()
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.message) }
                }
        }
    }

    private fun startResendCooldown() {
        viewModelScope.launch {
            var remaining = RESEND_COOLDOWN_SECONDS
            while (remaining > 0) {
                kotlinx.coroutines.delay(1_000)
                remaining--
                _uiState.update { it.copy(resendCooldownSeconds = remaining) }
            }
        }
    }

    companion object {
        private const val RESEND_COOLDOWN_SECONDS = 60
    }

    fun confirmVerification(email: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
            authRepository.confirmEmailVerification(email)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isVerified = true,
                            successMessage = "Email confirmado correctamente"
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.message) }
                }
        }
    }
}
