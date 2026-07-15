package com.comunidapp.app.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.remote.storage.StoragePaths
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.UserRepository
import com.comunidapp.app.domain.user.CompleteOnboardingCommand
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
    val usernameAvailable: Boolean? = null,
    val checkingUsername: Boolean = false,
    val city: String = "",
    val province: String = "",
    val countryCode: String = "",
    val profileVisibility: ProfileVisibility = ProfileVisibility.PRIVATE,
    val showLocation: Boolean = true,
    val showPhone: Boolean = false,
    val allowFriendRequests: Boolean = true,
    val bio: String = "",
    val pendingImageUri: Uri? = null,
    val avatarPath: String? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val errorMessage: String? = null,
    val isSubmitting: Boolean = false,
    val success: Boolean = false
)

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
            _uiState.update {
                ProfileOnboardingUiState(
                    isLoading = false,
                    userId = authUser.id,
                    displayName = profile?.displayName?.takeIf { it.isNotBlank() }
                        ?: profile?.name.orEmpty(),
                    username = profile?.username.orEmpty(),
                    city = profile?.city.orEmpty(),
                    province = profile?.province.orEmpty(),
                    countryCode = profile?.countryCode.orEmpty(),
                    bio = profile?.bio.orEmpty(),
                    avatarPath = profile?.avatarPath
                )
            }
            if (profile?.username.isNullOrBlank().not()) {
                usernameQuery.value = profile?.username.orEmpty()
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
        _uiState.update { it.copy(pendingImageUri = uri, errorMessage = null) }
    }

    fun goBack() {
        val previous = when (_uiState.value.step) {
            OnboardingStep.IDENTITY -> return
            OnboardingStep.LOCATION_PRIVACY -> OnboardingStep.IDENTITY
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
                it.copy(step = OnboardingStep.IDENTITY, fieldErrors = identityErrors)
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null, success = false) }

            var avatarPath = state.avatarPath
            state.pendingImageUri?.let { uri ->
                val avatarStorage = DataProvider.profileAvatarStorage
                if (avatarStorage != null) {
                    avatarStorage.uploadAvatar(state.userId, uri)
                        .onSuccess { avatarPath = it }
                        .onFailure { error ->
                            _uiState.update {
                                it.copy(
                                    isSubmitting = false,
                                    errorMessage = error.message ?: "No se pudo subir la foto"
                                )
                            }
                            return@launch
                        }
                } else {
                    // Mock / sin storage: avatar opcional; no bloquea completar.
                    avatarPath = StoragePaths.userAvatar(state.userId)
                }
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
                countryCode = state.countryCode.trim().ifBlank { null },
                bio = state.bio.trim().ifBlank { null },
                avatarPath = avatarPath,
                privacy = privacy
            )

            userRepository.completeOnboarding(state.userId, command)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            success = true,
                            avatarPath = avatarPath,
                            pendingImageUri = null
                        )
                    }
                }
                .onFailure { error ->
                    val message = when (error.message) {
                        "USERNAME_UNAVAILABLE" -> "Ese nombre de usuario no está disponible."
                        "DISPLAY_NAME_INVALID" -> "El nombre debe tener entre 2 y 80 caracteres."
                        "AVATAR_PATH_INVALID" -> "No se pudo guardar la foto de perfil."
                        else -> error.message ?: "No se pudo completar el perfil"
                    }
                    _uiState.update {
                        it.copy(isSubmitting = false, errorMessage = message)
                    }
                }
        }
    }

    fun clearSuccess() {
        _uiState.update { it.copy(success = false) }
    }

    private suspend fun checkUsernameAvailability(raw: String) {
        val userId = _uiState.value.userId
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
        return errors
    }

    private fun validateLocationStep(state: ProfileOnboardingUiState): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        val code = state.countryCode.trim()
        if (code.isNotEmpty() && !COUNTRY_CODE_REGEX.matches(code)) {
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
