package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.AuthAccount
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.mock.MockAuthDatabase
import com.comunidapp.app.data.mock.MockData
import kotlinx.coroutines.delay

class EmailNotVerifiedException(email: String) :
    Exception("Debés confirmar tu email ($email) antes de iniciar sesión.")

interface AuthRepository {
    suspend fun login(email: String, password: String): Result<User>
    suspend fun register(name: String, email: String, password: String): Result<User>
    suspend fun sendPasswordResetEmail(email: String): Result<Unit>
    suspend fun resetPassword(email: String, token: String, newPassword: String): Result<Unit>
    suspend fun sendEmailVerification(email: String): Result<Unit>
    suspend fun confirmEmailVerification(email: String): Result<Unit>
    suspend fun isEmailVerified(email: String): Boolean
    fun getCurrentUser(): User?
    fun logout()
}

class MockAuthRepository : AuthRepository {

    private var loggedInUser: User? = null

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
                    loggedInUser = User(
                        id = "user_${normalizedEmail.hashCode()}",
                        name = account.name,
                        email = account.email
                    )
                    Result.success(loggedInUser!!)
                }
            }
        } else {
            loggedInUser = MockData.currentUser.copy(email = normalizedEmail)
            Result.success(loggedInUser!!)
        }
    }

    override suspend fun register(name: String, email: String, password: String): Result<User> {
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

        val user = User(id = "user_new", name = name.trim(), email = normalizedEmail)
        loggedInUser = user
        return Result.success(user)
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

    override suspend fun isEmailVerified(email: String): Boolean {
        return MockAuthDatabase.findByEmail(email.trim().lowercase())?.emailVerified == true
    }

    override fun getCurrentUser(): User? = loggedInUser

    override fun logout() {
        loggedInUser = null
    }
}
