package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.AccountType
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.remote.supabase.SupabaseAuthConfig
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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
            val trimmedName = name.trim()
            val signedUpUser = supabase.auth.signUpWith(
                Email,
                redirectUrl = SupabaseAuthConfig.REDIRECT_URL
            ) {
                this.email = normalizedEmail
                this.password = password
                data = buildJsonObject {
                    put("name", trimmedName)
                    put("account_type", accountType.name)
                }
            }

            val authUser = signedUpUser ?: supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalArgumentException("Error al crear la cuenta"))

            val user = User(
                id = authUser.id,
                name = trimmedName,
                email = normalizedEmail,
                accountType = accountType
            )

            if (supabase.auth.currentUserOrNull() != null) {
                userDataSource.createUser(user).onFailure { /* trigger may have created profile */ }
            }

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(mapSupabaseException(e))
        }
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        val normalizedEmail = email.trim().lowercase()
        if (normalizedEmail.isBlank()) {
            return Result.failure(IllegalArgumentException("Ingresá tu email"))
        }
        return try {
            supabase.auth.resetPasswordForEmail(
                normalizedEmail,
                redirectUrl = SupabaseAuthConfig.REDIRECT_URL
            )
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
        val normalizedEmail = email.trim().lowercase()
        return try {
            val sessionUser = supabase.auth.currentUserOrNull()
            if (sessionUser != null) {
                if (!sessionUser.email.equals(normalizedEmail, ignoreCase = true)) {
                    return Result.failure(IllegalArgumentException("El email no coincide con la sesión activa"))
                }
                supabase.auth.resendEmail(OtpType.Email.SIGNUP, sessionUser.email ?: normalizedEmail)
            } else {
                supabase.auth.resendEmail(OtpType.Email.SIGNUP, normalizedEmail)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(mapSupabaseException(e))
        }
    }

    override suspend fun confirmEmailVerification(email: String): Result<Unit> {
        val normalizedEmail = email.trim().lowercase()
        return try {
            val sessionUser = supabase.auth.currentUserOrNull()
            if (sessionUser == null) {
                return Result.failure(
                    IllegalArgumentException(
                        "Abrí el link del email para confirmar tu cuenta, o iniciá sesión y tocá \"Verificar ahora\"."
                    )
                )
            }
            if (!sessionUser.email.equals(normalizedEmail, ignoreCase = true)) {
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
                    IllegalArgumentException(
                        "Tu email aún no está confirmado. Abrí el link del email o revisá spam."
                    )
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
        val raw = e.message.orEmpty()
        val message = when {
            raw.contains("rate limit", ignoreCase = true) ||
                raw.contains("over_email_send_rate_limit", ignoreCase = true) ||
                raw.contains("email rate limit exceeded", ignoreCase = true) ->
                "Supabase limitó el envío de emails (demasiados intentos de registro o reenvío). " +
                    "Esperá 30–60 minutos, usá otro email, o desactivá temporalmente " +
                    "'Confirm email' en Supabase → Authentication → Providers → Email."
            raw.contains("Invalid login credentials", ignoreCase = true) ->
                "Email o contraseña incorrectos"
            raw.contains("User already registered", ignoreCase = true) ||
                raw.contains("already been registered", ignoreCase = true) ->
                "Ya existe una cuenta con ese email. Revisá tu bandeja o iniciá sesión."
            raw.contains("Password should be at least", ignoreCase = true) ->
                "La contraseña debe tener al menos 6 caracteres"
            raw.contains("Unable to validate email", ignoreCase = true) ->
                "No encontramos una cuenta con ese email"
            else -> raw.ifBlank { "Ocurrió un error. Intentá de nuevo." }
        }
        return IllegalArgumentException(message)
    }
}
