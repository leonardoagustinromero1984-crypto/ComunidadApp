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
import com.comunidapp.app.domain.auth.PostAuthDestination
import com.comunidapp.app.domain.auth.PostAuthResolver
import com.comunidapp.app.domain.user.AccountStatus
import com.comunidapp.app.domain.user.ProfileGate
import com.comunidapp.app.domain.user.ProfileSessionGate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Compatibilidad con el gate de navegación existente.
 * Preferir [authState] para lógica nueva.
 */
enum class SessionState {
    Loading,
    LoggedOut,
    LegalConsentRequired,
    PasswordResetActive,
    ProfileSetupRequired,
    AccountAccessBlocked,
    LoggedIn,
    AdminSession,
    AdminPasswordChangeRequired,
    AdminMfaEnrollmentRequired,
    AdminMfaChallengeRequired
}

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModel(
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val userRepository: UserRepository = DataProvider.userRepository,
    private val adminSessionRepository: com.comunidapp.app.data.repository.AdminSessionRepository =
        DataProvider.adminSessionRepository,
    private val permissionRepository: com.comunidapp.app.data.repository.PermissionRepository =
        DataProvider.permissionRepository,
    private val adminMfaRepository: com.comunidapp.app.data.repository.AdminMfaRepository =
        DataProvider.adminMfaRepository
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Initializing)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _sessionState = MutableStateFlow(SessionState.Loading)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _blockedAccountStatus = MutableStateFlow(AccountStatus.SUSPENDED)
    val blockedAccountStatus: StateFlow<AccountStatus> = _blockedAccountStatus.asStateFlow()

    private var observeJob: Job? = null
    private var logoutJob: Job? = null
    private var loginJob: Job? = null
    private var consentJob: Job? = null

    /** Deep link de recovery activo; bloquea entrada a MAIN hasta reset. */
    private var passwordResetActive: Boolean = false

    private var lastContextUserId: String? = null
    private var trackedAuthUserId: String? = null
    private var resolveGeneration: Int = 0
    private var contextRefreshJob: Job? = null

    init {
        startObserving()
    }

    private fun startObserving() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            authRepository.observeAuthState()
                .distinctUntilChanged { previous, next -> previous?.id == next?.id }
                .collectLatest { authUser ->
                    val generation = resolveGeneration
                    if (passwordResetActive) {
                        setCurrentUser(authUser)
                        emitAuth(AuthState.PasswordResetActive)
                        return@collectLatest
                    }
                    if (_authState.value is AuthState.SigningOut) {
                        return@collectLatest
                    }
                    if (authUser == null) {
                        recoverOrSignOut(generation)
                        return@collectLatest
                    }
                    if (com.comunidapp.app.domain.auth.GoogleOAuthPending.isPendingUserId(authUser.id)) {
                        emitAuth(AuthState.Initializing)
                        return@collectLatest
                    }
                    isolateIdentity(authUser.id)
                    if (alreadyRoutedFor(authUser.id)) {
                        if (!isAdminSessionActive()) {
                            watchPersonUpdates(generation, authUser.id)
                        }
                        return@collectLatest
                    }
                    emitAuth(AuthState.Initializing)
                    if (tryEnterAdminSession(authUser)) return@collectLatest
                    hydratePerson(generation, authUser)
                }
        }
    }

    private suspend fun recoverOrSignOut(generation: Int) {
        if (trackedAuthUserId != null &&
            _authState.value is AuthState.Initializing &&
            _sessionState.value == SessionState.Loading
        ) {
            delay(OAUTH_NULL_GRACE_MS)
            if (generation != resolveGeneration) return
            val still = authRepository.getCurrentUser()
            if (still != null &&
                !com.comunidapp.app.domain.auth.GoogleOAuthPending.isPendingUserId(still.id)
            ) {
                isolateIdentity(still.id)
                if (alreadyRoutedFor(still.id)) {
                    if (!isAdminSessionActive()) {
                        watchPersonUpdates(generation, still.id)
                    }
                } else {
                    emitAuth(AuthState.Initializing)
                    if (tryEnterAdminSession(still)) return
                    hydratePerson(generation, still)
                }
                return
            }
        }
        handleSignedOut()
    }

    private fun isolateIdentity(incomingAuthUserId: String) {
        val previous = trackedAuthUserId
        if (previous != null && previous != incomingAuthUserId) {
            clearSessionIdentity()
        }
        trackedAuthUserId = incomingAuthUserId
    }

    private fun clearSessionIdentity() {
        contextRefreshJob?.cancel()
        setCurrentUser(null)
        lastContextUserId = null
        permissionRepository.invalidate()
        adminMfaRepository.clearMemory()
        com.comunidapp.app.domain.user.AccountIdentityCleanup.clear()
    }

    private fun handleSignedOut() {
        if (_authState.value is AuthState.SigningOut ||
            _authState.value is AuthState.Authenticating ||
            _authState.value is AuthState.Registering ||
            _authState.value is AuthState.PasswordResetActive ||
            _authState.value is AuthState.AuthError
        ) {
            return
        }
        if (passwordResetActive) return
        trackedAuthUserId = null
        clearSessionIdentity()
        emitAuth(AuthState.Unauthenticated)
    }

    private fun alreadyRoutedFor(authUserId: String): Boolean {
        val resolved = _currentUser.value ?: return false
        if (resolved.id != authUserId) return false
        if (isAdminSessionActive()) return true
        if (com.comunidapp.app.domain.user.SessionPersonRouting.isJwtStub(resolved)) return false
        return when (_sessionState.value) {
            SessionState.Loading, SessionState.LoggedOut -> false
            else -> true
        }
    }

    private fun isAdminSessionActive(): Boolean =
        _sessionState.value == SessionState.AdminSession ||
            _sessionState.value == SessionState.AdminPasswordChangeRequired ||
            _sessionState.value == SessionState.AdminMfaEnrollmentRequired ||
            _sessionState.value == SessionState.AdminMfaChallengeRequired

    private suspend fun tryEnterAdminSession(authUser: User): Boolean {
        val auth = runCatching {
            adminSessionRepository.authState(authUser.id)
        }.getOrNull() ?: return false
        if (!auth.isAdminIdentity) return false
        setCurrentUser(authUser, treatAsPerson = false)
        lastContextUserId = null
        if (auth.mustChangePassword) {
            emitAuth(
                AuthState.AdminAuthenticated(
                    toAuthUser(authUser),
                    mustChangePassword = true
                )
            )
            return true
        }
        return continueAdminMfaOrHub(authUser)
    }

    private suspend fun continueAdminMfaOrHub(authUser: User): Boolean {
        return when (adminMfaRepository.resolveFactorState()) {
            com.comunidapp.app.data.repository.AdminMfaFactorState.ENROLLMENT_REQUIRED -> {
                emitAuth(
                    AuthState.AdminAuthenticated(
                        toAuthUser(authUser),
                        mfaEnrollmentRequired = true
                    )
                )
                true
            }
            com.comunidapp.app.data.repository.AdminMfaFactorState.CHALLENGE_REQUIRED -> {
                emitAuth(
                    AuthState.AdminAuthenticated(
                        toAuthUser(authUser),
                        mfaChallengeRequired = true
                    )
                )
                true
            }
            com.comunidapp.app.data.repository.AdminMfaFactorState.AAL2 ->
                enterAdminHub(authUser)
            com.comunidapp.app.data.repository.AdminMfaFactorState.STALE -> {
                emitAuth(
                    AuthState.AuthError(
                        AuthErrorMapper.fromThrowable(
                            AuthErrorMapper.toException(
                                AuthErrorCode.INVALID_CREDENTIALS,
                                "La sesión administrativa no es válida. Volvé a iniciar sesión."
                            )
                        ),
                        previous = AuthState.Unauthenticated
                    )
                )
                runCatching { authRepository.logout() }
                true
            }
        }
    }

    private suspend fun enterAdminHub(authUser: User): Boolean {
        if (!adminMfaRepository.confirmAal2()) {
            emitAuth(
                AuthState.AuthError(
                    AuthErrorMapper.fromThrowable(
                        AuthErrorMapper.toException(
                            AuthErrorCode.INVALID_CREDENTIALS,
                            "La sesión administrativa no es válida. Volvé a iniciar sesión."
                        )
                    ),
                    previous = AuthState.Unauthenticated
                )
            )
            runCatching { authRepository.logout() }
            return true
        }
        val info = runCatching {
            adminSessionRepository.currentSession(authUser.id)
        }.getOrNull()
        val ctx = runCatching {
            permissionRepository.refresh(authUser.id)
        }.getOrElse { com.comunidapp.app.domain.authorization.AuthorizationContext.empty(authUser.id) }
        if (!com.comunidapp.app.domain.authorization.AdminSessionRouting.canEnterHub(ctx, info)) {
            emitAuth(
                AuthState.AuthError(
                    AuthErrorMapper.fromThrowable(
                        AuthErrorMapper.toException(
                            AuthErrorCode.INVALID_CREDENTIALS,
                            "Usuario o contraseña incorrectos."
                        )
                    ),
                    previous = AuthState.Unauthenticated
                )
            )
            runCatching { authRepository.logout() }
            return true
        }
        emitAuth(AuthState.AdminAuthenticated(toAuthUser(authUser)))
        return true
    }

    private suspend fun hydratePerson(generation: Int, authUser: User) {
        when (val fetched = fetchCanonicalPerson(generation, authUser.id)) {
            PersonFetch.Cancelled -> return
            is PersonFetch.Found -> {
                applyResolvedPerson(fetched.person)
                watchPersonUpdates(generation, authUser.id)
                return
            }
            is PersonFetch.BackendError -> {
                emitPersonResolveError(fetched.error)
                watchPersonUpdates(generation, authUser.id)
                return
            }
            PersonFetch.Missing -> Unit
        }
        if (generation != resolveGeneration) return
        // observeUser already emitted null: wait for a PERSON row, not a JWT stub.
        val observed = withTimeoutOrNull(PERSON_OBSERVE_TIMEOUT_MS) {
            userRepository.observeUser(authUser.id).first { candidate -> candidate != null }
        }
        if (generation != resolveGeneration) return
        if (observed != null) {
            applyResolvedPerson(observed)
            watchPersonUpdates(generation, authUser.id)
            return
        }
        if (com.comunidapp.app.domain.user.ProfileHydrationStore.isReady(authUser.id)) {
            emitPersonResolveError(
                AuthErrorMapper.toException(
                    AuthErrorCode.NETWORK_UNAVAILABLE,
                    "person hydrate timeout"
                )
            )
        } else {
            emitAuth(AuthState.ProfileSetupRequired(toAuthUser(authUser)))
        }
        watchPersonUpdates(generation, authUser.id)
    }

    private suspend fun fetchCanonicalPerson(generation: Int, userId: String): PersonFetch {
        var lastError: Throwable? = null
        var emptyRead = false
        repeat(CANONICAL_PERSON_ATTEMPTS) { attempt ->
            if (generation != resolveGeneration) return PersonFetch.Cancelled
            val result = runCatching { userRepository.fetchPerson(userId) }
            val person = result.getOrNull()
            if (person != null) return PersonFetch.Found(person)
            if (result.isFailure) {
                lastError = result.exceptionOrNull()
            } else {
                emptyRead = true
            }
            if (attempt < CANONICAL_PERSON_ATTEMPTS - 1) {
                delay(CANONICAL_PERSON_RETRY_MS)
            }
        }
        val error = lastError
        if (error != null && !emptyRead) return PersonFetch.BackendError(error)
        return PersonFetch.Missing
    }

    private suspend fun watchPersonUpdates(generation: Int, userId: String) {
        if (isAdminSessionActive()) return
        userRepository.observeUser(userId).collect { user ->
            if (generation != resolveGeneration) return@collect
            if (_authState.value is AuthState.SigningOut) return@collect
            if (passwordResetActive) return@collect
            if (isAdminSessionActive()) return@collect
            if (user != null) {
                applyResolvedPerson(user)
            }
        }
    }

    private suspend fun applyResolvedPerson(person: User) {
        val latched = _currentUser.value
        if (shouldKeepCanonicalHome(latched, person)) {
            return
        }
        setCurrentUser(person)
        if (lastContextUserId != person.id) {
            lastContextUserId = person.id
            contextRefreshJob?.cancel()
            contextRefreshJob = viewModelScope.launch {
                com.comunidapp.app.domain.context.OperationalContextProvider.refresh(person.id)
            }
        }
        resolveAuthenticatedFlow(person)
    }

    /**
     * Home already rendered from a canonical complete PERSON.
     * A later observeUser / cache / JWT stub must not navigate to Completar perfil.
     */
    private fun shouldKeepCanonicalHome(latched: User?, incoming: User): Boolean {
        if (latched == null || latched.id != incoming.id) return false
        if (_sessionState.value != SessionState.LoggedIn) return false
        if (!com.comunidapp.app.domain.user.OnboardingCompleteness.isComplete(latched)) return false
        return !com.comunidapp.app.domain.user.OnboardingCompleteness.isComplete(incoming)
    }

    private fun emitPersonResolveError(error: Throwable) {
        emitAuth(
            AuthState.AuthError(
                AuthErrorMapper.fromThrowable(error),
                previous = AuthState.Unauthenticated
            )
        )
    }

    private sealed interface PersonFetch {
        data class Found(val person: User) : PersonFetch
        data class BackendError(val error: Throwable) : PersonFetch
        data object Missing : PersonFetch
        data object Cancelled : PersonFetch
    }

    private suspend fun resolveAuthenticatedFlow(user: User) {
        val hasConsent = authRepository.hasCurrentLegalConsent(user.id)
        val authUser = toAuthUser(user)
        if (!hasConsent) {
            emitAuth(AuthState.LegalConsentRequired(authUser))
            return
        }
        when (
            com.comunidapp.app.domain.user.SessionPersonRouting.decide(
                person = user,
                personResolved = true
            )
        ) {
            com.comunidapp.app.domain.user.SessionPersonRouting.Decision.LOADING ->
                emitAuth(AuthState.Initializing)
            com.comunidapp.app.domain.user.SessionPersonRouting.Decision.HOME ->
                emitAuth(authStateForProfile(user, authUser))
            com.comunidapp.app.domain.user.SessionPersonRouting.Decision.COMPLETE_PROFILE ->
                emitAuth(AuthState.ProfileSetupRequired(authUser))
        }
    }

    private fun authStateForProfile(user: User, authUser: AuthUser): AuthState {
        return when (val gate = ProfileSessionGate.evaluate(user)) {
            ProfileGate.ProfileSetupRequired -> AuthState.ProfileSetupRequired(authUser)
            ProfileGate.OnboardingBlocked -> {
                _blockedAccountStatus.value = AccountStatus.RESTRICTED
                AuthState.OnboardingBlocked(authUser)
            }
            ProfileGate.AccountSuspended -> {
                _blockedAccountStatus.value = AccountStatus.SUSPENDED
                AuthState.AccountSuspended(authUser)
            }
            ProfileGate.AccountBanned -> {
                _blockedAccountStatus.value = AccountStatus.BANNED
                AuthState.AccountBanned(authUser)
            }
            ProfileGate.AccountRestricted -> AuthState.AccountRestricted(authUser)
            ProfileGate.ProfileReady ->
                if (PostAuthResolver.destination(user) == PostAuthDestination.COMPLETE_LEOVER_PROFILE) {
                    AuthState.ProfileSetupRequired(authUser)
                } else {
                    com.comunidapp.app.domain.user.ProfileHydrationStore.markReady(user.id)
                    AuthState.Authenticated(authUser)
                }
        }
    }

    private fun toAuthUser(user: User) = AuthUser(
        id = user.id,
        email = user.email,
        emailVerified = user.emailVerified,
        sessionStartedAtEpochMs = System.currentTimeMillis()
    )

    /**
     * Procesa deep link clasificado (ya consumido una vez por [AuthDeepLinkParser]).
     */
    fun onAuthDeepLink(kind: AuthDeepLinkKind, userMessage: String? = null) {
        when (kind) {
            AuthDeepLinkKind.PasswordRecovery -> {
                passwordResetActive = true
                emitAuth(AuthState.PasswordResetActive)
            }
            AuthDeepLinkKind.EmailConfirmation,
            AuthDeepLinkKind.SessionCallback -> Unit
            AuthDeepLinkKind.LinkError,
            AuthDeepLinkKind.Unknown -> Unit
        }
        if (userMessage.isNullOrBlank()) return
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
                        emitAuth(authStateForProfile(user, toAuthUser(user)))
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
     * Tras completar onboarding, re-evalúa el perfil observado.
     */
    fun onProfileSetupCompleted() {
        viewModelScope.launch {
            val user = _currentUser.value ?: return@launch
            val refreshed = userRepository.getUser(user.id) ?: user
            setCurrentUser(refreshed)
            com.comunidapp.app.domain.onboarding.onb02.Onb02SessionFlags.justCompletedProfileSetup = true
            runCatching {
                com.comunidapp.app.data.local.Onb02StoreProvider.instance.markFullPending(user.id)
            }
            emitAuth(authStateForProfile(refreshed, toAuthUser(refreshed)))
        }
    }

    fun signIn(email: String, password: String) {
        if (_authState.value.isTransient) return
        loginJob?.cancel()
        loginJob = viewModelScope.launch {
            emitAuth(AuthState.Authenticating)
            authRepository.login(email, password)
                .onSuccess { user ->
                    setCurrentUser(user)
                    if (!tryEnterAdminSession(user)) {
                        resolveAuthenticatedFlow(user)
                    }
                }
                .onFailure { error ->
                    com.comunidapp.app.domain.observability.ObservabilityInstrumentation.reportLoginFailure()
                    val appError = AuthErrorMapper.fromThrowable(error)
                    if (appError.code == AuthErrorCode.EMAIL_NOT_VERIFIED.name) {
                        emitAuth(AuthState.EmailVerificationRequired(emailHint = null))
                    } else {
                        emitAuth(AuthState.AuthError(appError, previous = AuthState.Unauthenticated))
                    }
                }
        }
    }

    fun onAdminPasswordChanged() {
        viewModelScope.launch {
            val user = _currentUser.value ?: return@launch
            continueAdminMfaOrHub(user)
        }
    }

    suspend fun verifyAdminMfaCode(code: String): Result<Unit> {
        val user = _currentUser.value ?: return Result.failure(IllegalStateException("NO_SESSION"))
        val verified = adminMfaRepository.verifyCode(code)
        if (verified.isFailure) return verified
        enterAdminHub(user)
        return Result.success(Unit)
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
        observeJob?.cancel()
        loginJob?.cancel()
        resolveGeneration += 1
        logoutJob = viewModelScope.launch {
            emitAuth(AuthState.SigningOut)
            passwordResetActive = false
            contextRefreshJob?.cancel()
            runCatching { authRepository.logout() }
            com.comunidapp.app.domain.observability.ObservabilityInstrumentation.reportLogout()
            setCurrentUser(null)
            lastContextUserId = null
            trackedAuthUserId = null
            permissionRepository.invalidate()
            adminMfaRepository.clearMemory()
            com.comunidapp.app.viewmodel.moderation.AdministrativeSessionCleanup.clear()
            com.comunidapp.app.notifications.NotificationPendingNavigationStore.clear()
            com.comunidapp.app.domain.navigation.AppNavRestoreStore.clear()
            com.comunidapp.app.domain.onboarding.onb02.Onb02SessionFlags.justCompletedProfileSetup = false
            com.comunidapp.app.domain.user.ProfileAvatarMemory.clear()
            com.comunidapp.app.domain.user.AccountIdentityCleanup.clear()
            emitAuth(AuthState.Unauthenticated)
            startObserving()
        }
    }

    private fun setCurrentUser(user: User?, treatAsPerson: Boolean = true) {
        _currentUser.value = user
        if (treatAsPerson && !isAdminSessionActive()) {
            com.comunidapp.app.domain.user.SessionResolvedPerson.set(user)
        } else {
            com.comunidapp.app.domain.user.SessionResolvedPerson.clear()
        }
        runCatching {
            com.comunidapp.app.domain.social.ReelPublishController.get().bindVisibleJob(user?.id)
        }
    }

    private fun emitAuth(state: AuthState) {
        _authState.value = state
        // AuthState.Initializing -> SessionState.Loading
        _sessionState.value = when (state) {
            AuthState.Initializing,
            AuthState.Authenticating,
            AuthState.Registering -> SessionState.Loading
            is AuthState.Authenticated,
            is AuthState.AccountRestricted -> SessionState.LoggedIn
            is AuthState.AdminAuthenticated ->
                when {
                    state.mustChangePassword -> SessionState.AdminPasswordChangeRequired
                    state.mfaEnrollmentRequired -> SessionState.AdminMfaEnrollmentRequired
                    state.mfaChallengeRequired -> SessionState.AdminMfaChallengeRequired
                    else -> SessionState.AdminSession
                }
            is AuthState.LegalConsentRequired -> SessionState.LegalConsentRequired
            is AuthState.ProfileSetupRequired -> SessionState.ProfileSetupRequired
            is AuthState.OnboardingBlocked,
            is AuthState.AccountSuspended,
            is AuthState.AccountBanned -> SessionState.AccountAccessBlocked
            AuthState.PasswordResetActive -> SessionState.PasswordResetActive
            AuthState.SigningOut -> SessionState.LoggedOut
            else -> SessionState.LoggedOut
        }
    }

    companion object {
        private const val CANONICAL_PERSON_ATTEMPTS = 3
        private const val CANONICAL_PERSON_RETRY_MS = 250L
        private const val PERSON_OBSERVE_TIMEOUT_MS = 6_000L
        private const val OAUTH_NULL_GRACE_MS = 400L
    }
}
