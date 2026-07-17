package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.mock.MockAuthDatabase
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.domain.auth.AuthAnalytics
import com.comunidapp.app.domain.auth.AuthErrorCode
import com.comunidapp.app.domain.auth.AuthErrorMapper
import com.comunidapp.app.domain.auth.AuthException
import com.comunidapp.app.domain.auth.ConsentMetadata
import com.comunidapp.app.domain.auth.LegalDocumentConfig
import com.comunidapp.app.domain.auth.SignInCommand
import com.comunidapp.app.domain.auth.SignUpCommand
import com.comunidapp.app.domain.auth.validation.AuthValidationException
import com.comunidapp.app.domain.auth.validation.AuthValidators
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
        if (_uiState.value.isLoading) return
        AuthAnalytics.track("login_started")
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, errorMessage = null, needsEmailVerification = null)
            }
            val command = SignInCommand(_uiState.value.email, _uiState.value.password)
            AuthValidators.validateEmail(command.email).getOrElse { err ->
                AuthAnalytics.track("auth_error_shown")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = userMessage(err)
                    )
                }
                return@launch
            }
            if (command.password.isEmpty()) {
                AuthAnalytics.track("auth_error_shown")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = AuthErrorMapper.fromCode(
                            AuthErrorCode.INVALID_CREDENTIALS,
                            "empty password"
                        ).userMessage
                    )
                }
                return@launch
            }
            authRepository.login(command.email, command.password)
                .onSuccess {
                    AuthAnalytics.track("login_completed")
                    _uiState.update { state -> state.copy(isLoading = false, isLoggedIn = true) }
                }
                .onFailure { error ->
                    AuthAnalytics.track("auth_error_shown")
                    // M07: security event without email/password (ViewModel layer, not AuthRepository).
                    com.comunidapp.app.domain.observability.ObservabilityInstrumentation.reportLoginFailure()
                    val appError = AuthErrorMapper.fromThrowable(error)
                    if (appError.code == AuthErrorCode.EMAIL_NOT_VERIFIED.name) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                needsEmailVerification = AuthValidators.normalizeEmail(command.email),
                                errorMessage = null
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(isLoading = false, errorMessage = appError.userMessage)
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
    val acceptedTerms: Boolean = false,
    val acceptedPrivacy: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val registeredEmail: String? = null
)

class RegisterViewModel(
    private val authRepository: AuthRepository = AuthProvider.repository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun onNameChange(name: String) {
        _uiState.update { it.copy(name = name, errorMessage = null, fieldErrors = it.fieldErrors - "name") }
    }

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(email = email, errorMessage = null, fieldErrors = it.fieldErrors - "email") }
    }

    fun onPasswordChange(password: String) {
        _uiState.update {
            it.copy(password = password, errorMessage = null, fieldErrors = it.fieldErrors - "password")
        }
    }

    fun onConfirmPasswordChange(confirmPassword: String) {
        _uiState.update {
            it.copy(
                confirmPassword = confirmPassword,
                errorMessage = null,
                fieldErrors = it.fieldErrors - "confirmPassword"
            )
        }
    }

    fun onAcceptedTermsChange(accepted: Boolean) {
        _uiState.update {
            it.copy(acceptedTerms = accepted, errorMessage = null, fieldErrors = it.fieldErrors - "terms")
        }
    }

    fun onAcceptedPrivacyChange(accepted: Boolean) {
        _uiState.update {
            it.copy(acceptedPrivacy = accepted, errorMessage = null, fieldErrors = it.fieldErrors - "privacy")
        }
    }

    fun register() {
        val state = _uiState.value
        if (state.isLoading) return
        AuthAnalytics.track("signup_started")

        LegalDocumentConfig.requireUsableForAuth().getOrElse { err ->
            AuthAnalytics.track("auth_error_shown")
            _uiState.update { it.copy(errorMessage = userMessage(err)) }
            return
        }

        val command = SignUpCommand(
            name = state.name,
            email = state.email,
            password = state.password,
            confirmPassword = state.confirmPassword,
            acceptedTerms = state.acceptedTerms,
            acceptedPrivacy = state.acceptedPrivacy,
            termsVersion = LegalDocumentConfig.terms.version,
            privacyVersion = LegalDocumentConfig.privacy.version
        )

        val fieldErrors = mutableMapOf<String, String>()
        if (command.name.isBlank()) {
            fieldErrors["name"] = "Ingresá tu nombre."
        }
        AuthValidators.validateEmail(command.email).onFailure { err ->
            fieldErrors["email"] = userMessage(err)
        }
        AuthValidators.validatePasswordConfirmation(command.password, command.confirmPassword)
            .onFailure { err ->
                fieldErrors["password"] = userMessage(err)
            }
        AuthValidators.validateConsents(
            command.acceptedTerms,
            command.acceptedPrivacy,
            command.termsVersion,
            command.privacyVersion
        ).onFailure { err ->
            fieldErrors["terms"] = userMessage(err)
        }

        if (fieldErrors.isNotEmpty()) {
            AuthAnalytics.track("signup_validation_failed")
            _uiState.update {
                it.copy(fieldErrors = fieldErrors, errorMessage = fieldErrors.values.firstOrNull())
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, fieldErrors = emptyMap()) }
            val consent = ConsentMetadata.forRegistration()
            authRepository.register(
                name = command.name,
                email = command.email,
                password = command.password,
                consent = consent
            )
                .onSuccess {
                    AuthAnalytics.track("signup_completed")
                    _uiState.update { s ->
                        s.copy(
                            isLoading = false,
                            registeredEmail = AuthValidators.normalizeEmail(command.email)
                        )
                    }
                }
                .onFailure { error ->
                    AuthAnalytics.track("auth_error_shown")
                    val appError = AuthErrorMapper.fromThrowable(error)
                    val alreadyRegistered =
                        appError.code == AuthErrorCode.EMAIL_ALREADY_REGISTERED.name
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            // Respuesta genérica / dirigir a verificación sin enumerar
                            errorMessage = if (alreadyRegistered) null else appError.userMessage,
                            registeredEmail = if (alreadyRegistered) {
                                AuthValidators.normalizeEmail(command.email)
                            } else {
                                null
                            }
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
        if (_uiState.value.isLoading) return
        AuthAnalytics.track("password_recovery_requested")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            AuthValidators.validateEmail(_uiState.value.email).getOrElse { err ->
                _uiState.update { it.copy(isLoading = false, errorMessage = userMessage(err)) }
                return@launch
            }
            authRepository.sendPasswordResetEmail(_uiState.value.email)
                .onSuccess {
                    // Respuesta genérica (anti-enumeración). Mock token solo en mock para demos.
                    val mockToken = if (!AuthProvider.isRemoteBackendEnabled) {
                        MockAuthDatabase.findByEmail(_uiState.value.email)?.resetToken
                    } else {
                        null
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            emailSent = true,
                            mockToken = mockToken,
                            token = mockToken.orEmpty()
                        )
                    }
                }
                .onFailure { error ->
                    AuthAnalytics.track("auth_error_shown")
                    // Genérico también en fallos de red conocibles
                    val appError = AuthErrorMapper.fromThrowable(error)
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = appError.userMessage)
                    }
                }
        }
    }

    fun resetPassword() {
        val state = _uiState.value
        if (state.isLoading) return
        if (state.newPassword != state.confirmPassword) {
            _uiState.update { it.copy(errorMessage = "Las contraseñas no coinciden.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = if (AuthProvider.isRemoteBackendEnabled) {
                authRepository.updatePasswordFromRecovery(state.newPassword)
            } else {
                authRepository.resetPassword(state.email, state.token, state.newPassword)
            }
            result
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, resetSuccess = true) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = userMessage(error))
                    }
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

    fun checkVerification(email: String) {
        viewModelScope.launch {
            val verified = authRepository.isEmailVerified(email)
            _uiState.update { it.copy(isVerified = verified) }
        }
    }

    fun resendVerification(email: String) {
        if (_uiState.value.isLoading || _uiState.value.resendCooldownSeconds > 0) return
        AuthAnalytics.track("email_verification_requested")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
            authRepository.sendEmailVerification(email)
                .onSuccess {
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
                    AuthAnalytics.track("auth_error_shown")
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = userMessage(error))
                    }
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

    fun confirmWithOtp(email: String, otpCode: String) {
        if (_uiState.value.isLoading) return
        if (otpCode.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Ingresá el código de 6 dígitos") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
            authRepository.verifyEmailOtp(email, otpCode)
                .onSuccess {
                    AuthAnalytics.track("email_verification_completed")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isVerified = true,
                            successMessage = "Email confirmado. Ya podés iniciar sesión."
                        )
                    }
                }
                .onFailure { error ->
                    AuthAnalytics.track("auth_error_shown")
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = userMessage(error))
                    }
                }
        }
    }

    fun confirmVerification(email: String) {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
            authRepository.confirmEmailVerification(email)
                .onSuccess {
                    AuthAnalytics.track("email_verification_completed")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isVerified = true,
                            successMessage = "Email confirmado correctamente"
                        )
                    }
                }
                .onFailure { error ->
                    AuthAnalytics.track("auth_error_shown")
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = userMessage(error))
                    }
                }
        }
    }

    companion object {
        const val RESEND_COOLDOWN_SECONDS = 60
    }
}

private fun userMessage(error: Throwable): String = when (error) {
    is AuthValidationException -> error.error.userMessage
    is AuthException -> error.authError.userMessage
    else -> AuthErrorMapper.fromThrowable(error).userMessage
}
