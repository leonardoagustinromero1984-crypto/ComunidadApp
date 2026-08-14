package com.comunidapp.shared.push

import com.comunidapp.shared.crypto.bytesToHex
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import platform.Foundation.NSData
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter
import platform.posix.memcpy

/**
 * Bridge APNs — token hex + deep link extras desde Swift AppDelegate.
 */
object IosPushBridge {
    private val tokenHex = MutableStateFlow<String?>(null)
    val deviceTokenHex: StateFlow<String?> = tokenHex.asStateFlow()

    private var registerHandler: (() -> Unit)? = null

    /**
     * Swift registra: `IosPushBridge.shared.setRegisterForRemoteNotificationsHandler { UIApplication.shared.registerForRemoteNotifications() }`
     */
    fun setRegisterForRemoteNotificationsHandler(handler: () -> Unit) {
        registerHandler = handler
    }

    fun requestOsRegistration() {
        registerHandler?.invoke()
    }

    fun onDeviceTokenHex(hex: String) {
        val clean = hex.trim().lowercase().replace(" ", "").replace("<", "").replace(">", "")
        if (clean.isNotEmpty()) {
            tokenHex.value = clean
        }
    }

    fun onDeviceTokenData(data: NSData) {
        onDeviceTokenHex(nsDataToHex(data))
    }

    fun clearToken() {
        tokenHex.value = null
    }

    fun peekTokenHex(): String? = tokenHex.value
}

/**
 * Entry pública para Swift.
 */
fun onIosDeviceTokenHex(hex: String) {
    IosPushBridge.onDeviceTokenHex(hex)
}

@OptIn(ExperimentalForeignApi::class)
internal fun nsDataToHex(data: NSData): String {
    val length = data.length.toInt()
    if (length <= 0) return ""
    val bytes = ByteArray(length)
    bytes.usePinned { pinned ->
        memcpy(pinned.addressOf(0), data.bytes, data.length)
    }
    return bytesToHex(bytes)
}

class IosPushRegistrationCoordinator : PushRegistrationCoordinator {
    override suspend fun currentPermission(): PushPermissionState {
        var status = PushPermissionState.NotDetermined
        val center = UNUserNotificationCenter.currentNotificationCenter()
        kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
            center.getNotificationSettingsWithCompletionHandler { settings ->
                status = when (settings?.authorizationStatus) {
                    UNAuthorizationStatusAuthorized -> PushPermissionState.Authorized
                    UNAuthorizationStatusDenied -> PushPermissionState.Denied
                    UNAuthorizationStatusProvisional -> PushPermissionState.Provisional
                    UNAuthorizationStatusNotDetermined -> PushPermissionState.NotDetermined
                    else -> PushPermissionState.Unavailable
                }
                cont.resume(Unit) {}
            }
        }
        return status
    }

    override suspend fun requestPermissionAndRegister(
        repository: PushInstallationRepository,
        installationId: String,
        appVersion: String?
    ): PushRegistrationResult {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        val granted = kotlinx.coroutines.suspendCancellableCoroutine<Boolean> { cont ->
            center.requestAuthorizationWithOptions(
                UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
            ) { ok, _ ->
                cont.resume(ok) {}
            }
        }
        if (!granted) {
            return PushRegistrationResult.PermissionDenied
        }
        IosPushBridge.requestOsRegistration()
        val hex = withTimeoutOrNull(15_000L) {
            IosPushBridge.deviceTokenHex.first { !it.isNullOrBlank() }
        } ?: return PushRegistrationResult.MissingToken
        val fingerprint = PushTokenFingerprintRules.ofApnsHexToken(hex!!).hexSha256
        return repository.registerIosInstallation(
            installationId = installationId,
            tokenFingerprint = fingerprint,
            tokenReference = null,
            appVersion = appVersion
        )
    }
}
