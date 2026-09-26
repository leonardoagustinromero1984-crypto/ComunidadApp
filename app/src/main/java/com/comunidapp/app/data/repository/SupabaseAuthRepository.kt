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
import com.comunidapp.app.domain.auth.AuthException
import com.comunidapp.app.domain.auth.ConsentMetadata
import com.comunidapp.app.domain.auth.GoogleAuthPolicy
import com.comunidapp.app.domain.auth.GoogleAuthTrace
import com.comunidapp.app.domain.auth.LegalDocumentConfig
import com.comunidapp.app.domain.auth.validation.AuthValidators
import com.comunidapp.app.domain.auth.validation.EmailOtpValidators
import com.comunidapp.app.notifications.PushTokenRegistrar
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
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
                runCatching { supabase.auth.signOut() }
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

    override suspend fun loginAdministrative(username: String, password: String): Result<User> {
        if (!com.comunidapp.app.core.config.SupabaseUrlPolicy.credentialsPresent()) {
            return Result.failure(
                AuthErrorMapper.toException(
                    AuthErrorCode.CONFIGURATION_ERROR,
                    "supabase credentials missing or non-remote"
                )
            )
        }
        if (username.trim().length < 3 || password.isEmpty()) {
            return Result.failure(
                AuthErrorMapper.toException(
                    AuthErrorCode.INVALID_CREDENTIALS,
                    "Usuario o contraseña incorrectos."
                )
            )
        }
        return try {
            val element = supabase.postgrest.rpc(
                function = "admin_begin_login",
                parameters = buildJsonObject {
                    put("p_username", username.trim())
                    put("p_password", password)
                }
            ).decodeAs<kotlinx.serialization.json.JsonElement>()
            val email = runCatching {
                kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                    .decodeFromJsonElement(AdminBeginLoginRow.serializer(), element)
                    .email
            }.getOrNull()
            if (email.isNullOrBlank()) {
                return Result.failure(
                    AuthErrorMapper.toException(
                        AuthErrorCode.INVALID_CREDENTIALS,
                        "Usuario o contraseña incorrectos."
                    )
                )
            }
            supabase.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            val authUser = supabase.auth.currentUserOrNull()
                ?: return Result.failure(
                    AuthErrorMapper.toException(
                        AuthErrorCode.INVALID_CREDENTIALS,
                        "Usuario o contraseña incorrectos."
                    )
                )
            Result.success(authUser.toUser())
        } catch (_: Exception) {
            runCatching { supabase.auth.signOut() }
            Result.failure(
                AuthErrorMapper.toException(
                    AuthErrorCode.INVALID_CREDENTIALS,
                    "Usuario o contraseña incorrectos."
                )
            )
        }
    }

    override suspend fun loginWithUsername(username: String, password: String): Result<User> {
        if (!com.comunidapp.app.core.config.SupabaseUrlPolicy.credentialsPresent()) {
            return Result.failure(
                AuthErrorMapper.toException(
                    AuthErrorCode.CONFIGURATION_ERROR,
                    "supabase credentials missing or non-remote"
                )
            )
        }
        val normalized = com.comunidapp.app.domain.user.UsernameValidators.normalize(username)
        if (normalized.length < 3 || password.isEmpty()) {
            return Result.failure(
                AuthErrorMapper.toException(
                    AuthErrorCode.INVALID_CREDENTIALS,
                    "Usuario o contraseña incorrectos."
                )
            )
        }
        return try {
            val element = supabase.postgrest.rpc(
                function = com.comunidapp.app.domain.canonical.CanonicalBackend.RPC_BEGIN_USERNAME_LOGIN,
                parameters = buildJsonObject {
                    put("p_username", normalized)
                    put("p_password", password)
                }
            ).decodeAs<kotlinx.serialization.json.JsonElement>()
            val email = runCatching {
                kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                    .decodeFromJsonElement(AdminBeginLoginRow.serializer(), element)
                    .email
            }.getOrNull()
            if (email.isNullOrBlank()) {
                return Result.failure(
                    AuthErrorMapper.toException(
                        AuthErrorCode.INVALID_CREDENTIALS,
                        "Usuario o contraseña incorrectos."
                    )
                )
            }
            supabase.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            val authUser = supabase.auth.currentUserOrNull()
                ?: return Result.failure(
                    AuthErrorMapper.toException(
                        AuthErrorCode.INVALID_CREDENTIALS,
                        "Usuario o contraseña incorrectos."
                    )
                )
            if (!authUser.isEmailConfirmed()) {
                runCatching { supabase.auth.signOut() }
                return Result.failure(
                    AuthErrorMapper.toException(AuthErrorCode.EMAIL_NOT_VERIFIED, email)
                )
            }
            Result.success(fetchUserProfile(authUser, email))
        } catch (e: Exception) {
            runCatching { supabase.auth.signOut() }
            if (e is AuthException && e.code == AuthErrorCode.EMAIL_NOT_VERIFIED.name) {
                return Result.failure(e)
            }
            Result.failure(
                AuthErrorMapper.toException(
                    AuthErrorCode.INVALID_CREDENTIALS,
                    "Usuario o contraseña incorrectos."
                )
            )
        }
    }

    override suspend fun createGoogleOAuthUrl(): Result<String> {
        if (!com.comunidapp.app.core.config.SupabaseUrlPolicy.credentialsPresent()) {
            return Result.failure(
                AuthErrorMapper.toException(
                    AuthErrorCode.CONFIGURATION_ERROR,
                    "supabase credentials missing or non-remote"
                )
            )
        }
        return try {
            val url = supabase.auth.getOAuthUrl(
                provider = Google,
                redirectUrl = SupabaseAuthConfig.REDIRECT_URL
            ) {
                queryParams["prompt"] = GoogleAuthPolicy.OAUTH_PROMPT_SELECT_ACCOUNT
            }
            if (url.isBlank() || !url.startsWith("https://", ignoreCase = true)) {
                GoogleAuthTrace.event("GOOGLE-ERROR=BAD_URL")
                return Result.failure(
                    AuthErrorMapper.toException(AuthErrorCode.GOOGLE_AUTH_FAILED, "google oauth url missing")
                )
            }
            Result.success(url)
        } catch (e: Exception) {
            com.comunidapp.app.core.config.AuthConfigDiagnostics.logSafe(
                "google_oauth_url_failure",
                exceptionClass = e::class.java.simpleName
            )
            GoogleAuthTrace.event("GOOGLE-ERROR=FAILED")
            Result.failure(mapSupabaseException(e))
        }
    }

    override suspend fun signInWithGoogle(): Result<User> {
        return Result.failure(
            AuthErrorMapper.toException(
                AuthErrorCode.CONFIGURATION_ERROR,
                "signInWith(Google) auto-open disconnected"
            )
        )
    }

    override suspend fun signInWithGoogleIdToken(idToken: String): Result<User> {
        return Result.failure(
            AuthErrorMapper.toException(
                AuthErrorCode.CONFIGURATION_ERROR,
                "google id token path disconnected"
            )
        )
    }

    override fun linkedAuthMethods(): List<com.comunidapp.app.domain.auth.AuthMethodKind> {
        val identities = supabase.auth.currentUserOrNull()?.identities.orEmpty()
        if (identities.isEmpty()) {
            val email = supabase.auth.currentUserOrNull()?.email
            return if (email.isNullOrBlank()) {
                emptyList()
            } else {
                listOf(com.comunidapp.app.domain.auth.AuthMethodKind.EMAIL_PASSWORD_OTP)
            }
        }
        return identities.mapNotNull { identity ->
            when (identity.provider.lowercase()) {
                "google" -> com.comunidapp.app.domain.auth.AuthMethodKind.GOOGLE
                "email" -> com.comunidapp.app.domain.auth.AuthMethodKind.EMAIL_PASSWORD_OTP
                "apple" -> com.comunidapp.app.domain.auth.AuthMethodKind.APPLE
                else -> null
            }
        }.distinct()
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
        if (name.isBlank()) {
            return Result.failure(
                AuthErrorMapper.toException(AuthErrorCode.UNKNOWN_AUTH_ERROR, "name required")
            )
        }
        val normalizedUsername = com.comunidapp.app.domain.user.UsernameValidators.validate(username)
            .getOrElse {
                return Result.failure(
                    AuthErrorMapper.toException(AuthErrorCode.UNKNOWN_AUTH_ERROR, "username invalid")
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
        val personAge = com.comunidapp.app.domain.user.PersonAgeRules
            .validateSignupBirthDate(birthDate)
            .getOrElse {
                return Result.failure(
                    AuthErrorMapper.toException(
                        AuthErrorCode.UNKNOWN_AUTH_ERROR,
                        it.message ?: "BIRTH_DATE_INVALID"
                    )
                )
            }
        val normalizedEmail = AuthValidators.normalizeEmail(email)
        val effectiveType = com.comunidapp.app.domain.user.SessionIdentity.signupAccountType()
        return try {
            val existingSessionId = supabase.auth.currentUserOrNull()?.id
            if (com.comunidapp.app.domain.auth.SignupSessionPolicy.mustClearExistingSessionBeforeSignup(
                    existingSessionId
                )
            ) {
                runCatching { supabase.auth.signOut() }
            }
            val trimmedName = name.trim()
            val signedUpUser = supabase.auth.signUpWith(
                Email,
                redirectUrl = SupabaseAuthConfig.requireRedirectUrl()
            ) {
                this.email = normalizedEmail
                this.password = password
                data = buildJsonObject {
                    put("name", trimmedName)
                    put("display_name", trimmedName)
                    put("username", normalizedUsername.value)
                    put("birth_date", personAge.birthDate.toString())
                    put("terms_version", consent.termsVersion)
                    put("privacy_version", consent.privacyVersion)
                    put("consent_source", consent.source)
                    consent.locale?.takeIf { it.isNotBlank() }?.let { put("consent_locale", it) }
                }
            }

            val authUser = signedUpUser ?: supabase.auth.currentUserOrNull()
            val session = supabase.auth.currentSessionOrNull()
            val identitiesCount = authUser?.identities.orEmpty().size
            val sessionPresent = session != null
            com.comunidapp.app.core.logging.AppLog.info(
                "AuthSignup",
                "SIGNUP identities=$identitiesCount session=${if (sessionPresent) "YES" else "NO"} " +
                    "userId=${if (authUser?.id.isNullOrBlank()) "NO" else "YES"} " +
                    "confirmed=${authUser?.isEmailConfirmed() == true}"
            )
            if (com.comunidapp.app.domain.auth.SignupSessionPolicy.existingEmailHiddenByGoTrue(
                    identitiesCount = identitiesCount,
                    sessionPresent = sessionPresent
                )
            ) {
                return Result.failure(
                    AuthErrorMapper.toException(
                        AuthErrorCode.EMAIL_ALREADY_REGISTERED,
                        "GOTRUE_EXISTING_EMAIL_NO_IDENTITIES"
                    )
                )
            }
            val confirmed = authUser?.isEmailConfirmed() == true
            val signupAccepted = authUser != null ||
                com.comunidapp.app.domain.auth.SignupSessionPolicy.signupAcceptedWithoutSession(
                    httpAccepted = true,
                    sessionPresent = sessionPresent
                )
            if (!signupAccepted) {
                return Result.failure(
                    AuthErrorMapper.toException(AuthErrorCode.SIGNUP_FAILED, "signup not accepted")
                )
            }

            val user = User(
                id = authUser?.id.orEmpty(),
                name = trimmedName,
                email = normalizedEmail,
                accountType = effectiveType,
                username = normalizedUsername.value,
                displayName = trimmedName,
                emailVerified = confirmed,
                onboardingStatus = "IN_PROGRESS",
                birthDate = personAge.birthDate.toString(),
                ageBand = personAge.band.name
            )

            if (com.comunidapp.app.domain.auth.SignupSessionPolicy.mustReleaseUnconfirmedSession(
                    sessionPresent = session != null,
                    emailConfirmed = confirmed
                )
            ) {
                runCatching { supabase.auth.signOut() }
            } else if (session != null && confirmed) {
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
                redirectUrl = SupabaseAuthConfig.requireRedirectUrl()
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

    override suspend fun addPassword(newPassword: String): Result<Unit> {
        AuthValidators.validatePassword(newPassword).getOrElse {
            return Result.failure(AuthErrorMapper.fromThrowableToException(it))
        }
        if (supabase.auth.currentUserOrNull() == null) {
            return Result.failure(
                AuthErrorMapper.toException(AuthErrorCode.SESSION_EXPIRED, "no session")
            )
        }
        return try {
            supabase.auth.updateUser {
                password = newPassword
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(mapSupabaseException(e))
        }
    }

    override suspend fun ensureAuthenticatedSession(): Result<User> {
        return try {
            val authUser = com.comunidapp.app.domain.auth.AuthSessionAccess.requireUser(supabase.auth)
            val email = authUser.email.orEmpty()
            Result.success(fetchUserProfile(authUser, email))
        } catch (e: Exception) {
            if (e.message == "NOT_AUTHENTICATED") {
                Result.failure(
                    AuthErrorMapper.toException(AuthErrorCode.SESSION_EXPIRED, "no session")
                )
            } else {
                Result.failure(mapSupabaseException(e))
            }
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
            // Resend OTP for the existing unconfirmed auth.users row. Do not signUp again.
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
                        AuthErrorCode.EMAIL_NOT_VERIFIED,
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
                    AuthErrorCode.OTP_INVALID,
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
                ?: return Result.failure(
                    AuthErrorMapper.toException(
                        AuthErrorCode.EMAIL_NOT_VERIFIED,
                        "otp verified without session"
                    )
                )
            userDataSource.updateEmailVerified(authUser.id, true)
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
        val authUser = com.comunidapp.app.domain.auth.AuthSessionAccess.currentUser(supabase.auth)
            ?: return null
        return authUser.toUser()
    }

    override suspend fun logout() {
        runCatching { PushTokenRegistrar.unlinkForCurrentUser() }
        runCatching { supabase.auth.signOut() }
            .onFailure { error ->
                val mapped = AuthErrorMapper.fromThrowable(error)
                AppLog.warning(TAG, "signOut remote failed ${mapped.code}; clearing local session")
            }
        if (supabase.auth.currentUserOrNull() != null) {
            runCatching { supabase.auth.signOut(SignOutScope.LOCAL) }
        }
    }

    override fun observeAuthState(): Flow<User?> = kotlinx.coroutines.flow.flow {
        var lastEmitted: User? = null
        supabase.auth.sessionStatus.collect { status ->
            when (status) {
                is SessionStatus.Authenticated -> {
                    val authUser = status.session.user
                    val next = if (authUser != null &&
                        com.comunidapp.app.domain.auth.SignupSessionPolicy.appAccessAllowed(
                            hasAuthenticatedSession = true,
                            emailConfirmed = authUser.isEmailConfirmed()
                        )
                    ) {
                        authUser.toUser()
                    } else {
                        null
                    }
                    lastEmitted = next
                    emit(next)
                }
                is SessionStatus.NotAuthenticated -> {
                    lastEmitted = null
                    emit(null)
                }
                else -> {
                    // Initializing / RefreshFailure must not log the user out,
                    // and must not re-emit a previous account during an account switch.
                    val current = supabase.auth.currentUserOrNull()?.takeIf { user ->
                        com.comunidapp.app.domain.auth.SignupSessionPolicy.appAccessAllowed(
                            hasAuthenticatedSession = true,
                            emailConfirmed = user.isEmailConfirmed()
                        )
                    }?.toUser()
                    if (current != null && current.id == lastEmitted?.id) {
                        emit(current)
                    }
                }
            }
        }
    }

    private suspend fun fetchUserProfile(authUser: UserInfo, email: String): User {
        return userDataSource.getUser(authUser.id) ?: User(
            id = authUser.id,
            name = authUser.metaString("full_name")
                ?: authUser.metaString("name")
                ?: authUser.metaString("display_name").orEmpty(),
            email = email,
            accountType = AccountType.PERSON,
            emailVerified = authUser.isEmailConfirmed(),
            displayName = authUser.metaString("full_name")
                ?: authUser.metaString("name")
                ?: authUser.metaString("display_name")
        )
    }

    private fun UserInfo.metaString(key: String): String? =
        userMetadata?.get(key)?.toString()?.trim('"')?.takeIf { it.isNotBlank() && it != "null" }

    private fun UserInfo.toUser(): User {
        val username = metaString("username")
        val display = metaString("display_name") ?: metaString("name").orEmpty()
        return User(
            id = id,
            name = display,
            email = email.orEmpty(),
            accountType = com.comunidapp.app.domain.user.SessionIdentity.fromLegacyJwtClaim(
                metaString("account_type")
            ),
            emailVerified = isEmailConfirmed(),
            username = username,
            displayName = display.takeIf { it.isNotBlank() },
            birthDate = metaString("birth_date"),
            onboardingStatus = "IN_PROGRESS"
        )
    }

    private fun UserInfo.isEmailConfirmed(): Boolean =
        emailConfirmedAt != null

    private fun mapSupabaseException(e: Exception): Exception =
        AuthErrorMapper.fromThrowableToException(e)

    @Serializable
    private data class UserConsentRow(
        val id: String? = null
    )

    @Serializable
    private data class AdminBeginLoginRow(
        val email: String? = null
    )

    companion object {
        private const val TAG = "SupabaseAuthRepository"
        private const val USER_CONSENTS_TABLE = "user_consents"
    }
}
