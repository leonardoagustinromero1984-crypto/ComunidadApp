package com.comunidapp.shared.push

import com.comunidapp.shared.crypto.sha256Hex
import com.comunidapp.shared.crypto.sha256HexOfHexToken
import com.comunidapp.shared.crypto.sha256HexOfUtf8
import com.comunidapp.shared.session.SessionState
import com.comunidapp.shared.ui.ErrorSanitizer
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Fingerprint de token — alinea contrato M06 (hex lowercase, sin raw en logs).
 * Preferir bytes del device token; fallback UTF-8 del string.
 */
object PushTokenFingerprintRules {
    fun ofRawUtf8Token(rawToken: String): PushTokenFingerprint =
        PushTokenFingerprint(sha256HexOfUtf8(rawToken))

    fun ofDeviceTokenBytes(bytes: ByteArray): PushTokenFingerprint =
        PushTokenFingerprint(sha256Hex(bytes))

    fun ofApnsHexToken(hexToken: String): PushTokenFingerprint =
        PushTokenFingerprint(sha256HexOfHexToken(hexToken))
}

internal class SupabasePushInstallationGateway(
    private val client: SupabaseClient
) {
    suspend fun registerIos(
        installationId: String,
        tokenFingerprint: String,
        tokenReference: String?,
        appVersion: String?
    ) {
        client.postgrest.rpc(
            function = "m06_register_installation",
            parameters = buildJsonObject {
                put("p_installation_id", installationId)
                put("p_platform", PushPlatform.IOS.name)
                put("p_token_fingerprint", tokenFingerprint)
                tokenReference?.takeIf { it.isNotBlank() }?.let { put("p_token_reference", it) }
                appVersion?.let { put("p_app_version", it) }
            }
        )
    }

    suspend fun revokeCurrent(installationId: String) {
        client.postgrest.rpc(
            function = "m06_revoke_current_installation",
            parameters = buildJsonObject {
                put("p_installation_id", installationId)
            }
        )
    }
}

internal class RemotePushInstallationRepository(
    private val gateway: SupabasePushInstallationGateway,
    private val sessionRepository: com.comunidapp.shared.session.SessionRepository
) : PushInstallationRepository {

    override suspend fun registerIosInstallation(
        installationId: String,
        tokenFingerprint: String,
        tokenReference: String?,
        appVersion: String?
    ): PushRegistrationResult {
        if (sessionRepository.currentSession() !is SessionState.Authenticated) {
            return PushRegistrationResult.Unauthenticated
        }
        return try {
            PushTokenFingerprint(tokenFingerprint) // validate
            gateway.registerIos(installationId, tokenFingerprint, tokenReference, appVersion)
            PushRegistrationResult.Success
        } catch (t: Throwable) {
            PushRegistrationResult.Failed(ErrorSanitizer.sanitize(t))
        }
    }

    override suspend fun revokeCurrent(installationId: String): PushRegistrationResult {
        if (sessionRepository.currentSession() !is SessionState.Authenticated) {
            return PushRegistrationResult.Unauthenticated
        }
        return try {
            gateway.revokeCurrent(installationId)
            PushRegistrationResult.Success
        } catch (t: Throwable) {
            PushRegistrationResult.Failed(ErrorSanitizer.sanitize(t))
        }
    }
}

/**
 * Coordina permiso + token + registro backend.
 * Implementación de plataforma se inyecta (iOS bridge / no-op Android shared).
 */
interface PushRegistrationCoordinator {
    suspend fun currentPermission(): PushPermissionState
    suspend fun requestPermissionAndRegister(
        repository: PushInstallationRepository,
        installationId: String,
        appVersion: String? = null
    ): PushRegistrationResult
}

class NoOpPushRegistrationCoordinator : PushRegistrationCoordinator {
    override suspend fun currentPermission(): PushPermissionState = PushPermissionState.Unavailable

    override suspend fun requestPermissionAndRegister(
        repository: PushInstallationRepository,
        installationId: String,
        appVersion: String?
    ): PushRegistrationResult = PushRegistrationResult.Unavailable
}
