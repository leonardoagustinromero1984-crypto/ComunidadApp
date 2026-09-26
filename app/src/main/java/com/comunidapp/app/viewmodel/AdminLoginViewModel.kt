package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminLoginUiState(
    val username: String = "",
    val password: String = "",
    val isBusy: Boolean = false,
    val error: String? = null,
    val isLoggedIn: Boolean = false
)

class AdminLoginViewModel(
    private val authRepository: AuthRepository = AuthProvider.repository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminLoginUiState())
    val uiState: StateFlow<AdminLoginUiState> = _uiState.asStateFlow()

    fun onUsernameChange(value: String) {
        _uiState.update { it.copy(username = value, error = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, error = null) }
    }

    fun login() {
        val state = _uiState.value
        if (state.isBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, error = null) }
            authRepository.loginAdministrative(state.username, state.password)
                .onSuccess {
                    _uiState.update { it.copy(isBusy = false, isLoggedIn = true) }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            error = "Usuario o contraseña incorrectos."
                        )
                    }
                }
        }
    }
}
