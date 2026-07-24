package com.comunidapp.app.data.repository

import com.comunidapp.app.BuildConfig
import com.comunidapp.app.core.logging.AppLog
import com.comunidapp.app.data.model.AccountType
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.remote.supabase.SupabaseAuthConfig
import com.comunidapp.app.data.remote.supabase.UserSupabaseDataSource
import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.domain.auth.AuthErrorCode
import com.comunidapp.app.domain.auth.AuthErrorMapper
import com.comunidapp.app.domain.auth.ConsentMetadata
import com.comunidapp.app.domain.auth.LegalDocumentConfig
import com.comunidapp.app.domain.auth.validation.AuthValidators
import com.comunidapp.app.domain.auth.validation.EmailOtpValidators
import com.comunidapp.app.notifications.PushTokenRegistrar
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class SupabaseAuthRepository(
    private val userDataSource: UserSupabaseDataSource = UserSupabaseDataSource()
) : AuthRepository {

    private var reauthFailures = 0

    override suspend fun login(email: String, password: String): Result<User> {
        if (!com.comunidapp.app.core.config.SupabaseUrlPolicy.credentialsPresent()) {
            com.comunidapp.app.core.config.AuthConfigDiagnostics.logSafe("login_config_invalid")
            return Result.failure(
                AuthErrorMapper.toException(
                    AuthErrorCode.CONFIGURATION_ERROR,
                    "supabase credentials missing or non-remote"
                )
            )
        }
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
            com.comunidapp.app.core.config.AuthConfigDiagnostics.logSafe(
                "login_failure",
                exceptionClass = e::class.java.simpleName
            )
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
    ): Result<Unit> = updatePasswordFromRecovery(newPassword)

    override suspend fun updatePasswordFromRecovery(newPassword: String): Result<Unit> {
        AuthValidators.validatePassword(newPassword).getOrElse {
            return Result.failure(AuthErrorMapper.fromThrowableToException(it))
        }
        val session = supabase.auth.currentSessionOrNull()
            ?: return Result.failure(
                AuthErrorMapper.toException(
                    AuthErrorCode.PASSWORD_RESET_NOT_AVAILABLE,
                    "no recovery session"
                )
            )
        if (session.user == null) {
            return Result.failure(
                AuthErrorMapper.toException(
                    AuthErrorCode.PASSWORD_RESET_NOT_AVAILABLE,
                    "session without user"
                )
            )
        }
        return try {
            supabase.auth.updateUser {
                password = newPassword
            }
            // Invalidar sesión de recovery tras uso (anti doble envío).
            runCatching { supabase.auth.signOut() }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(mapSupabaseException(e))
        }
    }

    override suspend fun changePassword(
        currentPassword: String,
        newPassword: String
    ): Result<Unit> {
        AuthValidators.validatePassword(newPassword).getOrElse {
            return Result.failure(AuthErrorMapper.fromThrowableToException(it))
        }
        val user = supabase.auth.currentUserOrNull()
            ?: return Result.failure(
                AuthErrorMapper.toException(AuthErrorCode.SESSION_EXPIRED, "no session")
            )
        val email = user.email
            ?: return Result.failure(
                AuthErrorMapper.toException(AuthErrorCode.SESSION_EXPIRED, "no email on user")
            )
        return try {
            // Reautenticación: verificar contraseña actual con sign-in (email/password).
            supabase.auth.signInWith(Email) {
                this.email = email
                this.password = currentPassword
            }
            reauthFailures = 0
            supabase.auth.updateUser {
                password = newPassword
            }
            Result.success(Unit)
        } catch (e: Exception) {
            reauthFailures += 1
            if (reauthFailures >= 5) {
                return Result.failure(
                    AuthErrorMapper.toException(AuthErrorCode.RATE_LIMITED, "too many reauth failures")
                )
            }
            Result.failure(mapSupabaseException(e))
        }
    }

    override suspend fun hasCurrentLegalConsent(userId: String): Boolean {
        return try {
            val rows = supabase.from(USER_CONSENTS_TABLE)
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("terms_version", LegalDocumentConfig.terms.version)
                        eq("privacy_version", LegalDocumentConfig.privacy.version)
                    }
                }
                .decodeList<UserConsentRow>()
            rows.isNotEmpty()
        } catch (e: Exception) {
            // Migración 014 aún no aplicada en remoto: no inventar consentimiento ni brickear.
            AppLog.warning(TAG, "user_consents query unavailable; skipping gate until migration", e)
            true
        }
    }

    override suspend fun acceptLegalConsents(consent: ConsentMetadata): Result<Unit> {
        AuthValidators.validateConsents(
            acceptedTerms = true,
            acceptedPrivacy = true,
            termsVersion = consent.termsVersion,
            privacyVersion = consent.privacyVersion
        ).getOrElse {
            return Result.failure(AuthErrorMapper.fromThrowableToException(it))
        }
        if (supabase.auth.currentUserOrNull() == null) {
            return Result.failure(
                AuthErrorMapper.toException(AuthErrorCode.SESSION_EXPIRED, "no session")
            )
        }
        return try {
            supabase.postgrest.rpc(
                function = "accept_legal_consents",
                parameters = buildJsonObject {
                    put("p_terms_version", consent.termsVersion)
                    put("p_privacy_version", consent.privacyVersion)
                    consent.locale?.let { put("p_locale", it) }
                    put(
                        "p_source",
                        consent.source.ifBlank { ConsentMetadata.SOURCE_POST_LOGIN_GATE }
                    )
                }
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(mapSupabaseException(e))
        }
    }

    override suspend fun deleteAccount(idempotencyKey: String): Result<Unit> {
        if (idempotencyKey.isBlank()) {
            return Result.failure(
                AuthErrorMapper.toException(
                    AuthErrorCode.ACCOUNT_DELETION_FAILED,
                    "idempotency key required"
                )
            )
        }
        val session = supabase.auth.currentSessionOrNull()
            ?: return Result.failure(
                AuthErrorMapper.toException(AuthErrorCode.SESSION_EXPIRED, "no session")
            )
        val accessToken = session.accessToken
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("${BuildConfig.SUPABASE_URL.trimEnd('/')}/functions/v1/delete-account")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 30_000
                    readTimeout = 60_000
                    doOutput = true
                    setRequestProperty("Authorization", "Bearer $accessToken")
                    setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Idempotency-Key", idempotencyKey)
                }
                // Nunca enviar user_id: la función deriva UID del JWT.
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write("{}") }
                val code = conn.responseCode
                val body = runCatching {
                    (if (code in 200..299) conn.inputStream else conn.errorStream)
                        ?.bufferedReader()
                        ?.readText()
                        .orEmpty()
                }.getOrDefault("")
                conn.disconnect()
                if (code in 200..299) {
                    runCatching { supabase.auth.signOut() }
                    Result.success(Unit)
                } else {
                    Result.failure(
                        AuthErrorMapper.toException(
                            AuthErrorCode.ACCOUNT_DELETION_FAILED,
                            "delete-account HTTP $code ${body.take(120)}"
                        )
                    )
                }
            } catch (e: Exception) {
                Result.failure(mapSupabaseException(e))
            }
        }
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
        val token = EmailOtpValidators.validate(otpCode).getOrElse { err ->
            return Result.failure(
                AuthErrorMapper.toException(
                    AuthErrorCode.RECOVERY_LINK_INVALID,
                    err.message ?: "otp invalid"
                )
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
        runCatching { PushTokenRegistrar.unlinkForCurrentUser() }
        runCatching { supabase.auth.signOut() }
            .onFailure { error ->
                val mapped = AuthErrorMapper.fromThrowable(error)
                // Refresh/sesión inválida: limpiar localmente sin brickear en AuthError.
                if (mapped.code == AuthErrorCode.SESSION_EXPIRED.name ||
                    mapped.code == AuthErrorCode.NETWORK_UNAVAILABLE.name
                ) {
                    AppLog.warning(TAG, "signOut cleaned after ${mapped.code}")
                } else {
                    throw AuthErrorMapper.fromThrowableToException(error)
                }
            }
    }

    override fun observeAuthState(): Flow<User?> =
        supabase.auth.sessionStatus.map { status ->
            when (status) {
                is SessionStatus.Authenticated -> {
                    val authUser = status.session.user ?: return@map null
                    if (authUser.isEmailConfirmed()) authUser.toUser() else null
                }
                // Sin sesión válida → null (Unauthenticated). No elevar a AuthError permanente.
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

    @Serializable
    private data class UserConsentRow(
        val id: String? = null
    )

    companion object {
        private const val TAG = "SupabaseAuthRepository"
        private const val USER_CONSENTS_TABLE = "user_consents"
    }
}
