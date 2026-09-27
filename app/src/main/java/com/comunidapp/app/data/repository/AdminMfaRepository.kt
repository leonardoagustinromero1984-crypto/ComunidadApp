package com.comunidapp.app.data.repository

import com.comunidapp.app.data.remote.supabase.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.mfa.AuthenticatorAssuranceLevel
import io.github.jan.supabase.auth.mfa.FactorType
import io.github.jan.supabase.auth.user.UserMfaFactor

enum class AdminMfaFactorState {
    ENROLLMENT_REQUIRED,
    CHALLENGE_REQUIRED,
    AAL2,
    STALE
}

data class AdminTotpEnrollment(
    val factorId: String,
    val secret: String,
    val otpauthUri: String
)

interface AdminMfaRepository {
    suspend fun resolveFactorState(): AdminMfaFactorState
    suspend fun enrollTotp(): Result<AdminTotpEnrollment>
    suspend fun verifyCode(code: String): Result<Unit>
    suspend fun confirmAal2(): Boolean
    suspend fun discardUnverifiedEnrollment()
    fun clearMemory()
}

class MockAdminMfaRepository : AdminMfaRepository {
    var factorState: AdminMfaFactorState = AdminMfaFactorState.AAL2
    var verifySucceeds: Boolean = true
    var lastVerifyCode: String? = null
    private var enrollment: AdminTotpEnrollment? = null

    fun resetForTests() {
        factorState = AdminMfaFactorState.AAL2
        verifySucceeds = true
        lastVerifyCode = null
        enrollment = null
    }

    override suspend fun resolveFactorState(): AdminMfaFactorState = factorState

    override suspend fun enrollTotp(): Result<AdminTotpEnrollment> {
        val enrolled = AdminTotpEnrollment(
            factorId = "mock-factor",
            secret = "MOCKSECRET",
            otpauthUri = "otpauth://totp/LeoVer:staff?secret=MOCKSECRET&issuer=LeoVer"
        )
        enrollment = enrolled
        return Result.success(enrolled)
    }

    override suspend fun verifyCode(code: String): Result<Unit> {
        lastVerifyCode = code
        if (!verifySucceeds || code.length != 6 || !code.all { it.isDigit() }) {
            return Result.failure(AdminMfaInvalidCodeException())
        }
        factorState = AdminMfaFactorState.AAL2
        enrollment = null
        return Result.success(Unit)
    }

    override suspend fun confirmAal2(): Boolean = factorState == AdminMfaFactorState.AAL2

    override suspend fun discardUnverifiedEnrollment() {
        enrollment = null
    }

    override fun clearMemory() {
        enrollment = null
    }
}

class AdminMfaInvalidCodeException : Exception("MFA_INVALID_CODE")
class AdminMfaSessionStaleException : Exception("MFA_SESSION_STALE")

class SupabaseAdminMfaRepository : AdminMfaRepository {

    @Volatile
    private var enrollmentFactorId: String? = null

    @Volatile
    private var enrollmentSecret: String? = null

    @Volatile
    private var enrollmentUri: String? = null

    override suspend fun resolveFactorState(): AdminMfaFactorState {
        val level = runCatching { supabase.auth.mfa.getAuthenticatorAssuranceLevel() }.getOrNull()
            ?: return AdminMfaFactorState.STALE
        val factors = runCatching { supabase.auth.mfa.retrieveFactorsForCurrentUser() }.getOrDefault(emptyList())
        val hasVerified = factors.any(::isVerifiedTotp)
        return when {
            level.current == AuthenticatorAssuranceLevel.AAL2 &&
                level.next == AuthenticatorAssuranceLevel.AAL2 &&
                hasVerified -> AdminMfaFactorState.AAL2
            level.current == AuthenticatorAssuranceLevel.AAL1 &&
                level.next == AuthenticatorAssuranceLevel.AAL2 &&
                hasVerified -> AdminMfaFactorState.CHALLENGE_REQUIRED
            level.current == AuthenticatorAssuranceLevel.AAL1 &&
                level.next == AuthenticatorAssuranceLevel.AAL1 &&
                !hasVerified -> AdminMfaFactorState.ENROLLMENT_REQUIRED
            else -> AdminMfaFactorState.STALE
        }
    }

    override suspend fun enrollTotp(): Result<AdminTotpEnrollment> {
        return try {
            unenrollUnverifiedTotp()
            val factor = supabase.auth.mfa.enroll(FactorType.TOTP, friendlyName = "LeoVer") {
                issuer = "LeoVer"
            }
            val totp = factor.data
            enrollmentFactorId = factor.id
            enrollmentSecret = totp.secret
            enrollmentUri = totp.uri
            Result.success(
                AdminTotpEnrollment(
                    factorId = factor.id,
                    secret = totp.secret,
                    otpauthUri = totp.uri
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun verifyCode(code: String): Result<Unit> {
        val trimmed = code.trim()
        if (trimmed.length != 6 || !trimmed.all { it.isDigit() }) {
            return Result.failure(AdminMfaInvalidCodeException())
        }
        return try {
            val factorId = enrollmentFactorId ?: verifiedTotpFactorId()
            if (factorId.isNullOrBlank()) {
                return Result.failure(AdminMfaSessionStaleException())
            }
            val challenge = supabase.auth.mfa.createChallenge(factorId)
            supabase.auth.mfa.verifyChallenge(
                factorId = factorId,
                challengeId = challenge.id,
                code = trimmed,
                saveSession = true
            )
            enrollmentFactorId = null
            enrollmentSecret = null
            enrollmentUri = null
            if (!confirmAal2()) {
                return Result.failure(AdminMfaSessionStaleException())
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(mapVerifyError(e))
        }
    }

    override suspend fun confirmAal2(): Boolean {
        val level = runCatching { supabase.auth.mfa.getAuthenticatorAssuranceLevel() }.getOrNull()
            ?: return false
        return level.current == AuthenticatorAssuranceLevel.AAL2
    }

    override suspend fun discardUnverifiedEnrollment() {
        val factorId = enrollmentFactorId
        enrollmentFactorId = null
        enrollmentSecret = null
        enrollmentUri = null
        if (!factorId.isNullOrBlank()) {
            runCatching { supabase.auth.mfa.unenroll(factorId) }
        }
    }

    override fun clearMemory() {
        enrollmentFactorId = null
        enrollmentSecret = null
        enrollmentUri = null
    }

    private suspend fun unenrollUnverifiedTotp() {
        val factors = runCatching { supabase.auth.mfa.retrieveFactorsForCurrentUser() }.getOrDefault(emptyList())
        factors.filter { isTotp(it) && !isVerifiedTotp(it) }.forEach { factor ->
            runCatching { supabase.auth.mfa.unenroll(factor.id) }
        }
    }

    private suspend fun verifiedTotpFactorId(): String? {
        val factors = runCatching { supabase.auth.mfa.retrieveFactorsForCurrentUser() }.getOrDefault(emptyList())
        return factors.firstOrNull(::isVerifiedTotp)?.id
            ?: supabase.auth.mfa.verifiedFactors.firstOrNull()?.id
    }

    private fun isTotp(factor: UserMfaFactor): Boolean =
        factor.factorType.equals("totp", ignoreCase = true)

    private fun isVerifiedTotp(factor: UserMfaFactor): Boolean =
        isTotp(factor) && factor.isVerified

    private fun mapVerifyError(error: Throwable): Throwable {
        val signal = error.message.orEmpty().lowercase()
        return if (
            signal.contains("invalid") ||
            signal.contains("otp") ||
            signal.contains("code") ||
            signal.contains("401") ||
            signal.contains("422")
        ) {
            AdminMfaInvalidCodeException()
        } else {
            error
        }
    }
}
