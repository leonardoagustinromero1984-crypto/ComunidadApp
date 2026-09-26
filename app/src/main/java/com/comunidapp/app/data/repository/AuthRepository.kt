package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.AccountType
import com.comunidapp.app.data.model.AuthAccount
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.mock.MockAuthDatabase
import com.comunidapp.app.data.mock.MockData
import com.comunidapp.app.data.mock.MockUserStore
import com.comunidapp.app.domain.auth.AuthErrorCode
import com.comunidapp.app.domain.auth.AuthErrorMapper
import com.comunidapp.app.domain.auth.AuthException
import com.comunidapp.app.domain.auth.ConsentMetadata
import com.comunidapp.app.domain.auth.validation.AuthValidators
import com.comunidapp.app.domain.auth.validation.EmailOtpValidators
import com.comunidapp.app.domain.user.UsernameValidators
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class EmailNotVerifiedException(email: String) :
    Exception("Debés confirmar tu email antes de iniciar sesión.") {
    init {
        require(email.isNotBlank())
    }
}

/**
 * Contrato de autenticación (mock + Supabase).
 * Extender este interface; no crear repositorios paralelos.
 */
interface AuthRepository {
    suspend fun login(email: String, password: String): Result<User>
    suspend fun loginAdministrative(username: String, password: String): Result<User> =
        Result.failure(
            AuthErrorMapper.toException(
                AuthErrorCode.INVALID_CREDENTIALS,
                "Usuario o contraseña incorrectos."
            )
        )

    /**
     * Unified username field: PERSON [persons.username] first, then staff.
     * Default keeps staff-only so callers that only implement admin stay safe.
     */
    suspend fun loginWithUsername(username: String, password: String): Result<User> =
        loginAdministrative(username, password)
    suspend fun register(
        name: String,
        email: String,
        password: String,
        consent: ConsentMetadata,
        username: String,
        birthDate: String = "",
        accountType: AccountType = AccountType.PERSON
    ): Result<User>
    suspend fun sendPasswordResetEmail(email: String): Result<Unit>
    /** Mock: token. Remoto: requiere sesión de recovery; [email]/[token] se ignoran si hay sesión. */
    suspend fun resetPassword(email: String, token: String, newPassword: String): Result<Unit>
    /** Actualiza contraseña con sesión de recovery activa (SDK updateUser). */
    suspend fun updatePasswordFromRecovery(newPassword: String): Result<Unit>
    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit>
    suspend fun hasCurrentLegalConsent(userId: String): Boolean
    suspend fun acceptLegalConsents(consent: ConsentMetadata): Result<Unit>
    /**
     * Elimina la cuenta del usuario autenticado vía Edge Function (remoto)
     * o borrado mock local. No acepta userId libre como autoridad.
     */
    suspend fun deleteAccount(idempotencyKey: String): Result<Unit>
    suspend fun sendEmailVerification(email: String): Result<Unit>
    suspend fun confirmEmailVerification(email: String): Result<Unit>
    suspend fun verifyEmailOtp(email: String, otpCode: String): Result<Unit>
    suspend fun isEmailVerified(email: String): Boolean
    fun getCurrentUser(): User?
    suspend fun logout()
    fun observeAuthState(): Flow<User?>

    /**
     * Builds the Google PKCE authorize URL. The ViewModel must open it with
     * [com.comunidapp.app.domain.auth.LeoVerGoogleSignIn.openAuthorizeUrl].
     * Empty URL means mock: [signInWithGoogle] completes locally.
     */
    suspend fun createGoogleOAuthUrl(): Result<String> = Result.success("")

    /**
     * Mock/local Google completion only. Real Google never uses this to open a browser.
     */
    suspend fun signInWithGoogle(): Result<User> =
        Result.failure(
            AuthErrorMapper.toException(
                AuthErrorCode.CONFIGURATION_ERROR,
                "google auth requires custom tabs pkce"
            )
        )

    suspend fun signInWithGoogleIdToken(idToken: String): Result<User> =
        Result.failure(
            AuthErrorMapper.toException(
                AuthErrorCode.CONFIGURATION_ERROR,
                "google id token path disconnected"
            )
        )

    /** Linked login methods for the current session. Read-only. No unlink. */
    fun linkedAuthMethods(): List<com.comunidapp.app.domain.auth.AuthMethodKind> = emptyList()

    /** Google/OAuth users can add a LeoVer password to the same auth.users / PERSON. */
    suspend fun addPassword(newPassword: String): Result<Unit> =
        Result.failure(
            AuthErrorMapper.toException(
                AuthErrorCode.CONFIGURATION_ERROR,
                "add password not implemented"
            )
        )

    suspend fun ensureAuthenticatedSession(): Result<User> {
        val user = getCurrentUser()
            ?: return Result.failure(
                AuthErrorMapper.toException(AuthErrorCode.SESSION_EXPIRED, "no session")
            )
        return Result.success(user)
    }
}

class MockAuthRepository : AuthRepository {

    private val _authState = MutableStateFlow<User?>(null)
    private val consentsByEmail = mutableMapOf<String, ConsentMetadata>()

    init {
        consentsByEmail[AuthValidators.normalizeEmail(MockData.currentUser.email)] =
            ConsentMetadata.forRegistration()
    }

    /** Solo tests: último consentimiento guardado para un email. */
    fun consentFor(email: String): ConsentMetadata? =
        consentsByEmail[AuthValidators.normalizeEmail(email)]

    override fun observeAuthState(): Flow<User?> = _authState.map { user ->
        if (user == null) return@map null
        val account = MockAuthDatabase.findByEmail(user.email)
        if (account != null && !account.emailVerified) null else user
    }

    private fun setLoggedInUser(user: User?) {
        _authState.value = user
    }

    /** Solo tests: limpia sesión y reinstala fixtures. */
    /** Solo tests. */
    fun clearConsentsForTests() {
        consentsByEmail.clear()
    }

    fun resetForTests() {
        setLoggedInUser(null)
        recoverySessionEmail = null
        consentsByEmail.clear()
        deletedEmails.clear()
        reauthFailures = 0
        sendEmailVerificationOverride = null
        verifyEmailOtpOverride = null
        MockAuthDatabase.resetToFixtures()
        googleSignInOverride = null
        mockLinkedMethods = emptyList()
        MockAdministrativeIdentityStore.reset()
        // Fixture demo ya verificada: consentimiento vigente alineado a LegalDocumentConfig.
        consentsByEmail[AuthValidators.normalizeEmail(MockData.currentUser.email)] =
            ConsentMetadata.forRegistration()
    }

    /** Solo tests: emite sesión OAuth (p. ej. JWT stub) sin pasar por login email. */
    fun emitSessionForTests(user: User?) {
        setLoggedInUser(user)
    }

    /** Solo tests: consentimiento vigente para un email distinto al fixture. */
    fun grantConsentForTests(email: String) {
        consentsByEmail[AuthValidators.normalizeEmail(email)] =
            ConsentMetadata.forRegistration()
    }

    var sendEmailVerificationOverride: Result<Unit>? = null
    var verifyEmailOtpOverride: Result<Unit>? = null

    private var recoverySessionEmail: String? = null
    private val deletedEmails = mutableSetOf<String>()
    private var reauthFailures = 0

    /** Solo tests / UI mock: abre sesión de recovery equivalente al deep link. */
    fun activateRecoverySession(email: String) {
        recoverySessionEmail = AuthValidators.normalizeEmail(email)
    }

    fun isRecoverySessionActive(): Boolean = recoverySessionEmail != null

    fun clearRecoverySession() {
        recoverySessionEmail = null
    }

    override suspend fun login(email: String, password: String): Result<User> {
        delay(50)
        AuthValidators.validateEmail(email).getOrElse {
            return Result.failure(AuthErrorMapper.fromThrowableToException(it))
        }
        AuthValidators.validatePassword(password).getOrElse {
            // En login, password inválida por formato se reporta como credenciales inválidas
            // para no filtrar detalles; si está vacía/corta tras trim de email ok:
            return Result.failure(
                AuthErrorMapper.toException(
                    AuthErrorCode.INVALID_CREDENTIALS,
                    "invalid credentials"
                )
            )
        }
        val normalizedEmail = AuthValidators.normalizeEmail(email)

        val account = MockAuthDatabase.findByEmail(normalizedEmail)
            ?: return Result.failure(
                AuthErrorMapper.toException(
                    AuthErrorCode.INVALID_CREDENTIALS,
                    "unknown email"
                )
            )

        return when {
            account.password != password ->
                Result.failure(
                    AuthErrorMapper.toException(
                        AuthErrorCode.INVALID_CREDENTIALS,
                        "bad password"
                    )
                )
            !account.emailVerified ->
                Result.failure(
                    AuthErrorMapper.toException(
                        AuthErrorCode.EMAIL_NOT_VERIFIED,
                        "email not verified"
                    )
                )
            else -> {
                val userId = resolveMockUserId(normalizedEmail)
                val stored = MockUserStore.get(userId)
                val user = stored ?: User(
                    id = userId,
                    name = account.name,
                    email = account.email,
                    emailVerified = true
                )
                setLoggedInUser(user)
                Result.success(user)
            }
        }
    }

    override suspend fun loginAdministrative(username: String, password: String): Result<User> {
        delay(40)
        val entry = MockAdministrativeIdentityStore.find(username, password)
            ?: return Result.failure(
                AuthErrorMapper.toException(
                    AuthErrorCode.INVALID_CREDENTIALS,
                    "Usuario o contraseña incorrectos."
                )
            )
        setLoggedInUser(entry.user)
        return Result.success(entry.user)
    }

    override suspend fun loginWithUsername(username: String, password: String): Result<User> {
        delay(40)
        val normalized = UsernameValidators.normalize(username)
        val person = MockUserStore.allUsers().firstOrNull {
            it.username.equals(normalized, ignoreCase = true)
        }
        if (person != null) {
            val email = person.email.trim()
            if (email.isEmpty()) {
                return Result.failure(
                    AuthErrorMapper.toException(
                        AuthErrorCode.INVALID_CREDENTIALS,
                        "Usuario o contraseña incorrectos."
                    )
                )
            }
            return login(email, password)
        }
        return loginAdministrative(username, password)
    }

    override suspend fun register(
        name: String,
        email: String,
        password: String,
        consent: ConsentMetadata,
        username: String,
        birthDate: String,
        accountType: AccountType
    ): Result<User> {
        delay(50)
        if (name.isBlank()) {
            return Result.failure(
                AuthErrorMapper.toException(
                    AuthErrorCode.UNKNOWN_AUTH_ERROR,
                    "name required",
                )
            )
        }
        val normalizedUsername = UsernameValidators.validate(username).getOrElse {
            return Result.failure(
                AuthErrorMapper.toException(
                    AuthErrorCode.UNKNOWN_AUTH_ERROR,
                    "username invalid"
                )
            )
        }
        AuthValidators.validateEmail(email).getOrElse {
            return Result.failure(AuthErrorMapper.fromThrowableToException(it))
        }
        AuthValidators.validatePassword(password).getOrElse {
            return Result.failure(AuthErrorMapper.fromThrowableToException(it))
        }
        AuthValidators.validateConsents(
            acceptedTerms = true,
            acceptedPrivacy = true,
            termsVersion = consent.termsVersion,
            privacyVersion = consent.privacyVersion
        ).getOrElse {
            return Result.failure(AuthErrorMapper.fromThrowableToException(it))
        }
        if (consent.source != ConsentMetadata.SOURCE_REGISTRATION) {
            return Result.failure(
                AuthErrorMapper.toException(
                    AuthErrorCode.CONFIGURATION_ERROR,
                    "invalid consent source"
                )
            )
        }
        val personAge = com.comunidapp.app.domain.user.PersonAgeRules
            .validateSignupBirthDate(birthDate.ifBlank { "1990-01-15" })
            .getOrElse {
                return Result.failure(
                    AuthErrorMapper.toException(
                        AuthErrorCode.UNKNOWN_AUTH_ERROR,
                        it.message ?: "BIRTH_DATE_INVALID"
                    )
                )
            }
        val normalizedEmail = AuthValidators.normalizeEmail(email)

        if (MockAuthDatabase.findByEmail(normalizedEmail) != null) {
            return Result.failure(
                AuthErrorMapper.toException(
                    AuthErrorCode.EMAIL_ALREADY_REGISTERED,
                    "duplicate email"
                )
            )
        }
        val usernameTaken = MockUserStore.allUsers().any {
            it.username.equals(normalizedUsername.value, ignoreCase = true)
        }
        if (usernameTaken) {
            return Result.failure(
                AuthErrorMapper.toException(
                    AuthErrorCode.UNKNOWN_AUTH_ERROR,
                    "USERNAME_UNAVAILABLE"
                )
            )
        }

        val effectiveType = com.comunidapp.app.domain.user.SessionIdentity.signupAccountType()

        MockAuthDatabase.save(
            AuthAccount(
                email = normalizedEmail,
                password = password,
                name = name.trim(),
                emailVerified = false
            )
        )
        consentsByEmail[normalizedEmail] = consent.copy(
            termsVersion = consent.termsVersion.trim(),
            privacyVersion = consent.privacyVersion.trim(),
            source = ConsentMetadata.SOURCE_REGISTRATION
        )
        sendEmailVerification(normalizedEmail)

        val userId = resolveMockUserId(normalizedEmail)
        val user = User(
            id = userId,
            name = name.trim(),
            email = normalizedEmail,
            accountType = effectiveType,
            emailVerified = false,
            username = normalizedUsername.value,
            displayName = name.trim(),
            onboardingStatus = "COMPLETED",
            birthDate = personAge.birthDate.toString(),
            ageBand = personAge.band.name
        )
        MockUserStore.upsert(user)
        setLoggedInUser(user)
        return Result.success(user)
    }

    private fun resolveMockUserId(email: String): String {
        return if (email == MockData.currentUser.email.lowercase()) {
            MockData.currentUser.id
        } else {
            "user_${email.hashCode()}"
        }
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        delay(40)
        AuthValidators.validateEmail(email).getOrElse {
            return Result.failure(AuthErrorMapper.fromThrowableToException(it))
        }
        val normalizedEmail = AuthValidators.normalizeEmail(email)
        // Anti-enumeración: siempre éxito genérico.
        MockAuthDatabase.generateResetToken(normalizedEmail)
        return Result.success(Unit)
    }

    override suspend fun resetPassword(
        email: String,
        token: String,
        newPassword: String
    ): Result<Unit> {
        delay(40)
        AuthValidators.validatePassword(newPassword).getOrElse {
            return Result.failure(AuthErrorMapper.fromThrowableToException(it))
        }
        // Prefer recovery session if active (deep link mock).
        val recoveryEmail = recoverySessionEmail
        if (recoveryEmail != null) {
            MockAuthDatabase.updatePassword(recoveryEmail, newPassword)
            clearRecoverySession()
            return Result.success(Unit)
        }
        AuthValidators.validateEmail(email).getOrElse {
            return Result.failure(AuthErrorMapper.fromThrowableToException(it))
        }
        val normalizedEmail = AuthValidators.normalizeEmail(email)
        if (!MockAuthDatabase.isValidResetToken(normalizedEmail, token)) {
            return Result.failure(
                AuthErrorMapper.toException(
                    AuthErrorCode.RECOVERY_LINK_EXPIRED,
                    "invalid or expired reset token"
                )
            )
        }
        MockAuthDatabase.updatePassword(normalizedEmail, newPassword)
        return Result.success(Unit)
    }

    override suspend fun updatePasswordFromRecovery(newPassword: String): Result<Unit> {
        delay(40)
        AuthValidators.validatePassword(newPassword).getOrElse {
            return Result.failure(AuthErrorMapper.fromThrowableToException(it))
        }
        val email = recoverySessionEmail
            ?: return Result.failure(
                AuthErrorMapper.toException(
                    AuthErrorCode.PASSWORD_RESET_NOT_AVAILABLE,
                    "no recovery session"
                )
            )
        MockAuthDatabase.updatePassword(email, newPassword)
        clearRecoverySession()
        setLoggedInUser(null)
        return Result.success(Unit)
    }

    override suspend fun changePassword(
        currentPassword: String,
        newPassword: String
    ): Result<Unit> {
        delay(40)
        AuthValidators.validatePassword(newPassword).getOrElse {
            return Result.failure(AuthErrorMapper.fromThrowableToException(it))
        }
        val user = _authState.value
            ?: return Result.failure(
                AuthErrorMapper.toException(AuthErrorCode.SESSION_EXPIRED, "no session")
            )
        val account = MockAuthDatabase.findByEmail(user.email)
            ?: return Result.failure(
                AuthErrorMapper.toException(AuthErrorCode.INVALID_CREDENTIALS, "missing account")
            )
        if (account.password != currentPassword) {
            reauthFailures += 1
            if (reauthFailures >= 5) {
                return Result.failure(
                    AuthErrorMapper.toException(AuthErrorCode.RATE_LIMITED, "too many reauth failures")
                )
            }
            return Result.failure(
                AuthErrorMapper.toException(AuthErrorCode.INVALID_CREDENTIALS, "bad current password")
            )
        }
        reauthFailures = 0
        MockAuthDatabase.updatePassword(user.email, newPassword)
        return Result.success(Unit)
    }

    override suspend fun hasCurrentLegalConsent(userId: String): Boolean {
        val user = _authState.value ?: getCurrentUser() ?: return false
        if (user.id != userId && user.email != userId) {
            // Mock store keys by email; callers pass user.id — resolve via session.
        }
        val email = user.email
        val consent = consentsByEmail[AuthValidators.normalizeEmail(email)] ?: return false
        return consent.termsVersion == com.comunidapp.app.domain.auth.LegalDocumentConfig.terms.version &&
            consent.privacyVersion == com.comunidapp.app.domain.auth.LegalDocumentConfig.privacy.version
    }

    override suspend fun acceptLegalConsents(consent: ConsentMetadata): Result<Unit> {
        delay(30)
        val user = _authState.value
            ?: return Result.failure(
                AuthErrorMapper.toException(AuthErrorCode.SESSION_EXPIRED, "no session")
            )
        AuthValidators.validateConsents(
            acceptedTerms = true,
            acceptedPrivacy = true,
            termsVersion = consent.termsVersion,
            privacyVersion = consent.privacyVersion
        ).getOrElse {
            return Result.failure(AuthErrorMapper.fromThrowableToException(it))
        }
        consentsByEmail[AuthValidators.normalizeEmail(user.email)] = consent.copy(
            source = consent.source.ifBlank { ConsentMetadata.SOURCE_POST_LOGIN_GATE }
        )
        return Result.success(Unit)
    }

    override suspend fun deleteAccount(idempotencyKey: String): Result<Unit> {
        delay(40)
        if (idempotencyKey.isBlank()) {
            return Result.failure(
                AuthErrorMapper.toException(
                    AuthErrorCode.ACCOUNT_DELETION_FAILED,
                    "idempotency key required"
                )
            )
        }
        val user = _authState.value
            ?: return Result.failure(
                AuthErrorMapper.toException(AuthErrorCode.SESSION_EXPIRED, "no session")
            )
        val email = AuthValidators.normalizeEmail(user.email)
        if (email in deletedEmails) {
            setLoggedInUser(null)
            return Result.success(Unit) // idempotent retry
        }
        MockAuthDatabase.deleteAccount(email)
        consentsByEmail.remove(email)
        deletedEmails.add(email)
        setLoggedInUser(null)
        return Result.success(Unit)
    }

    override suspend fun sendEmailVerification(email: String): Result<Unit> {
        delay(30)
        sendEmailVerificationOverride?.let { return it }
        val normalizedEmail = AuthValidators.normalizeEmail(email)
        if (MockAuthDatabase.findByEmail(normalizedEmail) == null) {
            return Result.failure(
                AuthErrorMapper.toException(
                    AuthErrorCode.INVALID_CREDENTIALS,
                    "account not found for resend"
                )
            )
        }
        return Result.success(Unit)
    }

    override suspend fun confirmEmailVerification(email: String): Result<Unit> {
        delay(30)
        val normalizedEmail = AuthValidators.normalizeEmail(email)
        MockAuthDatabase.findByEmail(normalizedEmail)
            ?: return Result.failure(
                AuthErrorMapper.toException(
                    AuthErrorCode.INVALID_CREDENTIALS,
                    "account not found"
                )
            )
        MockAuthDatabase.setEmailVerified(normalizedEmail, true)
        return Result.success(Unit)
    }

    override suspend fun verifyEmailOtp(email: String, otpCode: String): Result<Unit> {
        delay(30)
        verifyEmailOtpOverride?.let { return it }
        val normalizedEmail = AuthValidators.normalizeEmail(email)
        EmailOtpValidators.validate(otpCode).getOrElse { err ->
            return Result.failure(
                AuthErrorMapper.toException(
                    AuthErrorCode.OTP_INVALID,
                    err.message ?: "otp invalid"
                )
            )
        }
        MockAuthDatabase.findByEmail(normalizedEmail)
            ?: return Result.failure(
                AuthErrorMapper.toException(
                    AuthErrorCode.INVALID_CREDENTIALS,
                    "account not found"
                )
            )
        // Mock: cualquier código de longitud válida confirma (no registrar el OTP).
        MockAuthDatabase.setEmailVerified(normalizedEmail, true)
        val pending = _authState.value
        if (pending != null && pending.email.equals(normalizedEmail, ignoreCase = true)) {
            setLoggedInUser(pending.copy(emailVerified = true))
        } else {
            MockUserStore.allUsers()
                .firstOrNull { it.email.equals(normalizedEmail, ignoreCase = true) }
                ?.let { stored -> setLoggedInUser(stored.copy(emailVerified = true)) }
        }
        return Result.success(Unit)
    }

    override suspend fun isEmailVerified(email: String): Boolean {
        return MockAuthDatabase.findByEmail(email.trim().lowercase())?.emailVerified == true
    }

    override fun getCurrentUser(): User? {
        val user = _authState.value ?: return null
        val account = MockAuthDatabase.findByEmail(user.email)
        return if (account != null && !account.emailVerified) null else user
    }

    override suspend fun logout() {
        setLoggedInUser(null)
        mockLinkedMethods = emptyList()
    }

    var googleSignInOverride: Result<User>? = null
    private var mockLinkedMethods: List<com.comunidapp.app.domain.auth.AuthMethodKind> = emptyList()

    override suspend fun createGoogleOAuthUrl(): Result<String> = Result.success("")

    override suspend fun signInWithGoogle(): Result<User> {
        delay(40)
        googleSignInOverride?.let { return it }
        val fixture = MockData.currentUser
        val account = MockAuthDatabase.findByEmail(fixture.email)
        if (account != null && !account.emailVerified) {
            MockAuthDatabase.setEmailVerified(fixture.email, true)
        }
        val stored = MockUserStore.get(fixture.id) ?: fixture.copy(emailVerified = true)
        val user = stored.copy(emailVerified = true)
        MockUserStore.upsert(user)
        mockLinkedMethods = listOf(
            com.comunidapp.app.domain.auth.AuthMethodKind.EMAIL_PASSWORD_OTP,
            com.comunidapp.app.domain.auth.AuthMethodKind.GOOGLE
        )
        setLoggedInUser(user)
        return Result.success(user)
    }

    override suspend fun signInWithGoogleIdToken(idToken: String): Result<User> = signInWithGoogle()

    override fun linkedAuthMethods(): List<com.comunidapp.app.domain.auth.AuthMethodKind> {
        if (_authState.value == null) return emptyList()
        return mockLinkedMethods.ifEmpty {
            listOf(com.comunidapp.app.domain.auth.AuthMethodKind.EMAIL_PASSWORD_OTP)
        }
    }

    override suspend fun addPassword(newPassword: String): Result<Unit> {
        delay(40)
        AuthValidators.validatePassword(newPassword).getOrElse {
            return Result.failure(AuthErrorMapper.fromThrowableToException(it))
        }
        val user = _authState.value
            ?: return Result.failure(
                AuthErrorMapper.toException(AuthErrorCode.SESSION_EXPIRED, "no session")
            )
        MockAuthDatabase.updatePassword(user.email, newPassword)
        mockLinkedMethods = (
            mockLinkedMethods + com.comunidapp.app.domain.auth.AuthMethodKind.EMAIL_PASSWORD_OTP
            ).distinct()
        return Result.success(Unit)
    }
}