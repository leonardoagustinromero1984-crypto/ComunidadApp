package com.comunidapp.shared.auth

/**
 * Disponibilidad de Sign in with Apple (solo iOS app-side).
 */
expect fun isAppleSignInAvailable(): Boolean

sealed class AppleSignInPlatformResult {
    data class Success(val idToken: String, val rawNonce: String?) : AppleSignInPlatformResult()
    data object Cancelled : AppleSignInPlatformResult()
    data object ConfigurationRequired : AppleSignInPlatformResult()
    data class Failed(val message: String) : AppleSignInPlatformResult()
}

/**
 * Coordinador de plataforma — iOS presenta ASAuthorization; Android no disponible.
 */
interface AppleSignInController {
    suspend fun requestCredential(): AppleSignInPlatformResult
}

class UnavailableAppleSignInController : AppleSignInController {
    override suspend fun requestCredential(): AppleSignInPlatformResult =
        AppleSignInPlatformResult.ConfigurationRequired
}
