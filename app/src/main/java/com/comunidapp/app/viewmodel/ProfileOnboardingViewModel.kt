package com.comunidapp.app.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.remote.storage.StoragePaths
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.UserRepository
import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.domain.files.FileAssetOwner
import com.comunidapp.app.domain.files.FileAssetPurpose
import com.comunidapp.app.domain.files.FileAssetVisibility
import com.comunidapp.app.domain.files.FileUploadRequest
import com.comunidapp.app.domain.files.FileUiErrorMapper
import com.comunidapp.app.domain.media.MediaDiagnostic
import com.comunidapp.app.domain.user.CompleteOnboardingCommand
import com.comunidapp.app.domain.user.OnboardingCompleteness
import com.comunidapp.app.domain.user.ProfileVisibility
import com.comunidapp.app.domain.user.UserPrivacySettings
import com.comunidapp.app.domain.user.UsernameValidationException
import com.comunidapp.app.domain.user.UsernameValidators
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OnboardingStep {
    IDENTITY,
    LOCATION_PRIVACY,
    AVATAR_SUMMARY
}

data class ProfileOnboardingUiState(
    val isLoading: Boolean = true,
    val step: OnboardingStep = OnboardingStep.IDENTITY,
    val userId: String = "",
    val displayName: String = "",
    val username: String = "",
    val usernameLocked: Boolean = false,
    val needsBirthDate: Boolean = false,
    val birthDate: String = "",
    val displayNamePresent: Boolean = false,
    val usernameAvailable: Boolean? = null,
    val checkingUsername: Boolean = false,
    val city: String = "",
    val province: String = "",
    val countryCode: String = "",
    val homeLocalityId: String? = null,
    val profileVisibility: ProfileVisibility = ProfileVisibility.PRIVATE,
    val showLocation: Boolean = true,
    val showPhone: Boolean = false,
    val allowFriendRequests: Boolean = true,
    val bio: String = "",
    val pendingImageUri: Uri? = null,
    val editorSourceUri: Uri? = null,
    val processedPhotoPath: String? = null,
    val registeredAvatarAssetId: String? = null,
    val isProcessingPhoto: Boolean = false,
    val avatarPath: String? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val errorMessage: String? = null,
    val photoUploadFailed: Boolean = false,
    val isSubmitting: Boolean = false,
    val success: Boolean = false
) {
    val identityRequired: Boolean
        get() = !usernameLocked || !displayNamePresent || needsBirthDate
}

@OptIn(FlowPreview::class)
class ProfileOnboardingViewModel(
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val userRepository: UserRepository = DataProvider.userRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileOnboardingUiState())
    val uiState: StateFlow<ProfileOnboardingUiState> = _uiState.asStateFlow()

    private val usernameQuery = MutableStateFlow("")

    init {
        viewModelScope.launch {
            val authUser = authRepository.getCurrentUser()
            if (authUser == null) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "No hay sesión activa")
                }
                return@launch
            }
            val profile = userRepository.getUser(authUser.id)
            if (profile != null &&
                !com.comunidapp.app.domain.auth.SignupSessionPolicy.sessionMatchesPerson(
                    authUser.id,
                    profile.id
                )
            ) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "La sesión no coincide con el perfil. Cerrá sesión e iniciá de nuevo."
                    )
                }
                return@launch
            }
            val username = profile?.username?.takeIf { it.isNotBlank() }.orEmpty()
            val displayName = profile?.displayName?.takeIf { it.isNotBlank() }
                ?: profile?.name?.takeIf { it.isNotBlank() }
                ?: authUser.displayName?.takeIf { it.isNotBlank() }
                ?: authUser.name
            val usernameLocked = profile != null && username.isNotBlank()
            com.comunidapp.app.core.logging.AppLog.info(
                "ProfileOnboarding",
                "ONB-LOAD person=${if (profile != null) "YES" else "NO"} uidPresent=YES usernameFromPerson=${if (username.isNotBlank()) "YES" else "NO"}"
            )
            val displayNamePresent = displayName.isNotBlank()
            val needsBirthDate = profile?.birthDate.isNullOrBlank()
            val startStep = if (usernameLocked && displayNamePresent && !needsBirthDate) {
                OnboardingStep.LOCATION_PRIVACY
            } else {
                OnboardingStep.IDENTITY
            }
            _uiState.update {
                ProfileOnboardingUiState(
                    isLoading = false,
                    step = startStep,
                    userId = authUser.id,
                    displayName = displayName,
                    username = username,
                    usernameLocked = usernameLocked,
                    needsBirthDate = needsBirthDate,
                    birthDate = profile?.birthDate.orEmpty(),
                    displayNamePresent = displayNamePresent,
                    usernameAvailable = if (usernameLocked) true else null,
                    city = profile?.city.orEmpty(),
                    province = profile?.province.orEmpty(),
                    countryCode = profile?.countryCode.orEmpty().ifBlank { "AR" },
                    homeLocalityId = profile?.homeLocalityId,
                    bio = profile?.bio.orEmpty(),
                    avatarPath = profile?.avatarPath
                )
            }
            if (!usernameLocked && username.isNotBlank()) {
                usernameQuery.value = username
            }
        }

        viewModelScope.launch {
            usernameQuery
                .debounce(USERNAME_DEBOUNCE_MS)
                .distinctUntilChanged()
                .collect { raw ->
                    checkUsernameAvailability(raw)
                }
        }
    }

    fun onDisplayNameChange(value: String) {
        _uiState.update {
            it.copy(
                displayName = value,
                fieldErrors = it.fieldErrors - "displayName",
                errorMessage = null
            )
        }
    }

    fun onUsernameChange(value: String) {
        if (_uiState.value.usernameLocked) return
        _uiState.update {
            it.copy(
                username = value,
                usernameAvailable = null,
                checkingUsername = value.isNotBlank(),
                fieldErrors = it.fieldErrors - "username",
                errorMessage = null
            )
        }
        usernameQuery.value = value
    }

    fun onBirthDateChange(isoDate: String) {
        _uiState.update {
            it.copy(birthDate = isoDate, fieldErrors = it.fieldErrors - "birthDate", errorMessage = null)
        }
    }

    fun onCityChange(value: String) {
        _uiState.update {
            it.copy(city = value, fieldErrors = it.fieldErrors - "city", errorMessage = null)
        }
    }

    fun onProvinceChange(value: String) {
        _uiState.update {
            it.copy(province = value, fieldErrors = it.fieldErrors - "province", errorMessage = null)
        }
    }

    fun onHomeLocalityIdChange(value: String?) {
        _uiState.update {
            it.copy(
                homeLocalityId = value?.trim()?.ifBlank { null },
                fieldErrors = it.fieldErrors - "province" - "city",
                errorMessage = null
            )
        }
    }

    fun onCountryCodeChange(value: String) {
        _uiState.update {
            it.copy(
                countryCode = value.uppercase().take(COUNTRY_CODE_LENGTH),
                fieldErrors = it.fieldErrors - "countryCode",
                errorMessage = null
            )
        }
    }

    fun onProfileVisibilityChange(value: ProfileVisibility) {
        _uiState.update { it.copy(profileVisibility = value, errorMessage = null) }
    }

    fun onShowLocationChange(value: Boolean) {
        _uiState.update { it.copy(showLocation = value, errorMessage = null) }
    }

    fun onShowPhoneChange(value: Boolean) {
        _uiState.update { it.copy(showPhone = value, errorMessage = null) }
    }

    fun onAllowFriendRequestsChange(value: Boolean) {
        _uiState.update { it.copy(allowFriendRequests = value, errorMessage = null) }
    }

    fun onBioChange(value: String) {
        _uiState.update {
            it.copy(bio = value, fieldErrors = it.fieldErrors - "bio", errorMessage = null)
        }
    }

    fun onImageSelected(uri: Uri?) {
        if (uri == null) {
            _uiState.update {
                it.copy(
                    pendingImageUri = null,
                    processedPhotoPath = null,
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
            val processed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                runCatching {
                    val app = com.comunidapp.app.LeoverApplication.instance
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
                val file = java.io.File(android.net.Uri.parse(result.uriString).path ?: "")
                if (!file.exists() || file.length() <= 0L) {
                    onPhotoProcessFailed(com.comunidapp.app.domain.media.MediaDiagnostic.ENCODE)
                    return@onSuccess
                }
                _uiState.update {
                    it.copy(
                        pendingImageUri = android.net.Uri.fromFile(file),
                        processedPhotoPath = file.absolutePath,
                        registeredAvatarAssetId = null,
                        editorSourceUri = null,
                        isProcessingPhoto = false,
                        photoUploadFailed = false
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

    fun goBack() {
        val state = _uiState.value
        val previous = when (state.step) {
            OnboardingStep.IDENTITY -> return
            OnboardingStep.LOCATION_PRIVACY -> {
                if (!state.identityRequired) return
                OnboardingStep.IDENTITY
            }
            OnboardingStep.AVATAR_SUMMARY -> OnboardingStep.LOCATION_PRIVACY
        }
        _uiState.update { it.copy(step = previous, errorMessage = null) }
    }

    fun goNext() {
        val state = _uiState.value
        when (state.step) {
            OnboardingStep.IDENTITY -> {
                val errors = validateIdentityStep(state)
                if (errors.isNotEmpty()) {
                    _uiState.update { it.copy(fieldErrors = errors) }
                    return
                }
                _uiState.update {
                    it.copy(step = OnboardingStep.LOCATION_PRIVACY, fieldErrors = emptyMap())
                }
            }
            OnboardingStep.LOCATION_PRIVACY -> {
                val errors = validateLocationStep(state)
                if (errors.isNotEmpty()) {
                    _uiState.update { it.copy(fieldErrors = errors) }
                    return
                }
                _uiState.update {
                    it.copy(step = OnboardingStep.AVATAR_SUMMARY, fieldErrors = emptyMap())
                }
            }
            OnboardingStep.AVATAR_SUMMARY -> completeOnboarding()
        }
    }

    fun completeOnboarding() {
        val state = _uiState.value
        if (state.userId.isBlank() || state.isSubmitting) return

        val identityErrors = validateIdentityStep(state)
        if (identityErrors.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    step = if (state.identityRequired) OnboardingStep.IDENTITY else it.step,
                    fieldErrors = identityErrors
                )
            }
            return
        }
        val locationErrors = validateLocationStep(state)
        if (locationErrors.isNotEmpty()) {
            _uiState.update {
                it.copy(step = OnboardingStep.LOCATION_PRIVACY, fieldErrors = locationErrors)
            }
            return
        }

        viewModelScope.launch {
            try {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null, success = false, photoUploadFailed = false) }

            var avatarPath = state.avatarPath
            val authUser = authRepository.getCurrentUser()
            if (authUser == null) {
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
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
                        isSubmitting = false,
                        photoUploadFailed = true,
                        errorMessage = FileUiErrorMapper.message(
                            com.comunidapp.app.domain.media.MediaDiagnostic.CROP
                        )
                    )
                }
                return@launch
            }

            val privacy = UserPrivacySettings(
                profileVisibility = state.profileVisibility,
                showLocation = state.showLocation,
                showPhone = state.showPhone,
                allowFriendRequests = state.allowFriendRequests
            )
            val command = CompleteOnboardingCommand(
                displayName = state.displayName.trim(),
                username = state.username.trim(),
                city = state.city.trim().ifBlank { null },
                province = state.province.trim().ifBlank { null },
                countryCode = state.countryCode.trim().ifBlank { "AR" },
                homeLocalityId = state.homeLocalityId?.trim()?.ifBlank { null },
                bio = state.bio.trim().ifBlank { null },
                avatarPath = avatarPath,
                privacy = privacy,
                birthDate = state.birthDate.takeIf { state.needsBirthDate }
            )
            // PERSON must exist before canon_register_media (FK owner_person_id → persons).
            val provisioned = userRepository.completeOnboarding(state.userId, command)
            if (provisioned.isFailure) {
                val error = provisioned.exceptionOrNull()
                val message = when (error?.message) {
                    "USERNAME_UNAVAILABLE" -> "Ese nombre de usuario no está disponible."
                    "DISPLAY_NAME_INVALID" -> "El nombre debe tener entre 2 y 80 caracteres."
                    "HOME_LOCALITY_REQUIRED" -> "Elegí una provincia y una localidad."
                    "BIRTH_DATE_INVALID", "UNDER_13_AUTONOMOUS_ACCOUNT_DENIED" ->
                        "Revisá la fecha de nacimiento."
                    else -> error?.message ?: "No se pudo completar el perfil"
                }
                _uiState.update {
                    it.copy(isSubmitting = false, errorMessage = message)
                }
                return@launch
            }

            var assetId = state.registeredAvatarAssetId?.takeIf { it.isNotBlank() }
            if (assetId == null && processedFile != null) {
                val uploadUri = android.net.Uri.fromFile(processedFile).toString()
                when (val upload = DataProvider.fileUploadCoordinator.startUpload(
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
                )) {
                    is AppResult.Success -> {
                        assetId = upload.data.assetId
                    }
                    is AppResult.Failure -> {
                        _uiState.update {
                            it.copy(
                                isSubmitting = false,
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
                    .onSuccess { avatarPath = assetId }
                    .onFailure { error ->
                        _uiState.update {
                            it.copy(
                                isSubmitting = false,
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

            _uiState.update {
                it.copy(
                    isSubmitting = false,
                    success = true,
                    avatarPath = avatarPath,
                    pendingImageUri = null
                )
            }
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        photoUploadFailed = true,
                        errorMessage = FileUiErrorMapper.message(
                            com.comunidapp.app.domain.media.MediaDiagnostic.fromThrowable(error)
                        )
                    )
                }
            }
        }
    }

    fun retryPhotoUpload() {
        completeOnboarding()
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
        completeOnboarding()
    }

    fun clearSuccess() {
        _uiState.update { it.copy(success = false) }
    }

    private suspend fun checkUsernameAvailability(raw: String) {
        val state = _uiState.value
        val userId = state.userId
        if (state.usernameLocked ||
            OnboardingCompleteness.isUnchangedSelfUsername(raw, state.username.takeIf { state.usernameLocked })
        ) {
            _uiState.update { it.copy(checkingUsername = false, usernameAvailable = true) }
            return
        }
        if (raw.isBlank()) {
            _uiState.update { it.copy(checkingUsername = false, usernameAvailable = null) }
            return
        }
        UsernameValidators.validate(raw).onFailure { ex ->
            val message = (ex as? UsernameValidationException)?.error?.userMessage
                ?: "Nombre de usuario inválido"
            _uiState.update {
                it.copy(
                    checkingUsername = false,
                    usernameAvailable = false,
                    fieldErrors = it.fieldErrors + ("username" to message)
                )
            }
            return
        }

        _uiState.update { it.copy(checkingUsername = true) }
        userRepository.isUsernameAvailable(raw, userId)
            .onSuccess { available ->
                _uiState.update {
                    it.copy(
                        checkingUsername = false,
                        usernameAvailable = available,
                        fieldErrors = if (available) {
                            it.fieldErrors - "username"
                        } else {
                            it.fieldErrors + ("username" to "Ese nombre de usuario no está disponible.")
                        }
                    )
                }
            }
            .onFailure {
                _uiState.update {
                    it.copy(
                        checkingUsername = false,
                        usernameAvailable = null,
                        errorMessage = "No se pudo verificar el usuario. Intentá de nuevo."
                    )
                }
            }
    }

    private fun validateIdentityStep(state: ProfileOnboardingUiState): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        val display = state.displayName.trim()
        if (display.length !in DISPLAY_NAME_MIN..DISPLAY_NAME_MAX) {
            errors["displayName"] = "El nombre debe tener entre $DISPLAY_NAME_MIN y $DISPLAY_NAME_MAX caracteres."
        }
        if (state.usernameLocked) {
            return errors
        }
        UsernameValidators.validate(state.username).onFailure { ex ->
            val message = (ex as? UsernameValidationException)?.error?.userMessage
                ?: "Nombre de usuario inválido"
            errors["username"] = message
        }
        if (!errors.containsKey("username")) {
            when (state.usernameAvailable) {
                false -> errors["username"] = "Ese nombre de usuario no está disponible."
                null -> {
                    if (state.checkingUsername) {
                        errors["username"] = "Esperá a que termine la verificación del usuario."
                    } else {
                        errors["username"] = "Verificá la disponibilidad del usuario."
                    }
                }
                true -> Unit
            }
        }
        if (state.needsBirthDate) {
            com.comunidapp.app.domain.user.PersonAgeRules.validateSignupBirthDate(state.birthDate)
                .onFailure { err ->
                    errors["birthDate"] = when (err.message) {
                        "UNDER_13_AUTONOMOUS_ACCOUNT_DENIED" ->
                            "LeoVer no crea cuentas autónomas para menores de 13 años."
                        "BIRTH_DATE_IN_FUTURE" -> "La fecha de nacimiento no puede ser futura."
                        else -> "Ingresá tu fecha de nacimiento."
                    }
                }
        }
        return errors
    }

    private fun validateLocationStep(state: ProfileOnboardingUiState): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        val hasCatalogLocality = !state.homeLocalityId.isNullOrBlank()
        val hasNamedPlace = state.province.isNotBlank() && state.city.isNotBlank()
        if (!hasCatalogLocality && !hasNamedPlace) {
            errors["province"] = "Elegí una provincia y una localidad."
        }
        val code = state.countryCode.trim().ifBlank { "AR" }
        if (!COUNTRY_CODE_REGEX.matches(code)) {
            errors["countryCode"] = "Usá un código de país de 2 letras (ej. AR)."
        }
        return errors
    }

    companion object {
        private const val USERNAME_DEBOUNCE_MS = 400L
        private const val DISPLAY_NAME_MIN = 2
        private const val DISPLAY_NAME_MAX = 80
        private const val COUNTRY_CODE_LENGTH = 2
        private val COUNTRY_CODE_REGEX = Regex("^[A-Z]{2}$")
    }
}
