package com.comunidapp.shared.push

/**
 * Fundación APNs / push installation (KMP-19).
 * Token raw nunca viaja en modelos públicos — solo fingerprint hex.
 */
enum class PushPermissionState {
    NotDetermined,
    Denied,
    Authorized,
    Provisional,
    Unavailable
}

enum class PushPlatform {
    IOS,
    ANDROID,
    UNKNOWN
}

data class PushTokenFingerprint(val hexSha256: String) {
    init {
        require(hexSha256.matches(Regex("^[a-f0-9]{8,128}$"))) {
            "TOKEN_FINGERPRINT_INVALID"
        }
    }

    override fun toString(): String = "PushTokenFingerprint(hexSha256=$hexSha256)"
}

sealed interface PushRegistrationResult {
    data object Success : PushRegistrationResult
    data object BackendError : PushRegistrationResult
    data object Unavailable : PushRegistrationResult
    data object Unauthenticated : PushRegistrationResult
    data object PermissionDenied : PushRegistrationResult
    data object MissingToken : PushRegistrationResult
    data class Failed(val message: String) : PushRegistrationResult
}

interface PushInstallationRepository {
    suspend fun registerIosInstallation(
        installationId: String,
        tokenFingerprint: String,
        tokenReference: String? = null,
        appVersion: String? = null
    ): PushRegistrationResult

    suspend fun revokeCurrent(installationId: String): PushRegistrationResult
}

class FakePushInstallationRepository(
    var registerResult: PushRegistrationResult = PushRegistrationResult.Success,
    var revokeResult: PushRegistrationResult = PushRegistrationResult.Success
) : PushInstallationRepository {
    var lastFingerprint: String? = null
    var lastInstallationId: String? = null
    var registerCalls: Int = 0
    var revokeCalls: Int = 0

    override suspend fun registerIosInstallation(
        installationId: String,
        tokenFingerprint: String,
        tokenReference: String?,
        appVersion: String?
    ): PushRegistrationResult {
        registerCalls++
        lastInstallationId = installationId
        lastFingerprint = tokenFingerprint
        // Never accept raw-looking tokens into lastFingerprint for assertions — fingerprint only.
        return registerResult
    }

    override suspend fun revokeCurrent(installationId: String): PushRegistrationResult {
        revokeCalls++
        lastInstallationId = installationId
        return revokeResult
    }
}

class UnconfiguredPushInstallationRepository : PushInstallationRepository {
    override suspend fun registerIosInstallation(
        installationId: String,
        tokenFingerprint: String,
        tokenReference: String?,
        appVersion: String?
    ): PushRegistrationResult = PushRegistrationResult.Unavailable

    override suspend fun revokeCurrent(installationId: String): PushRegistrationResult =
        PushRegistrationResult.Unavailable
}
