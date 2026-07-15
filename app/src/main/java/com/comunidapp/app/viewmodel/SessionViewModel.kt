package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.UserRepository
import com.comunidapp.app.domain.auth.AuthDeepLinkKind
import com.comunidapp.app.domain.auth.AuthErrorCode
import com.comunidapp.app.domain.auth.AuthErrorMapper
import com.comunidapp.app.domain.auth.AuthState
import com.comunidapp.app.domain.auth.AuthUser
import com.comunidapp.app.domain.auth.ConsentMetadata
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Compatibilidad con el gate de navegación existente.
 * Preferir [authState] para lógica nueva de M01.
 */
enum class SessionState {
    Loading,
    LoggedOut,
    LegalConsentRequired,
    PasswordResetActive,
    LoggedIn
}

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModel(
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val userRepository: UserRepository = DataProvider.userRepository
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Initializing)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _sessionState = MutableStateFlow(SessionState.Loading)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private var observeJob: Job? = null
    private var logoutJob: Job? = null
    private var loginJob: Job? = null
    private var consentJob: Job? = null

    /** Deep link de recovery activo; bloquea entrada a MAIN hasta reset. */
    private var passwordResetActive: Boolean = false

    init {
        startObserving()
    }

    private fun startObserving() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            authRepository.observeAuthState()
                .flatMapLatest { authUser ->
                    if (authUser == null) {
                        if (_authState.value !is AuthState.SigningOut &&
                            _authState.value !is AuthState.Authenticating &&
                            _authState.value !is AuthState.Registering &&
                            _authState.value !is AuthState.PasswordResetActive
                        ) {
                            if (!passwordResetActive) {
                                emitAuth(AuthState.Unauthenticated)
                            }
                        }
                        flowOf(null)
                    } else {
                        userRepository.observeUser(authUser.id).map { profile -> profile ?: authUser }
                    }
                }
                .collect { user ->
                    if (passwordResetActive) {
                        _currentUser.value = user
                        emitAuth(AuthState.PasswordResetActive)
                        return@collect
                    }
                    _currentUser.value = user
                    if (user != null) {
                        val hasConsent = authRepository.hasCurrentLegalConsent(user.id)
                        if (!hasConsent) {
                            emitAuth(
                                AuthState.LegalConsentRequired(
                                    AuthUser(
                                        id = user.id,
                                        email = user.email,
                                        emailVerified = user.emailVerified
                                    )
                                )
                            )
                        } else {
                            emitAuth(
                                AuthState.Authenticated(
                                    AuthUser(
                                        id = user.id,
                                        email = user.email,
                                        emailVerified = user.emailVerified,
                                        sessionStartedAtEpochMs = System.currentTimeMillis()
                                    )
                                )
                            )
                        }
                    } else if (_authState.value is AuthState.Authenticated ||
                        _authState.value is AuthState.LegalConsentRequired ||
                        _authState.value is AuthState.Initializing ||
                        _authState.value is AuthState.SigningOut
                    ) {
                        emitAuth(AuthState.Unauthenticated)
                    }
                }
        }
    }

    /**
     * Procesa deep link clasificado (ya consumido una vez por [AuthDeepLinkParser]).
     */
    fun onAuthDeepLink(kind: AuthDeepLinkKind) {
        when (kind) {
            AuthDeepLinkKind.PasswordRecovery -> {
                passwordResetActive = true
                emitAuth(AuthState.PasswordResetActive)
            }
            AuthDeepLinkKind.EmailConfirmation -> {
                // La sesión la restaura handleDeeplinks; la UI de verificación existente aplica.
            }
            AuthDeepLinkKind.Unknown -> Unit
        }
    }

    fun clearPasswordResetActive() {
        passwordResetActive = false
        if (_authState.value is AuthState.PasswordResetActive) {
            emitAuth(AuthState.Unauthenticated)
        }
    }

    fun acceptLegalConsents(
        acceptedTerms: Boolean,
        acceptedPrivacy: Boolean,
        locale: String? = null
    ) {
        if (_authState.value !is AuthState.LegalConsentRequired) return
        if (consentJob?.isActive == true) return
        consentJob = viewModelScope.launch {
            val consent = ConsentMetadata.forPostLoginGate(locale)
            com.comunidapp.app.domain.auth.validation.AuthValidators.validateConsents(
                acceptedTerms = acceptedTerms,
                acceptedPrivacy = acceptedPrivacy,
                termsVersion = consent.termsVersion,
                privacyVersion = consent.privacyVersion
            ).getOrElse { err ->
                emitAuth(
                    AuthState.AuthError(
                        AuthErrorMapper.fromThrowable(err),
                        previous = _authState.value
                    )
                )
                return@launch
            }
            authRepository.acceptLegalConsents(consent)
                .onSuccess {
                    val user = _currentUser.value
                    if (user != null) {
                        emitAuth(
                            AuthState.Authenticated(
                                AuthUser(
                                    id = user.id,
                                    email = user.email,
                                    emailVerified = user.emailVerified
                                )
                            )
                        )
                    }
                }
                .onFailure { error ->
                    emitAuth(
                        AuthState.AuthError(
                            AuthErrorMapper.fromThrowable(error),
                            previous = _authState.value
                        )
                    )
                }
        }
    }

    /**
     * Login controlado para tests y Etapa 3. Evita doble envío en estados transitorios.
     */
    fun signIn(email: String, password: String) {
        if (_authState.value.isTransient) return
        loginJob?.cancel()
        loginJob = viewModelScope.launch {
            emitAuth(AuthState.Authenticating)
            authRepository.login(email, password)
                .onSuccess { user ->
                    _currentUser.value = user
                    val hasConsent = authRepository.hasCurrentLegalConsent(user.id)
                    if (!hasConsent) {
                        emitAuth(
                            AuthState.LegalConsentRequired(
                                AuthUser(
                                    id = user.id,
                                    email = user.email,
                                    emailVerified = user.emailVerified
                                )
                            )
                        )
                    } else {
                        emitAuth(
                            AuthState.Authenticated(
                                AuthUser(
                                    id = user.id,
                                    email = user.email,
                                    emailVerified = user.emailVerified
                                )
                            )
                        )
                    }
                }
                .onFailure { error ->
                    val appError = AuthErrorMapper.fromThrowable(error)
                    if (appError.code == AuthErrorCode.EMAIL_NOT_VERIFIED.name) {
                        emitAuth(AuthState.EmailVerificationRequired(emailHint = null))
                    } else {
                        emitAuth(AuthState.AuthError(appError, previous = AuthState.Unauthenticated))
                    }
                }
        }
    }

    fun clearAuthError() {
        val current = _authState.value
        if (current is AuthState.AuthError) {
            emitAuth(current.previous ?: AuthState.Unauthenticated)
        }
    }

    fun logout() {
        if (_authState.value is AuthState.SigningOut) return
        logoutJob?.cancel()
        logoutJob = viewModelScope.launch {
            emitAuth(AuthState.SigningOut)
            passwordResetActive = false
            runCatching { authRepository.logout() }
                .onFailure { error ->
                    emitAuth(
                        AuthState.AuthError(
                            AuthErrorMapper.fromThrowable(error),
                            previous = AuthState.Unauthenticated
                        )
                    )
                }
            _currentUser.value = null
            emitAuth(AuthState.Unauthenticated)
        }
    }

    private fun emitAuth(state: AuthState) {
        _authState.value = state
        _sessionState.value = when (state) {
            AuthState.Initializing -> SessionState.Loading
            is AuthState.Authenticated -> SessionState.LoggedIn
            is AuthState.LegalConsentRequired -> SessionState.LegalConsentRequired
            AuthState.PasswordResetActive -> SessionState.PasswordResetActive
            AuthState.SigningOut -> SessionState.LoggedOut
            else -> SessionState.LoggedOut
        }
    }
}
