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
import com.comunidapp.app.domain.auth.validation.AuthValidators
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class EmailNotVerifiedException(email: String) :
    Exception("Debés confirmar tu email antes de iniciar sesión.") {
    init {
        // email retenido solo para diagnóstico interno del mapper; no loguear.
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
        accountType: AccountType = AccountType.PERSON
    ): Result<User>
    suspend fun sendPasswordResetEmail(email: String): Result<Unit>
    suspend fun resetPassword(email: String, token: String, newPassword: String): Result<Unit>
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

    override fun observeAuthState(): Flow<User?> = _authState.map { user ->
        if (user == null) return@map null
        val account = MockAuthDatabase.findByEmail(user.email)
        if (account != null && !account.emailVerified) null else user
    }

    private fun setLoggedInUser(user: User?) {
        _authState.value = user
    }

    /** Solo tests: limpia sesión y reinstala fixtures. */
    fun resetForTests() {
        setLoggedInUser(null)
        MockAuthDatabase.resetToFixtures()
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
        val normalizedEmail = AuthValidators.normalizeEmail(email)

        if (MockAuthDatabase.findByEmail(normalizedEmail) != null) {
            return Result.failure(
                AuthErrorMapper.toException(
                    AuthErrorCode.EMAIL_ALREADY_REGISTERED,
                    "duplicate email"
                )
            )
        }

        // M01: AccountType de UI se ignora como decisión de negocio; default técnico PERSON.
        val effectiveType = AccountType.PERSON

        MockAuthDatabase.save(
            AuthAccount(
                email = normalizedEmail,
                password = password,
                name = name.trim(),
                emailVerified = false
            )
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
        // No sesión "Authenticated" hasta verificar (observeAuthState filtra no verificados).
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
        AuthValidators.validateEmail(email).getOrElse {
            return Result.failure(AuthErrorMapper.fromThrowableToException(it))
        }
        AuthValidators.validatePassword(newPassword).getOrElse {
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
        val code = otpCode.trim()
        if (code.length < 6) {
            return Result.failure(
                AuthErrorMapper.toException(
                    AuthErrorCode.RECOVERY_LINK_INVALID,
                    "otp too short"
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