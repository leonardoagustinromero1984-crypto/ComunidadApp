package com.comunidapp.app.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.UserRepository
import com.comunidapp.app.domain.user.UpdateMyProfileCommand
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
    val city: String = "",
    val province: String = "",
    val countryCode: String = "",
    val locationText: String = "",
    val phone: String = "",
    val profilePrivate: Boolean = true,
    val profileImageUrl: String? = null,
    val avatarPath: String? = null,
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
                    name = profile.resolvedDisplayName,
                    bio = profile.bio.orEmpty(),
                    city = profile.city.orEmpty(),
                    province = profile.province.orEmpty(),
                    countryCode = profile.countryCode.orEmpty(),
                    locationText = profile.locationText.orEmpty(),
                    phone = profile.phone.orEmpty(),
                    profilePrivate = profile.profilePrivate,
                    profileImageUrl = profile.profileImageUrl,
                    avatarPath = profile.avatarPath
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

    fun onCityChange(value: String) {
        _uiState.update { it.copy(city = value, errorMessage = null) }
    }

    fun onProvinceChange(value: String) {
        _uiState.update { it.copy(province = value, errorMessage = null) }
    }

    fun onCountryCodeChange(value: String) {
        _uiState.update { it.copy(countryCode = value.uppercase(), errorMessage = null) }
    }

    fun onPhoneChange(value: String) {
        _uiState.update { it.copy(phone = value, errorMessage = null) }
    }

    fun onProfilePrivateChange(value: Boolean) {
        _uiState.update { it.copy(profilePrivate = value, errorMessage = null) }
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

            var avatarPath = state.avatarPath
            var imageUrl = state.profileImageUrl
            state.pendingImageUri?.let { uri ->
                val avatarStorage = DataProvider.profileAvatarStorage
                if (avatarStorage != null) {
                    avatarStorage.uploadAvatar(state.userId, uri)
                        .onSuccess { path ->
                            avatarPath = path
                            avatarStorage.createSignedUrl(path)
                                .onSuccess { imageUrl = it }
                        }
                        .onFailure { error ->
                            _uiState.update {
                                it.copy(
                                    isSaving = false,
                                    errorMessage = error.message ?: "No se pudo subir la foto"
                                )
                            }
                            return@launch
                        }
                } else {
                    // Mock: conservar URL local/legacy si hay storage genérico.
                    DataProvider.storageService?.uploadImage(
                        com.comunidapp.app.data.remote.storage.StoragePaths.userAvatar(state.userId),
                        uri
                    )?.onSuccess { imageUrl = it }
                }
            }

            userRepository.updateMyProfile(
                state.userId,
                UpdateMyProfileCommand(
                    displayName = state.name.trim(),
                    bio = state.bio.trim().ifBlank { null },
                    city = state.city.trim().ifBlank {
                        state.locationText.trim().ifBlank { null }
                    },
                    province = state.province.trim().ifBlank { null },
                    countryCode = state.countryCode.trim().ifBlank { null },
                    avatarPath = avatarPath
                )
            ).onSuccess { updated ->
                loadedUser = baseUser.copy(
                    name = updated.displayName,
                    displayName = updated.displayName,
                    bio = updated.bio,
                    city = updated.city,
                    province = updated.province,
                    countryCode = updated.countryCode,
                    avatarPath = updated.avatarPath,
                    profileImageUrl = imageUrl ?: updated.avatarUrl
                )
                if (state.profilePrivate != baseUser.profilePrivate) {
                    val privacy = userRepository.getPrivacySettings(state.userId).getOrNull()
                        ?: com.comunidapp.app.domain.user.UserPrivacySettings()
                    userRepository.updatePrivacySettings(
                        state.userId,
                        privacy.copy(
                            profileVisibility = if (state.profilePrivate) {
                                com.comunidapp.app.domain.user.ProfileVisibility.PRIVATE
                            } else {
                                com.comunidapp.app.domain.user.ProfileVisibility.PUBLIC
                            }
                        )
                    )
                }
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        saveSuccess = true,
                        profileImageUrl = imageUrl,
                        avatarPath = avatarPath,
                        pendingImageUri = null
                    )
                }
            }.onFailure { error ->
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
