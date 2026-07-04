package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.AccountType
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.remote.supabase.UserSupabaseDataSource
import com.comunidapp.app.data.remote.supabase.supabase
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

class SupabaseAuthRepository(
    private val userDataSource: UserSupabaseDataSource = UserSupabaseDataSource()
) : AuthRepository {

    override suspend fun login(email: String, password: String): Result<User> {
        val normalizedEmail = email.trim().lowercase()
        if (normalizedEmail.isBlank() || password.isBlank()) {
            return Result.failure(IllegalArgumentException("Email y contraseña son requeridos"))
        }
        return try {
            supabase.auth.signInWith(Email) {
                this.email = normalizedEmail
                this.password = password
            }
            supabase.auth.refreshCurrentSession()
            val authUser = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalArgumentException("Error al iniciar sesión"))

            if (!authUser.isEmailConfirmed()) {
                return Result.failure(EmailNotVerifiedException(normalizedEmail))
            }

            val profile = fetchUserProfile(authUser, normalizedEmail)
            Result.success(profile)
        } catch (e: Exception) {
            Result.failure(mapSupabaseException(e))
        }
    }

    override suspend fun register(
        name: String,
        email: String,
        password: String,
        accountType: AccountType
    ): Result<User> {
        val normalizedEmail = email.trim().lowercase()
        if (name.isBlank() || normalizedEmail.isBlank() || password.isBlank()) {
            return Result.failure(IllegalArgumentException("Todos los campos son requeridos"))
        }
        return try {
            supabase.auth.signUpWith(Email) {
                this.email = normalizedEmail
                this.password = password
            }

            val authUser = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalArgumentException("Error al crear la cuenta"))

            val user = User(
                id = authUser.id,
                name = name.trim(),
                email = normalizedEmail,
                accountType = accountType
            )
            userDataSource.createUser(user)
            Result.success(user)
        } catch (e: Exception) {
            supabase.auth.signOut()
            Result.failure(mapSupabaseException(e))
        }
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        val normalizedEmail = email.trim().lowercase()
        if (normalizedEmail.isBlank()) {
            return Result.failure(IllegalArgumentException("Ingresá tu email"))
        }
        return try {
            supabase.auth.resetPasswordForEmail(normalizedEmail)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(mapSupabaseException(e))
        }
    }

    override suspend fun resetPassword(
        email: String,
        token: String,
        newPassword: String
    ): Result<Unit> {
        return Result.failure(
            IllegalArgumentException("Abrí el link que te enviamos por email para restablecer tu contraseña")
        )
    }

    override suspend fun sendEmailVerification(email: String): Result<Unit> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalArgumentException("No hay sesión activa para reenviar el email"))
            if (!user.email.equals(email.trim(), ignoreCase = true)) {
                return Result.failure(IllegalArgumentException("El email no coincide con la sesión activa"))
            }
            supabase.auth.resendEmail(OtpType.Email.SIGNUP, user.email ?: email.trim())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(mapSupabaseException(e))
        }
    }

    override suspend fun confirmEmailVerification(email: String): Result<Unit> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(
                    IllegalArgumentException("Iniciá sesión con tu email y contraseña para verificar.")
                )
            if (!user.email.equals(email.trim(), ignoreCase = true)) {
                return Result.failure(IllegalArgumentException("El email no coincide con la sesión activa"))
            }
            supabase.auth.refreshCurrentSession()
            val refreshed = supabase.auth.currentUserOrNull()
            if (refreshed?.isEmailConfirmed() == true) {
                userDataSource.updateEmailVerified(refreshed.id, true)
                supabase.auth.signOut()
                Result.success(Unit)
            } else {
                Result.failure(
                    IllegalArgumentException("Tu email aún no está confirmado. Revisá tu bandeja de entrada y spam.")
                )
            }
        } catch (e: Exception) {
            Result.failure(mapSupabaseException(e))
        }
    }

    override suspend fun isEmailVerified(email: String): Boolean {
        val user = supabase.auth.currentUserOrNull() ?: return false
        if (!user.email.equals(email.trim(), ignoreCase = true)) return false
        supabase.auth.refreshCurrentSession()
        return supabase.auth.currentUserOrNull()?.isEmailConfirmed() == true
    }

    override fun getCurrentUser(): User? {
        val authUser = supabase.auth.currentUserOrNull() ?: return null
        return User(
            id = authUser.id,
            name = authUser.userMetadata?.get("name")?.toString()?.trim('"').orEmpty(),
            email = authUser.email.orEmpty()
        )
    }

    override fun logout() {
        runBlocking { supabase.auth.signOut() }
    }

    override fun observeAuthState(): Flow<User?> =
        supabase.auth.sessionStatus.map { status ->
            when (status) {
                is SessionStatus.Authenticated -> {
                    val authUser = status.session.user ?: return@map null
                    if (authUser.isEmailConfirmed()) authUser.toUser() else null
                }
                else -> null
            }
        }

    private suspend fun fetchUserProfile(authUser: UserInfo, email: String): User {
        return userDataSource.getUser(authUser.id) ?: User(
            id = authUser.id,
            name = authUser.userMetadata?.get("name")?.toString()?.trim('"').orEmpty(),
            email = email,
            accountType = AccountType.PERSON,
            emailVerified = authUser.isEmailConfirmed()
        )
    }

    private fun UserInfo.toUser(): User = User(
        id = id,
        name = userMetadata?.get("name")?.toString()?.trim('"').orEmpty(),
        email = email.orEmpty(),
        emailVerified = isEmailConfirmed()
    )

    private fun UserInfo.isEmailConfirmed(): Boolean =
        emailConfirmedAt != null

    private fun mapSupabaseException(e: Exception): Exception {
        val message = when {
            e.message?.contains("Invalid login credentials", ignoreCase = true) == true ->
                "Email o contraseña incorrectos"
            e.message?.contains("User already registered", ignoreCase = true) == true ->
                "Ya existe una cuenta con ese email"
            e.message?.contains("Password should be at least", ignoreCase = true) == true ->
                "La contraseña debe tener al menos 6 caracteres"
            e.message?.contains("Unable to validate email", ignoreCase = true) == true ->
                "No encontramos una cuenta con ese email"
            else -> e.message ?: "Ocurrió un error. Intentá de nuevo."
        }
        return IllegalArgumentException(message)
    }
}
