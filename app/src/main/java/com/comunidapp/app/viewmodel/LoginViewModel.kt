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
import com.comunidapp.app.domain.auth.LoginIdentifier
import com.comunidapp.app.domain.auth.LegalDocumentConfig
import com.comunidapp.app.domain.auth.SignInCommand
import com.comunidapp.app.domain.auth.SignUpCommand
import com.comunidapp.app.domain.auth.validation.AuthValidationException
import com.comunidapp.app.domain.auth.validation.AuthValidators
import com.comunidapp.app.domain.auth.validation.EmailOtpValidators
import com.comunidapp.app.domain.user.UsernameErrorCode
import com.comunidapp.app.domain.user.UsernameValidationException
import com.comunidapp.app.domain.user.UsernameValidators
import android.os.SystemClock
import com.comunidapp.app.domain.auth.GoogleAuthLifecycle
import com.comunidapp.app.domain.auth.GoogleAuthResumeGuard
import com.comunidapp.app.domain.auth.GoogleAuthTrace
import com.comunidapp.app.domain.auth.GoogleOAuthPending
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val isGoogleLoading: Boolean = false,
    val errorMessage: String? = null,
    val isLoggedIn: Boolean = false,
    val needsEmailVerification: String? = null,
    val googleLifecycle: GoogleAuthLifecycle = GoogleAuthLifecycle.IDLE
) {
    val isBusy: Boolean get() = isLoading || isGoogleLoading
}

class LoginViewModel(
    private val authRepository: AuthRepository = AuthProvider.repository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()
    private var googleWaitJob: Job? = null
    private var googleLaunchAtElapsedMs: Long = 0L
    private var googleHostPausedSinceLaunch: Boolean = false
    private var googleLaunchAuthUserId: String? = null

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(email = email, errorMessage = null) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, errorMessage = null) }
    }

    fun login() {
        if (_uiState.value.isBusy) return
        AuthAnalytics.track("login_started")
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, errorMessage = null, needsEmailVerification = null)
            }
            val identifier = _uiState.value.email.trim()
            val password = _uiState.value.password
            if (identifier.isEmpty() || password.isEmpty()) {
                showGenericLoginError()
                return@launch
            }
            if (!LoginIdentifier.looksLikeEmail(identifier)) {
                val usernameResult = authRepository.loginWithUsername(identifier, password)
                val user = usernameResult.getOrNull()
                if (user != null) {
                    AuthAnalytics.track("login_completed")
                    if (isAdministrativeIdentity(user.id)) {
                        // SessionViewModel observes auth and routes ADMIN_SESSION.
                        _uiState.update { it.copy(isLoading = false) }
                    } else {
                        _uiState.update { it.copy(isLoading = false, isLoggedIn = true) }
                    }
                } else {
                    val error = usernameResult.exceptionOrNull()
                    com.comunidapp.app.domain.observability.ObservabilityInstrumentation.reportLoginFailure()
                    val appError = error?.let { AuthErrorMapper.fromThrowable(it) }
                    if (appError?.code == AuthErrorCode.EMAIL_NOT_VERIFIED.name) {
                        val maybeEmail = appError.technicalMessage
                        if (LoginIdentifier.looksLikeEmail(maybeEmail)) {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    needsEmailVerification = AuthValidators.normalizeEmail(maybeEmail),
                                    errorMessage = null
                                )
                            }
                            return@launch
                        }
                    }
                    showGenericLoginError()
                }
                return@launch
            }
            val command = SignInCommand(identifier, password)
            AuthValidators.validateEmail(command.email).getOrElse {
                showGenericLoginError()
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
                            it.copy(isLoading = false, errorMessage = GENERIC_LOGIN_ERROR)
                        }
                    }
                }
        }
    }

    fun signInWithGoogle(activity: android.app.Activity) {
        if (_uiState.value.isBusy) return
        AuthAnalytics.track("google_login_started")
        googleWaitJob?.cancel()
        googleLaunchAtElapsedMs = SystemClock.elapsedRealtime()
        googleHostPausedSinceLaunch = false
        googleLaunchAuthUserId = authRepository.getCurrentUser()?.id
        GoogleAuthTrace.event("GOOGLE-TAP")
        _uiState.update {
            it.copy(
                isGoogleLoading = true,
                errorMessage = null,
                needsEmailVerification = null,
                googleLifecycle = GoogleAuthLifecycle.LAUNCHING_OAUTH
            )
        }
        googleWaitJob = viewModelScope.launch {
            val url = authRepository.createGoogleOAuthUrl().getOrElse { error ->
                failGoogleLogin(error)
                return@launch
            }
            if (url.isBlank()) {
                GoogleAuthTrace.event("GOOGLE-ERROR=BAD_URL")
                failGoogleLogin(IllegalStateException("google oauth url missing"))
                return@launch
            }
            GoogleAuthTrace.event("GOOGLE-AUTH-URL-CREATED=YES")
            val handler = com.comunidapp.app.domain.auth.LeoVerGoogleSignIn.openAuthorizeUrl(activity, url)
            if (handler == null) {
                _uiState.update {
                    it.copy(
                        isGoogleLoading = false,
                        errorMessage = "No encontramos un navegador para continuar con Google.",
                        googleLifecycle = GoogleAuthLifecycle.ERROR
                    )
                }
                return@launch
            }
            _uiState.update { it.copy(googleLifecycle = GoogleAuthLifecycle.WAITING_EXTERNAL_AUTH) }
            val previousId = googleLaunchAuthUserId
            val user = authRepository.observeAuthState().first { candidate ->
                isFreshGoogleUser(candidate, previousId)
            }
            if (user != null) completeGoogleSuccess(user)
        }
    }

    private fun isFreshGoogleUser(
        candidate: com.comunidapp.app.data.model.User?,
        previousId: String?
    ): Boolean {
        val id = candidate?.id ?: return false
        if (GoogleOAuthPending.isPendingUserId(id)) return false
        return previousId.isNullOrBlank() || id != previousId
    }

    private fun failGoogleLogin(error: Throwable) {
        AuthAnalytics.track("auth_error_shown")
        val appError = AuthErrorMapper.fromThrowable(error)
        val cancelled = appError.code == AuthErrorCode.GOOGLE_AUTH_CANCELLED.name
        val mapped = runCatching { AuthErrorCode.valueOf(appError.code.orEmpty()) }.getOrNull()
        GoogleAuthTrace.error(GoogleAuthTrace.errorType(mapped))
        _uiState.update {
            it.copy(
                isGoogleLoading = false,
                errorMessage = if (cancelled) null else appError.userMessage,
                googleLifecycle = if (cancelled) {
                    GoogleAuthLifecycle.CANCELLED
                } else {
                    GoogleAuthLifecycle.ERROR
                }
            )
        }
    }

    fun onHostPaused() {
        val phase = _uiState.value.googleLifecycle
        if (phase == GoogleAuthLifecycle.LAUNCHING_OAUTH ||
            phase == GoogleAuthLifecycle.WAITING_EXTERNAL_AUTH
        ) {
            googleHostPausedSinceLaunch = true
        }
    }

    fun onHostResumed() {
        if (_uiState.value.googleLifecycle != GoogleAuthLifecycle.WAITING_EXTERNAL_AUTH) return
        if (GoogleAuthResumeGuard.shouldIgnoreResume(googleLaunchAtElapsedMs, googleHostPausedSinceLaunch)) {
            return
        }
        viewModelScope.launch {
            delay(400)
            if (_uiState.value.googleLifecycle != GoogleAuthLifecycle.WAITING_EXTERNAL_AUTH) return@launch
            val user = authRepository.getCurrentUser()
            if (isFreshGoogleUser(user, googleLaunchAuthUserId)) {
                googleWaitJob?.cancel()
                completeGoogleSuccess(user!!)
            }
        }
    }

    private fun completeGoogleSuccess(user: com.comunidapp.app.data.model.User) {
        if (GoogleOAuthPending.isPendingUserId(user.id)) return
        AuthAnalytics.track("google_login_completed")
        GoogleAuthTrace.event("GOOGLE-SUCCESS")
        _uiState.update { state ->
            state.copy(
                isGoogleLoading = false,
                isLoggedIn = true,
                errorMessage = null,
                googleLifecycle = GoogleAuthLifecycle.SUCCESS
            )
        }
    }

    fun clearLoginState() {
        _uiState.update { LoginUiState() }
    }

    fun clearEmailVerificationRedirect() {
        _uiState.update { it.copy(needsEmailVerification = null) }
    }

    private suspend fun isAdministrativeIdentity(userId: String): Boolean {
        if (com.comunidapp.app.data.repository.MockAdministrativeIdentityStore.sessionFor(userId) != null) {
            return true
        }
        if (authRepository is com.comunidapp.app.data.repository.MockAuthRepository) {
            return false
        }
        return runCatching {
            com.comunidapp.app.data.provider.DataProvider.adminSessionRepository
                .authState(userId)?.isAdminIdentity == true
        }.getOrDefault(false)
    }

    private fun showGenericLoginError() {
        AuthAnalytics.track("auth_error_shown")
        _uiState.update {
            it.copy(isLoading = false, errorMessage = GENERIC_LOGIN_ERROR)
        }
    }

    companion object {
        const val GENERIC_LOGIN_ERROR = "Usuario o contraseña incorrectos."
    }
}

enum class UsernameAvailabilityUi {
    IDLE,
    CHECKING,
    AVAILABLE,
    TAKEN,
    RESERVED,
    INVALID,
    ERROR
}

data class RegisterUiState(
    val firstName: String = "",
    val lastName: String = "",
    val username: String = "",
    val usernameNormalized: String = "",
    val usernameAvailability: UsernameAvailabilityUi = UsernameAvailabilityUi.IDLE,
    val birthDate: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val acceptedTerms: Boolean = false,
    val acceptedPrivacy: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val registeredEmail: String? = null,
    val googleAuthenticated: Boolean = false,
    val offerResendConfirmation: Boolean = false,
    val qaDiagnostic: String? = null,
    val googleLifecycle: GoogleAuthLifecycle = GoogleAuthLifecycle.IDLE,
    val errorTitle: String? = null,
    val emailAlreadyRegistered: Boolean = false
) {
    val name: String
        get() = listOf(firstName.trim(), lastName.trim()).filter { it.isNotEmpty() }.joinToString(" ")

    val canSubmit: Boolean
        get() = !isLoading &&
            !googleAuthenticated &&
            firstName.isNotBlank() &&
            lastName.isNotBlank() &&
            usernameNormalized.length >= UsernameValidators.MIN_LENGTH &&
            usernameAvailability != UsernameAvailabilityUi.TAKEN &&
            usernameAvailability != UsernameAvailabilityUi.RESERVED &&
            usernameAvailability != UsernameAvailabilityUi.INVALID &&
            birthDate.isNotBlank() &&
            email.isNotBlank() &&
            password.isNotBlank() &&
            confirmPassword.isNotBlank() &&
            acceptedTerms &&
            acceptedPrivacy &&
            fieldErrors.isEmpty()
}

@OptIn(kotlinx.coroutines.FlowPreview::class)
class RegisterViewModel(
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val userRepository: com.comunidapp.app.data.repository.UserRepository =
        com.comunidapp.app.data.provider.DataProvider.userRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()
    private var googleWaitJob: Job? = null
    private var googleLaunchAtElapsedMs: Long = 0L
    private var googleHostPausedSinceLaunch: Boolean = false
    private var googleLaunchAuthUserId: String? = null

    private val usernameQuery = MutableStateFlow("")
    private var availabilityToken = 0L

    init {
        viewModelScope.launch {
            usernameQuery
                .debounce(400)
                .distinctUntilChanged()
                .collect { raw -> checkUsername(raw) }
        }
    }

    fun onFirstNameChange(value: String) {
        _uiState.update {
            it.copy(firstName = value, errorMessage = null, fieldErrors = it.fieldErrors - "name")
        }
    }

    fun onLastNameChange(value: String) {
        _uiState.update {
            it.copy(lastName = value, errorMessage = null, fieldErrors = it.fieldErrors - "name")
        }
    }

    @Deprecated("Usar onFirstNameChange / onLastNameChange", ReplaceWith("onFirstNameChange(name)"))
    fun onNameChange(name: String) = onFirstNameChange(name)

    fun onUsernameChange(raw: String) {
        val normalized = UsernameValidators.normalize(raw)
        _uiState.update {
            it.copy(
                username = raw,
                usernameNormalized = normalized,
                usernameAvailability = if (normalized.isBlank()) {
                    UsernameAvailabilityUi.IDLE
                } else {
                    UsernameAvailabilityUi.CHECKING
                },
                fieldErrors = it.fieldErrors - "username",
                errorMessage = null
            )
        }
        usernameQuery.value = raw
    }

    fun onBirthDateChange(isoDate: String) {
        _uiState.update {
            it.copy(birthDate = isoDate, errorMessage = null, fieldErrors = it.fieldErrors - "birthDate")
        }
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

    private suspend fun checkUsername(raw: String) {
        val token = ++availabilityToken
        val normalized = UsernameValidators.normalize(raw)
        if (normalized.isBlank()) {
            if (token == availabilityToken) {
                _uiState.update {
                    it.copy(usernameAvailability = UsernameAvailabilityUi.IDLE, usernameNormalized = "")
                }
            }
            return
        }
        val validation = UsernameValidators.validate(raw)
        if (validation.isFailure) {
            val code = (validation.exceptionOrNull() as? UsernameValidationException)?.error?.code
            if (token != availabilityToken) return
            _uiState.update {
                it.copy(
                    usernameNormalized = normalized,
                    usernameAvailability = when (code) {
                        UsernameErrorCode.RESERVED.name -> UsernameAvailabilityUi.RESERVED
                        else -> UsernameAvailabilityUi.INVALID
                    },
                    fieldErrors = it.fieldErrors + (
                        "username" to (
                            (validation.exceptionOrNull() as? UsernameValidationException)
                                ?.error?.userMessage
                                ?: UsernameValidators.userMessage(UsernameErrorCode.INVALID_CHARS)
                            )
                        )
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                usernameNormalized = normalized,
                usernameAvailability = UsernameAvailabilityUi.CHECKING,
                fieldErrors = it.fieldErrors - "username"
            )
        }
        val available = userRepository.isUsernameAvailable(normalized).getOrElse {
            if (token != availabilityToken) return
            _uiState.update {
                it.copy(
                    usernameAvailability = UsernameAvailabilityUi.ERROR,
                    fieldErrors = it.fieldErrors + (
                        "username" to "No pudimos comprobar la disponibilidad. Intentá nuevamente."
                        )
                )
            }
            return
        }
        if (token != availabilityToken) return
        _uiState.update {
            it.copy(
                usernameAvailability = if (available) {
                    UsernameAvailabilityUi.AVAILABLE
                } else {
                    UsernameAvailabilityUi.TAKEN
                },
                fieldErrors = if (available) {
                    it.fieldErrors - "username"
                } else {
                    it.fieldErrors + ("username" to "Este nombre ya está en uso.")
                }
            )
        }
    }

    fun register() {
        val state = _uiState.value
        if (state.isLoading || state.googleAuthenticated || !state.canSubmit) return
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
            username = state.usernameNormalized,
            birthDate = state.birthDate,
            acceptedTerms = state.acceptedTerms,
            acceptedPrivacy = state.acceptedPrivacy,
            termsVersion = LegalDocumentConfig.terms.version,
            privacyVersion = LegalDocumentConfig.privacy.version
        )

        val fieldErrors = mutableMapOf<String, String>()
        if (command.name.isBlank()) {
            fieldErrors["name"] = "Ingresá tu nombre y apellido."
        }
        UsernameValidators.validate(command.username).onFailure { err ->
            fieldErrors["username"] = (err as? UsernameValidationException)?.error?.userMessage
                ?: "Nombre de usuario inválido."
        }
        com.comunidapp.app.domain.user.PersonAgeRules.validateSignupBirthDate(command.birthDate)
            .onFailure { err ->
                fieldErrors["birthDate"] = when (err.message) {
                    "UNDER_13_AUTONOMOUS_ACCOUNT_DENIED" ->
                        "LeoVer no crea cuentas autónomas para menores de 13 años."
                    "BIRTH_DATE_IN_FUTURE" -> "La fecha de nacimiento no puede ser futura."
                    else -> "Ingresá tu fecha de nacimiento."
                }
            }
        if (state.usernameAvailability == UsernameAvailabilityUi.TAKEN ||
            state.usernameAvailability == UsernameAvailabilityUi.RESERVED ||
            state.usernameAvailability == UsernameAvailabilityUi.INVALID
        ) {
            fieldErrors["username"] = fieldErrors["username"]
                ?: "Comprobá el nombre de usuario."
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
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    errorTitle = null,
                    emailAlreadyRegistered = false,
                    fieldErrors = emptyMap()
                )
            }
            val consent = ConsentMetadata.forRegistration()
            authRepository.register(
                name = command.name,
                email = command.email,
                password = command.password,
                consent = consent,
                username = command.username,
                birthDate = command.birthDate
            )
                .onSuccess {
                    AuthAnalytics.track("signup_completed")
                    _uiState.update { s ->
                        s.copy(
                            isLoading = false,
                            registeredEmail = AuthValidators.normalizeEmail(command.email),
                            offerResendConfirmation = false,
                            qaDiagnostic = null
                        )
                    }
                }
                .onFailure { error ->
                    AuthAnalytics.track("auth_error_shown")
                    val appError = AuthErrorMapper.fromThrowable(error)
                    val alreadyRegistered =
                        appError.code == AuthErrorCode.EMAIL_ALREADY_REGISTERED.name
                    val usernameConflict =
                        appError.technicalMessage.contains("USERNAME", ignoreCase = true) ||
                            error.message.orEmpty().contains("USERNAME", ignoreCase = true)
                    val qa = com.comunidapp.app.domain.auth.AuthSignupDiagnostic.debugDetail(
                        AuthErrorCode.entries.firstOrNull { it.name == appError.code }
                            ?: AuthErrorCode.UNKNOWN_AUTH_ERROR,
                        appError.technicalMessage,
                        com.comunidapp.app.BuildConfig.DEBUG
                    )
                    if (!qa.isNullOrBlank()) {
                        com.comunidapp.app.core.logging.AppLog.info("AuthSignup", "diagnostic=$qa")
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorTitle = if (alreadyRegistered) {
                                "Este correo ya está registrado"
                            } else {
                                null
                            },
                            errorMessage = when {
                                alreadyRegistered ->
                                    "Ya existe una cuenta asociada a este correo. Si te registraste con Google, continuá con Google para ingresar."
                                usernameConflict ->
                                    "Este nombre acaba de ser utilizado. Elegí otro."
                                else -> appError.userMessage
                            },
                            emailAlreadyRegistered = alreadyRegistered,
                            qaDiagnostic = null,
                            offerResendConfirmation = false,
                            usernameAvailability = if (usernameConflict) {
                                UsernameAvailabilityUi.TAKEN
                            } else {
                                it.usernameAvailability
                            },
                            fieldErrors = if (usernameConflict) {
                                it.fieldErrors + (
                                    "username" to "Este nombre acaba de ser utilizado. Elegí otro."
                                    )
                            } else {
                                it.fieldErrors
                            },
                            registeredEmail = null
                        )
                    }
                }
        }
    }

    fun signInWithGoogle(activity: android.app.Activity) {
        val state = _uiState.value
        if (state.isLoading || state.googleAuthenticated) return
        AuthAnalytics.track("google_signup_started")
        googleWaitJob?.cancel()
        googleLaunchAtElapsedMs = SystemClock.elapsedRealtime()
        googleHostPausedSinceLaunch = false
        googleLaunchAuthUserId = authRepository.getCurrentUser()?.id
        GoogleAuthTrace.event("GOOGLE-TAP")
        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                fieldErrors = emptyMap(),
                googleLifecycle = GoogleAuthLifecycle.LAUNCHING_OAUTH
            )
        }
        googleWaitJob = viewModelScope.launch {
            val url = authRepository.createGoogleOAuthUrl().getOrElse { error ->
                failGoogleSignup(error)
                return@launch
            }
            if (url.isBlank()) {
                GoogleAuthTrace.event("GOOGLE-ERROR=BAD_URL")
                failGoogleSignup(IllegalStateException("google oauth url missing"))
                return@launch
            }
            GoogleAuthTrace.event("GOOGLE-AUTH-URL-CREATED=YES")
            val handler = com.comunidapp.app.domain.auth.LeoVerGoogleSignIn.openAuthorizeUrl(activity, url)
            if (handler == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "No encontramos un navegador para continuar con Google.",
                        googleLifecycle = GoogleAuthLifecycle.ERROR
                    )
                }
                return@launch
            }
            _uiState.update { it.copy(googleLifecycle = GoogleAuthLifecycle.WAITING_EXTERNAL_AUTH) }
            val previousId = googleLaunchAuthUserId
            val user = authRepository.observeAuthState().first { candidate ->
                isFreshGoogleUser(candidate, previousId)
            }
            if (user != null) completeGoogleSignupSuccess()
        }
    }

    private fun isFreshGoogleUser(
        candidate: com.comunidapp.app.data.model.User?,
        previousId: String?
    ): Boolean {
        val id = candidate?.id ?: return false
        if (GoogleOAuthPending.isPendingUserId(id)) return false
        return previousId.isNullOrBlank() || id != previousId
    }

    private fun failGoogleSignup(error: Throwable) {
        AuthAnalytics.track("auth_error_shown")
        val appError = AuthErrorMapper.fromThrowable(error)
        val cancelled = appError.code == AuthErrorCode.GOOGLE_AUTH_CANCELLED.name
        val mapped = runCatching { AuthErrorCode.valueOf(appError.code.orEmpty()) }.getOrNull()
        GoogleAuthTrace.error(GoogleAuthTrace.errorType(mapped))
        _uiState.update {
            it.copy(
                isLoading = false,
                errorMessage = if (cancelled) {
                    null
                } else if (appError.code == AuthErrorCode.NETWORK_UNAVAILABLE.name) {
                    "Revisá tu conexión e intentá nuevamente."
                } else {
                    appError.userMessage
                },
                googleLifecycle = if (cancelled) {
                    GoogleAuthLifecycle.CANCELLED
                } else {
                    GoogleAuthLifecycle.ERROR
                }
            )
        }
    }

    fun onHostPaused() {
        val phase = _uiState.value.googleLifecycle
        if (phase == GoogleAuthLifecycle.LAUNCHING_OAUTH ||
            phase == GoogleAuthLifecycle.WAITING_EXTERNAL_AUTH
        ) {
            googleHostPausedSinceLaunch = true
        }
    }

    fun onHostResumed() {
        if (_uiState.value.googleLifecycle != GoogleAuthLifecycle.WAITING_EXTERNAL_AUTH) return
        if (GoogleAuthResumeGuard.shouldIgnoreResume(googleLaunchAtElapsedMs, googleHostPausedSinceLaunch)) {
            return
        }
        viewModelScope.launch {
            delay(400)
            if (_uiState.value.googleLifecycle != GoogleAuthLifecycle.WAITING_EXTERNAL_AUTH) return@launch
            val user = authRepository.getCurrentUser()
            if (isFreshGoogleUser(user, googleLaunchAuthUserId)) {
                googleWaitJob?.cancel()
                completeGoogleSignupSuccess()
            }
        }
    }

    private fun completeGoogleSignupSuccess() {
        AuthAnalytics.track("google_signup_completed")
        GoogleAuthTrace.event("GOOGLE-SUCCESS")
        _uiState.update { s ->
            s.copy(
                isLoading = false,
                googleAuthenticated = true,
                registeredEmail = null,
                qaDiagnostic = null,
                errorMessage = null,
                googleLifecycle = GoogleAuthLifecycle.SUCCESS
            )
        }
    }

    fun resendConfirmation() {
        val email = AuthValidators.normalizeEmail(_uiState.value.email)
        if (email.isBlank() || _uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            authRepository.sendEmailVerification(email)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            registeredEmail = email,
                            offerResendConfirmation = false,
                            qaDiagnostic = null
                        )
                    }
                }
                .onFailure { error ->
                    val appError = AuthErrorMapper.fromThrowable(error)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = appError.userMessage,
                            qaDiagnostic = com.comunidapp.app.domain.auth.AuthSignupDiagnostic.debugDetail(
                                AuthErrorCode.entries.firstOrNull { code -> code.name == appError.code }
                                    ?: AuthErrorCode.UNKNOWN_AUTH_ERROR,
                                appError.technicalMessage,
                                com.comunidapp.app.BuildConfig.DEBUG
                            )
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
                            successMessage = "Te enviamos un nuevo código.",
                            resendCooldownSeconds = RESEND_COOLDOWN_SECONDS
                        )
                    }
                    startResendCooldown()
                }
                .onFailure { error ->
                    AuthAnalytics.track("auth_error_shown")
                    val appError = AuthErrorMapper.fromThrowable(error)
                    val message = if (appError.code == AuthErrorCode.RATE_LIMITED.name) {
                        "Esperá un momento antes de solicitar otro código."
                    } else {
                        userMessage(error)
                    }
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = message)
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

    fun clearOtpFeedback() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    fun confirmWithOtp(email: String, otpCode: String) {
        if (_uiState.value.isLoading) return
        val validated = EmailOtpValidators.validate(otpCode)
        if (validated.isFailure) {
            _uiState.update { it.copy(errorMessage = EmailOtpValidators.PROMPT_MESSAGE) }
            return
        }
        val code = validated.getOrThrow()
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
            authRepository.verifyEmailOtp(email, code)
                .onSuccess {
                    AuthAnalytics.track("email_verification_completed")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isVerified = true,
                            successMessage = "Email confirmado."
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
