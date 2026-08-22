package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.UserRepository
import com.comunidapp.app.domain.user.ProfileVisibility
import com.comunidapp.app.domain.user.UserPrivacySettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfilePrivacyUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val userId: String = "",
    val visibility: ProfileVisibility = ProfileVisibility.PRIVATE,
    val errorMessage: String? = null,
    val saveSuccess: Boolean = false
)

class ProfilePrivacyViewModel(
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val userRepository: UserRepository = DataProvider.userRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfilePrivacyUiState())
    val uiState: StateFlow<ProfilePrivacyUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val authUser = authRepository.getCurrentUser()
            if (authUser == null) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "No hay sesión activa")
                }
                return@launch
            }
            val settings = userRepository.getPrivacySettings(authUser.id).getOrNull()
                ?: UserPrivacySettings()
            _uiState.update {
                ProfilePrivacyUiState(
                    isLoading = false,
                    userId = authUser.id,
                    visibility = settings.profileVisibility
                )
            }
        }
    }

    fun onVisibilityChange(value: ProfileVisibility) {
        _uiState.update { it.copy(visibility = value, errorMessage = null) }
    }

    fun save() {
        val state = _uiState.value
        if (state.userId.isBlank() || state.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            userRepository.updatePrivacySettings(
                state.userId,
                UserPrivacySettings(profileVisibility = state.visibility)
            ).onSuccess {
                _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = error.message ?: "No se pudo guardar la privacidad"
                    )
                }
            }
        }
    }

    fun clearSaveSuccess() {
        _uiState.update { it.copy(saveSuccess = false) }
    }
}
