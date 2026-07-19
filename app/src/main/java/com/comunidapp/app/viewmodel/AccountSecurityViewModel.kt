package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.MockAuthRepository
import com.comunidapp.app.domain.auth.AuthAnalytics
import com.comunidapp.app.domain.auth.AuthErrorMapper
import com.comunidapp.app.domain.auth.DeleteAccountCommand
import com.comunidapp.app.domain.auth.validation.AuthValidators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class AccountSecurityUiState(
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val isChangingPassword: Boolean = false,
    val passwordChangeSuccess: Boolean = false,
    val deleteCurrentPassword: String = "",
    val deleteAcknowledged: Boolean = false,
    val deleteConfirmationText: String = "",
    val isDeleting: Boolean = false,
    val deleteSuccess: Boolean = false,
    val errorMessage: String? = null
)

class AccountSecurityViewModel(
    private val authRepository: AuthRepository = AuthProvider.repository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountSecurityUiState())
    val uiState: StateFlow<AccountSecurityUiState> = _uiState.asStateFlow()

    fun onCurrentPasswordChange(value: String) =
        _uiState.update { it.copy(currentPassword = value, errorMessage = null, passwordChangeSuccess = false) }

    fun onNewPasswordChange(value: String) =
        _uiState.update { it.copy(newPassword = value, errorMessage = null, passwordChangeSuccess = false) }

    fun onConfirmPasswordChange(value: String) =
        _uiState.update { it.copy(confirmPassword = value, errorMessage = null, passwordChangeSuccess = false) }

    fun onDeletePasswordChange(value: String) =
        _uiState.update { it.copy(deleteCurrentPassword = value, errorMessage = null) }

    fun onDeleteAcknowledgedChange(value: Boolean) =
        _uiState.update { it.copy(deleteAcknowledged = value, errorMessage = null) }

    fun onDeleteConfirmationTextChange(value: String) =
        _uiState.update { it.copy(deleteConfirmationText = value, errorMessage = null) }

    fun changePassword() {
        val state = _uiState.value
        if (state.isChangingPassword || state.isDeleting) return
        AuthValidators.validatePasswordConfirmation(state.newPassword, state.confirmPassword)
            .getOrElse { err ->
                _uiState.update { it.copy(errorMessage = AuthErrorMapper.fromThrowable(err).userMessage) }
                return
            }
        if (state.currentPassword.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Ingresá tu contraseña actual.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isChangingPassword = true, errorMessage = null) }
            authRepository.changePassword(state.currentPassword, state.newPassword)
                .onSuccess {
                    AuthAnalytics.track("password_changed")
                    _uiState.update {
                        it.copy(
                            isChangingPassword = false,
                            passwordChangeSuccess = true,
                            currentPassword = "",
                            newPassword = "",
                            confirmPassword = ""
                        )
                    }
                }
                .onFailure { error ->
                    AuthAnalytics.track("auth_error_shown")
                    _uiState.update {
                        it.copy(
                            isChangingPassword = false,
                            errorMessage = AuthErrorMapper.fromThrowable(error).userMessage
                        )
                    }
                }
        }
    }

    fun deleteAccount() {
        val state = _uiState.value
        if (state.isDeleting || state.isChangingPassword) return
        if (!state.deleteAcknowledged) {
            _uiState.update { it.copy(errorMessage = "Marcá que comprendés que la acción es permanente.") }
            return
        }
        if (state.deleteConfirmationText.trim() != DeleteAccountCommand.CONFIRMATION_PHRASE) {
            _uiState.update {
                it.copy(errorMessage = "Escribí ${DeleteAccountCommand.CONFIRMATION_PHRASE} para confirmar.")
            }
            return
        }
        if (state.deleteCurrentPassword.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Ingresá tu contraseña para confirmar.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true, errorMessage = null) }
            val user = authRepository.getCurrentUser()
            if (user == null) {
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        errorMessage = AuthErrorMapper.fromCode(
                            com.comunidapp.app.domain.auth.AuthErrorCode.SESSION_EXPIRED,
                            "no session"
                        ).userMessage
                    )
                }
                return@launch
            }
            val reauth = authRepository.login(user.email, state.deleteCurrentPassword)
            if (reauth.isFailure) {
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        errorMessage = AuthErrorMapper.fromThrowable(reauth.exceptionOrNull()!!).userMessage
                    )
                }
                return@launch
            }
            val key = UUID.randomUUID().toString()
            authRepository.deleteAccount(key)
                .onSuccess {
                    AuthAnalytics.track("account_deleted")
                    _uiState.update {
                        it.copy(
                            isDeleting = false,
                            deleteSuccess = true,
                            deleteCurrentPassword = "",
                            deleteConfirmationText = ""
                        )
                    }
                }
                .onFailure { error ->
                    AuthAnalytics.track("auth_error_shown")
                    _uiState.update {
                        it.copy(
                            isDeleting = false,
                            errorMessage = AuthErrorMapper.fromThrowable(error).userMessage
                        )
                    }
                }
        }
    }
}

data class PasswordResetActiveUiState(
    val newPassword: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val success: Boolean = false,
    val consumed: Boolean = false,
    val errorMessage: String? = null
)

class PasswordResetActiveViewModel(
    private val authRepository: AuthRepository = AuthProvider.repository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PasswordResetActiveUiState())
    val uiState: StateFlow<PasswordResetActiveUiState> = _uiState.asStateFlow()

    fun onNewPasswordChange(value: String) =
        _uiState.update { it.copy(newPassword = value, errorMessage = null) }

    fun onConfirmPasswordChange(value: String) =
        _uiState.update { it.copy(confirmPassword = value, errorMessage = null) }

    fun submit() {
        val state = _uiState.value
        if (state.isLoading || state.consumed || state.success) return
        AuthValidators.validatePasswordConfirmation(state.newPassword, state.confirmPassword)
            .getOrElse { err ->
                _uiState.update { it.copy(errorMessage = AuthErrorMapper.fromThrowable(err).userMessage) }
                return
            }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, consumed = true) }
            // Mock: activate recovery if token path; prefer updatePasswordFromRecovery.
            if (!AuthProvider.isRemoteBackendEnabled && authRepository is MockAuthRepository) {
                if (!authRepository.isRecoverySessionActive()) {
                    // Allow mock ForgotPassword token path via resetPassword elsewhere.
                }
            }
            authRepository.updatePasswordFromRecovery(state.newPassword)
                .onSuccess {
                    AuthAnalytics.track("password_reset_completed")
                    _uiState.update {
                        it.copy(isLoading = false, success = true, newPassword = "", confirmPassword = "")
                    }
                }
                .onFailure { error ->
                    AuthAnalytics.track("auth_error_shown")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            consumed = false,
                            errorMessage = AuthErrorMapper.fromThrowable(error).userMessage
                        )
                    }
                }
        }
    }
}
