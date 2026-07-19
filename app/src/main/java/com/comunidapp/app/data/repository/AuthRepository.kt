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
    suspend fun register(
        name: String,
        email: String,
        password: String,
        consent: ConsentMetadata,
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
        MockAuthDatabase.resetToFixtures()
        // Fixture demo ya verificada: consentimiento vigente alineado a LegalDocumentConfig.
        consentsByEmail[AuthValidators.normalizeEmail(MockData.currentUser.email)] =
            ConsentMetadata.forRegistration()
    }

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

    override suspend fun register(
        name: String,
        email: String,
        password: String,
        consent: ConsentMetadata,
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
        val normalizedEmail = AuthValidators.normalizeEmail(email)

        if (MockAuthDatabase.findByEmail(normalizedEmail) != null) {
            return Result.failure(
                AuthErrorMapper.toException(
                    AuthErrorCode.EMAIL_ALREADY_REGISTERED,
                    "duplicate email"
                )
            )
        }

        val effectiveType = AccountType.PERSON

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
            emailVerified = false
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
        val normalizedEmail = AuthValidators.normalizeEmail(email)
        EmailOtpValidators.validate(otpCode).getOrElse { err ->
            return Result.failure(
                AuthErrorMapper.toException(
                    AuthErrorCode.RECOVERY_LINK_INVALID,
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
    }
}