package com.comunidapp.app.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.LeoverApplication
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.UserRepository
import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.domain.files.FileAssetOwner
import com.comunidapp.app.domain.files.FileAssetPurpose
import com.comunidapp.app.domain.files.FileAssetVisibility
import com.comunidapp.app.domain.files.FileUploadRequest
import com.comunidapp.app.domain.files.FileUiErrorMapper
import com.comunidapp.app.domain.media.AvatarPhotoTempStore
import com.comunidapp.app.domain.media.MediaDiagnostic
import com.comunidapp.app.domain.user.ProfileAvatarResolver
import com.comunidapp.app.domain.user.UpdateMyProfileCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val homeLocalityId: String? = null,
    val phone: String = "",
    val showLocation: Boolean = true,
    val showPhone: Boolean = false,
    val profilePrivate: Boolean = true,
    val profileImageUrl: String? = null,
    val avatarPath: String? = null,
    val pendingImageUri: Uri? = null,
    val editorSourceUri: Uri? = null,
    val processedPhotoPath: String? = null,
    val registeredAvatarAssetId: String? = null,
    val isProcessingPhoto: Boolean = false,
    val photoUploadFailed: Boolean = false,
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
            val displayUrl = ProfileAvatarResolver.displayUrl(profile)
                ?: ProfileAvatarResolver.httpOrLocalUrl(profile)
            val privacy = userRepository.getPrivacySettings(profile.id).getOrNull()
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
                    homeLocalityId = profile.homeLocalityId,
                    phone = profile.phone.orEmpty(),
                    showLocation = privacy?.showLocation ?: true,
                    showPhone = privacy?.showPhone ?: profile.phonePublic,
                    profilePrivate = privacy?.profilePrivate ?: profile.profilePrivate,
                    profileImageUrl = displayUrl,
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

    fun onAdministrativeLocationChange(
        locationText: String,
        city: String,
        province: String,
        homeLocalityId: String?
    ) {
        _uiState.update {
            it.copy(
                locationText = locationText,
                city = city,
                province = province,
                homeLocalityId = homeLocalityId?.trim()?.ifBlank { null },
                countryCode = it.countryCode.ifBlank { "AR" },
                errorMessage = null
            )
        }
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

    fun onShowLocationChange(value: Boolean) {
        _uiState.update { it.copy(showLocation = value, errorMessage = null) }
    }

    fun onShowPhoneChange(value: Boolean) {
        _uiState.update { it.copy(showPhone = value, errorMessage = null) }
    }

    fun onPhoneChange(value: String) {
        _uiState.update { it.copy(phone = value, errorMessage = null) }
    }

    fun onProfilePrivateChange(value: Boolean) {
        _uiState.update { it.copy(profilePrivate = value, errorMessage = null) }
    }

    fun onImageSelected(uri: Uri?) {
        if (uri == null) {
            _uiState.update {
                it.copy(
                    pendingImageUri = null,
                    processedPhotoPath = null,
                    registeredAvatarAssetId = null,
                    editorSourceUri = null,
                    photoUploadFailed = false,
                    errorMessage = null
                )
            }
            return
        }
        _uiState.update { it.copy(editorSourceUri = uri, errorMessage = null, photoUploadFailed = false) }
    }

    fun onCroppedPhoto(uri: Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessingPhoto = true,
                    errorMessage = null,
                    photoUploadFailed = false,
                    editorSourceUri = null
                )
            }
            val processed = withContext(Dispatchers.Default) {
                runCatching {
                    val app = LeoverApplication.instance
                    com.comunidapp.app.domain.media.AndroidImageIngest(
                        app.contentResolver,
                        app.cacheDir
                    ).encodeAlreadyCropped(uri.toString(), FileAssetPurpose.USER_AVATAR)
                }
            }
            processed.onSuccess { result ->
                if (!result.normalized) {
                    onPhotoProcessFailed(com.comunidapp.app.domain.media.MediaDiagnostic.ENCODE)
                    return@onSuccess
                }
                val file = java.io.File(Uri.parse(result.uriString).path ?: "")
                if (!file.exists() || file.length() <= 0L) {
                    onPhotoProcessFailed(com.comunidapp.app.domain.media.MediaDiagnostic.ENCODE)
                    return@onSuccess
                }
                val previous = _uiState.value.processedPhotoPath
                if (previous != null && previous != file.absolutePath) {
                    AvatarPhotoTempStore.delete(previous)
                }
                _uiState.update {
                    it.copy(
                        pendingImageUri = Uri.fromFile(file),
                        processedPhotoPath = file.absolutePath,
                        registeredAvatarAssetId = null,
                        editorSourceUri = null,
                        isProcessingPhoto = false,
                        photoUploadFailed = false,
                        errorMessage = null
                    )
                }
            }.onFailure { error ->
                onPhotoProcessFailed(
                    com.comunidapp.app.domain.media.MediaDiagnostic.fromThrowable(error)
                )
            }
        }
    }

    fun onPhotoCropFailed(code: String) {
        onPhotoProcessFailed(code)
    }

    private fun onPhotoProcessFailed(code: String) {
        _uiState.update {
            it.copy(
                isProcessingPhoto = false,
                photoUploadFailed = true,
                editorSourceUri = null,
                errorMessage = FileUiErrorMapper.message(code)
            )
        }
    }

    fun cancelPhotoEditor() {
        _uiState.update { it.copy(editorSourceUri = null, isProcessingPhoto = false) }
    }

    fun skipPhotoAndContinue() {
        _uiState.update {
            it.copy(
                pendingImageUri = null,
                processedPhotoPath = null,
                editorSourceUri = null,
                photoUploadFailed = false,
                errorMessage = null
            )
        }
        saveProfile()
    }

    fun saveProfile() {
        val state = _uiState.value
        val baseUser = loadedUser ?: return

        if (state.name.isBlank()) {
            _uiState.update { it.copy(errorMessage = "El nombre es obligatorio") }
            return
        }

        viewModelScope.launch {
            try {
            _uiState.update { it.copy(isSaving = true, errorMessage = null, saveSuccess = false) }

            var avatarPath = state.avatarPath
            val authUser = authRepository.getCurrentUser()
            if (authUser == null) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = FileUiErrorMapper.message(
                            MediaDiagnostic.DB,
                            "SET_PERSON_AVATAR: NOT_AUTHENTICATED"
                        )
                    )
                }
                return@launch
            }
            val actorId = authUser.id
            val processedFile = state.processedPhotoPath
                ?.let { java.io.File(it) }
                ?.takeIf { it.exists() && it.length() > 0L }
            if (state.pendingImageUri != null && processedFile == null && state.registeredAvatarAssetId.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        photoUploadFailed = true,
                        errorMessage = FileUiErrorMapper.message(
                            com.comunidapp.app.domain.media.MediaDiagnostic.CROP
                        )
                    )
                }
                return@launch
            }
            var assetId = state.registeredAvatarAssetId?.takeIf { it.isNotBlank() }
            if (assetId == null && processedFile != null) {
                val uploadUri = Uri.fromFile(processedFile).toString()
                val upload = DataProvider.fileUploadCoordinator.startUpload(
                    uriString = uploadUri,
                    request = FileUploadRequest(
                        purpose = FileAssetPurpose.USER_AVATAR,
                        owner = FileAssetOwner.User(actorId),
                        originalFilename = "avatar.jpg",
                        declaredMimeType = "image/jpeg",
                        sizeBytes = processedFile.length(),
                        requestedVisibility = FileAssetVisibility.PUBLIC
                    ),
                    actorUserId = actorId
                )
                when (upload) {
                    is AppResult.Success -> {
                        assetId = upload.data.assetId
                    }
                    is AppResult.Failure -> {
                        _uiState.update {
                            it.copy(
                                isSaving = false,
                                photoUploadFailed = true,
                                errorMessage = FileUiErrorMapper.message(upload.error)
                            )
                        }
                        return@launch
                    }
                }
            }
            if (!assetId.isNullOrBlank()) {
                userRepository.setPersonAvatar(assetId)
                    .onSuccess {
                        avatarPath = assetId
                        AvatarPhotoTempStore.delete(state.processedPhotoPath)
                    }
                    .onFailure { error ->
                        MediaDiagnostic.logStaging(
                            MediaDiagnostic.classifyDb(
                                "SET_PERSON_AVATAR",
                                error.message
                            )
                        )
                        _uiState.update {
                            it.copy(
                                isSaving = false,
                                photoUploadFailed = true,
                                registeredAvatarAssetId = assetId,
                                errorMessage = FileUiErrorMapper.message(
                                    "SET_PERSON_AVATAR",
                                    error.message?.take(80)
                                )
                            )
                        }
                        return@launch
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
                    homeLocalityId = state.homeLocalityId,
                    avatarPath = avatarPath,
                    phone = state.phone
                )
            ).onSuccess { updated ->
                val latest = userRepository.getUser(state.userId) ?: baseUser.copy(
                    name = updated.displayName,
                    displayName = updated.displayName,
                    bio = updated.bio,
                    city = updated.city,
                    province = updated.province,
                    countryCode = updated.countryCode,
                    avatarPath = updated.avatarPath ?: avatarPath
                )
                loadedUser = latest
                val remoteUrl = runCatching { ProfileAvatarResolver.displayUrl(latest) }
                    .onFailure { MediaDiagnostic.logRenderFailure() }
                    .getOrNull()
                if (remoteUrl == null && !latest.avatarPath.isNullOrBlank()) {
                    MediaDiagnostic.logRenderFailure()
                }
                val privacy = userRepository.getPrivacySettings(state.userId).getOrNull()
                    ?: com.comunidapp.app.domain.user.UserPrivacySettings()
                userRepository.updatePrivacySettings(
                    state.userId,
                    privacy.copy(
                        profileVisibility = if (state.profilePrivate) {
                            com.comunidapp.app.domain.user.ProfileVisibility.PRIVATE
                        } else {
                            com.comunidapp.app.domain.user.ProfileVisibility.PUBLIC
                        },
                        showLocation = state.showLocation,
                        showPhone = state.showPhone
                    )
                )
                if (state.profilePrivate != baseUser.profilePrivate) {
                    // Re-evaluate social surfaces; authorization is dynamic on read.
                    runCatching { DataProvider.feedRepository.refreshPosts() }
                    runCatching { DataProvider.feedRepository.refreshStories() }
                }
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        saveSuccess = true,
                        profileImageUrl = remoteUrl,
                        avatarPath = latest.avatarPath ?: avatarPath,
                        pendingImageUri = null,
                        processedPhotoPath = null,
                        registeredAvatarAssetId = null,
                        photoUploadFailed = false
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
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        photoUploadFailed = true,
                        errorMessage = FileUiErrorMapper.message(
                            MediaDiagnostic.fromThrowable(error)
                        )
                    )
                }
            }
        }
    }

    fun clearSaveSuccess() {
        _uiState.update { it.copy(saveSuccess = false) }
    }
}
