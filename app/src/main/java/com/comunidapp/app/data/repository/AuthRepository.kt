package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.AccountType
import com.comunidapp.app.data.model.AuthAccount
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.mock.MockAuthDatabase
import com.comunidapp.app.data.mock.MockData
import com.comunidapp.app.data.mock.MockUserStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class EmailNotVerifiedException(email: String) :
    Exception("Debés confirmar tu email ($email) antes de iniciar sesión.")

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
    fun logout()
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

    override suspend fun login(email: String, password: String): Result<User> {
        delay(800)
        val normalizedEmail = email.trim().lowercase()
        if (normalizedEmail.isBlank() || password.isBlank()) {
            return Result.failure(IllegalArgumentException("Email y contraseña son requeridos"))
        }

        val account = MockAuthDatabase.findByEmail(normalizedEmail)
        return if (account != null) {
            when {
                account.password != password ->
                    Result.failure(IllegalArgumentException("Email o contraseña incorrectos"))
                !account.emailVerified ->
                    Result.failure(EmailNotVerifiedException(normalizedEmail))
                else -> {
                    val userId = resolveMockUserId(normalizedEmail)
                    val stored = MockUserStore.get(userId)
                    val user = stored ?: User(
                        id = userId,
                        name = account.name,
                        email = account.email
                    )
                    setLoggedInUser(user)
                    Result.success(user)
                }
            }
        } else {
            val user = MockData.currentUser.copy(email = normalizedEmail)
            setLoggedInUser(user)
            Result.success(user)
        }
    }

    override suspend fun register(
        name: String,
        email: String,
        password: String,
        accountType: AccountType
    ): Result<User> {
        delay(800)
        val normalizedEmail = email.trim().lowercase()
        if (name.isBlank() || normalizedEmail.isBlank() || password.isBlank()) {
            return Result.failure(IllegalArgumentException("Todos los campos son requeridos"))
        }
        if (password.length < 6) {
            return Result.failure(IllegalArgumentException("La contraseña debe tener al menos 6 caracteres"))
        }
        if (MockAuthDatabase.findByEmail(normalizedEmail) != null) {
            return Result.failure(IllegalArgumentException("Ya existe una cuenta con ese email"))
        }

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
            accountType = accountType
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
        delay(600)
        val normalizedEmail = email.trim().lowercase()
        if (normalizedEmail.isBlank()) {
            return Result.failure(IllegalArgumentException("Ingresá tu email"))
        }
        if (MockAuthDatabase.findByEmail(normalizedEmail) == null) {
            return Result.failure(IllegalArgumentException("No encontramos una cuenta con ese email"))
        }
        MockAuthDatabase.generateResetToken(normalizedEmail)
        return Result.success(Unit)
    }

    override suspend fun resetPassword(
        email: String,
        token: String,
        newPassword: String
    ): Result<Unit> {
        delay(600)
        val normalizedEmail = email.trim().lowercase()
        if (newPassword.length < 6) {
            return Result.failure(IllegalArgumentException("La contraseña debe tener al menos 6 caracteres"))
        }
        if (!MockAuthDatabase.isValidResetToken(normalizedEmail, token)) {
            return Result.failure(IllegalArgumentException("Código inválido o expirado"))
        }
        MockAuthDatabase.updatePassword(normalizedEmail, newPassword)
        return Result.success(Unit)
    }

    override suspend fun sendEmailVerification(email: String): Result<Unit> {
        delay(500)
        if (MockAuthDatabase.findByEmail(email.trim().lowercase()) == null) {
            return Result.failure(IllegalArgumentException("Cuenta no encontrada"))
        }
        return Result.success(Unit)
    }

    override suspend fun confirmEmailVerification(email: String): Result<Unit> {
        delay(400)
        val normalizedEmail = email.trim().lowercase()
        val account = MockAuthDatabase.findByEmail(normalizedEmail)
            ?: return Result.failure(IllegalArgumentException("Cuenta no encontrada"))
        MockAuthDatabase.setEmailVerified(normalizedEmail, true)
        return Result.success(Unit)
    }

    override suspend fun verifyEmailOtp(email: String, otpCode: String): Result<Unit> {
        delay(400)
        val normalizedEmail = email.trim().lowercase()
        val code = otpCode.trim()
        if (code.length < 6) {
            return Result.failure(IllegalArgumentException("Ingresá el código de 6 dígitos del email"))
        }
        val account = MockAuthDatabase.findByEmail(normalizedEmail)
            ?: return Result.failure(IllegalArgumentException("Cuenta no encontrada"))
        MockAuthDatabase.setEmailVerified(normalizedEmail, true)
        return Result.success(Unit)
    }

    override suspend fun isEmailVerified(email: String): Boolean {
        return MockAuthDatabase.findByEmail(email.trim().lowercase())?.emailVerified == true
    }

    override fun getCurrentUser(): User? = _authState.value

    override fun logout() {
        setLoggedInUser(null)
    }
}
