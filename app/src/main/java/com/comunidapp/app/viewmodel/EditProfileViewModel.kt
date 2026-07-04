package com.comunidapp.app.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.AccountType
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.remote.storage.StoragePaths
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditProfileUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val userId: String = "",
    val name: String = "",
    val bio: String = "",
    val locationText: String = "",
    val phone: String = "",
    val accountType: AccountType = AccountType.PERSON,
    val profileImageUrl: String? = null,
    val pendingImageUri: Uri? = null,
    val errorMessage: String? = null,
    val saveSuccess: Boolean = false
)

class EditProfileViewModel(
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val userRepository: UserRepository = DataProvider.userRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditProfileUiState())
    val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()

    private var loadedUser: User? = null

    init {
        viewModelScope.launch {
            val authUser = authRepository.getCurrentUser()
            if (authUser == null) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "No hay sesión activa") }
                return@launch
            }
            val profile = userRepository.getUser(authUser.id) ?: authUser
            loadedUser = profile
            _uiState.update {
                EditProfileUiState(
                    isLoading = false,
                    userId = profile.id,
                    name = profile.name,
                    bio = profile.bio.orEmpty(),
                    locationText = profile.locationText.orEmpty(),
                    phone = profile.phone.orEmpty(),
                    accountType = profile.accountType,
                    profileImageUrl = profile.profileImageUrl
                )
            }
        }
    }

    fun onNameChange(value: String) {
        _uiState.update { it.copy(name = value, errorMessage = null) }
    }

    fun onBioChange(value: String) {
        _uiState.update { it.copy(bio = value, errorMessage = null) }
    }

    fun onLocationChange(value: String) {
        _uiState.update { it.copy(locationText = value, errorMessage = null) }
    }

    fun onPhoneChange(value: String) {
        _uiState.update { it.copy(phone = value, errorMessage = null) }
    }

    fun onAccountTypeChange(value: AccountType) {
        _uiState.update { it.copy(accountType = value, errorMessage = null) }
    }

    fun onImageSelected(uri: Uri?) {
        _uiState.update { it.copy(pendingImageUri = uri, errorMessage = null) }
    }

    fun saveProfile() {
        val state = _uiState.value
        val baseUser = loadedUser ?: return

        if (state.name.isBlank()) {
            _uiState.update { it.copy(errorMessage = "El nombre es obligatorio") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null, saveSuccess = false) }

            var imageUrl = state.profileImageUrl
            state.pendingImageUri?.let { uri ->
                val storage = DataProvider.storageService
                if (storage != null) {
                    storage.uploadImage(StoragePaths.userAvatar(state.userId), uri)
                        .onSuccess { imageUrl = it }
                        .onFailure { error ->
                            _uiState.update {
                                it.copy(
                                    isSaving = false,
                                    errorMessage = error.message ?: "No se pudo subir la foto"
                                )
                            }
                            return@launch
                        }
                }
            }

            val updatedUser = baseUser.copy(
                name = state.name.trim(),
                bio = state.bio.trim().ifBlank { null },
                locationText = state.locationText.trim().ifBlank { null },
                phone = state.phone.trim().ifBlank { null },
                accountType = state.accountType,
                profileImageUrl = imageUrl
            )

            userRepository.updateUser(updatedUser)
                .onSuccess {
                    loadedUser = updatedUser
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            saveSuccess = true,
                            profileImageUrl = imageUrl,
                            pendingImageUri = null
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = error.message ?: "No se pudo guardar el perfil"
                        )
                    }
                }
        }
    }

    fun clearSaveSuccess() {
        _uiState.update { it.copy(saveSuccess = false) }
    }
}
