package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.AccountType
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.remote.supabase.SupabaseAuthConfig
import com.comunidapp.app.data.remote.supabase.UserSupabaseDataSource
import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.domain.auth.AuthErrorCode
import com.comunidapp.app.domain.auth.AuthErrorMapper
import com.comunidapp.app.domain.auth.ConsentMetadata
import com.comunidapp.app.domain.auth.validation.AuthValidators
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SupabaseAuthRepository(
    private val userDataSource: UserSupabaseDataSource = UserSupabaseDataSource()
) : AuthRepository {

    override suspend fun login(email: String, password: String): Result<User> {
        AuthValidators.validateEmail(email).getOrElse {
            return Result.failure(AuthErrorMapper.fromThrowableToException(it))
        }
        if (password.isEmpty()) {
            return Result.failure(
                AuthErrorMapper.toException(AuthErrorCode.INVALID_CREDENTIALS, "empty password")
            )
        }
        val normalizedEmail = AuthValidators.normalizeEmail(email)
        return try {
            supabase.auth.signInWith(Email) {
                this.email = normalizedEmail
                this.password = password
            }
            supabase.auth.refreshCurrentSession()
            val authUser = supabase.auth.currentUserOrNull()
                ?: return Result.failure(
                    AuthErrorMapper.toException(AuthErrorCode.INVALID_CREDENTIALS, "no session")
                )

            if (!authUser.isEmailConfirmed()) {
                return Result.failure(
                    AuthErrorMapper.toException(AuthErrorCode.EMAIL_NOT_VERIFIED, "email not confirmed")
                )
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
        consent: ConsentMetadata,
        accountType: AccountType
    ): Result<User> {
        if (name.isBlank()) {
            return Result.failure(
                AuthErrorMapper.toException(AuthErrorCode.UNKNOWN_AUTH_ERROR, "name required")
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
        val normalizedEmail = AuthValidators.normalizeEmail(email)
        val effectiveType = AccountType.PERSON
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
                    put("terms_version", consent.termsVersion)
                    put("privacy_version", consent.privacyVersion)
                    put("consent_source", consent.source)
                    consent.locale?.takeIf { it.isNotBlank() }?.let { put("consent_locale", it) }
                }
            }

            val authUser = signedUpUser ?: supabase.auth.currentUserOrNull()
                ?: return Result.failure(
                    AuthErrorMapper.toException(AuthErrorCode.UNKNOWN_AUTH_ERROR, "signup failed")
                )

            val user = User(
                id = authUser.id,
                name = trimmedName,
                email = normalizedEmail,
                accountType = effectiveType
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
        AuthValidators.validateEmail(email).getOrElse {
            return Result.failure(AuthErrorMapper.fromThrowableToException(it))
        }
        val normalizedEmail = AuthValidators.normalizeEmail(email)
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
        // Etapa 4: completar updateUser post-deep-link. No devolver falso éxito.
        return Result.failure(
            AuthErrorMapper.toException(
                AuthErrorCode.PASSWORD_RESET_NOT_AVAILABLE,
                "Password reset is not available in-app until M01 stage 4"
            )
        )
    }

    override suspend fun sendEmailVerification(email: String): Result<Unit> {
        val normalizedEmail = AuthValidators.normalizeEmail(email)
        return try {
            val sessionUser = supabase.auth.currentUserOrNull()
            if (sessionUser != null) {
                if (!sessionUser.email.equals(normalizedEmail, ignoreCase = true)) {
                    return Result.failure(
                        AuthErrorMapper.toException(
                            AuthErrorCode.INVALID_CREDENTIALS,
                            "email mismatch with session"
                        )
                    )
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
        val normalizedEmail = AuthValidators.normalizeEmail(email)
        return try {
            val sessionUser = supabase.auth.currentUserOrNull()
            if (sessionUser == null) {
                return Result.failure(
                    AuthErrorMapper.toException(
                        AuthErrorCode.RECOVERY_LINK_INVALID,
                        "no session for confirm email"
                    )
                )
            }
            if (!sessionUser.email.equals(normalizedEmail, ignoreCase = true)) {
                return Result.failure(
                    AuthErrorMapper.toException(
                        AuthErrorCode.INVALID_CREDENTIALS,
                        "email mismatch"
                    )
                )
            }
            supabase.auth.refreshCurrentSession()
            val refreshed = supabase.auth.currentUserOrNull()
            if (refreshed?.isEmailConfirmed() == true) {
                userDataSource.updateEmailVerified(refreshed.id, true)
                supabase.auth.signOut()
                Result.success(Unit)
            } else {
                Result.failure(
                    AuthErrorMapper.toException(
                        AuthErrorCode.EMAIL_NOT_VERIFIED,
                        "still not confirmed"
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(mapSupabaseException(e))
        }
    }

    override suspend fun verifyEmailOtp(email: String, otpCode: String): Result<Unit> {
        val normalizedEmail = AuthValidators.normalizeEmail(email)
        val token = otpCode.trim()
        if (token.length < 6) {
            return Result.failure(
                AuthErrorMapper.toException(AuthErrorCode.RECOVERY_LINK_INVALID, "otp too short")
            )
        }
        return try {
            supabase.auth.verifyEmailOtp(
                type = OtpType.Email.SIGNUP,
                email = normalizedEmail,
                token = token
            )
            val authUser = supabase.auth.currentUserOrNull()
            if (authUser != null) {
                userDataSource.updateEmailVerified(authUser.id, true)
            }
            supabase.auth.signOut()
            Result.success(Unit)
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
        return authUser.toUser()
    }

    override suspend fun logout() {
        supabase.auth.signOut()
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
        accountType = AccountType.fromString(
            userMetadata?.get("account_type")?.toString()?.trim('"')
        ),
        emailVerified = isEmailConfirmed()
    )

    private fun UserInfo.isEmailConfirmed(): Boolean =
        emailConfirmedAt != null

    private fun mapSupabaseException(e: Exception): Exception =
        AuthErrorMapper.fromThrowableToException(e)
}
