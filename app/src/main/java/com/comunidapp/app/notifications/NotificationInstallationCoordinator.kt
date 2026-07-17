package com.comunidapp.app.notifications

import android.provider.Settings
import com.comunidapp.app.BuildConfig
import com.comunidapp.app.LeoverApplication
import com.comunidapp.app.core.config.AppConfigProvider
import com.comunidapp.app.core.logging.AppLog
import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.domain.notifications.NotificationInstallationPlatform
import com.comunidapp.app.domain.notifications.NotificationInstallationRules
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Instalaciones reales M06: installationId estable, registro post-login,
 * rotación onNewToken, revoke solo instalación actual, sin doble registro.
 * No expone raw token. Evita doble push: no escribe device_tokens si M06 registra OK.
 */
object NotificationInstallationCoordinator {

    private const val TAG = "NotificationInstall"
    private val registerMutex = Mutex()
    private val lastRegisteredFingerprint = AtomicReference<String?>(null)
    private val registering = AtomicBoolean(false)

    suspend fun syncAfterLogin() {
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            registerOrRotate(token)
        } catch (e: Exception) {
            if (!AppConfigProvider.featureFlags().useSupabase) {
                registerMockInstallation()
            } else {
                AppLog.warning(TAG, "No se pudo obtener token FCM", e)
            }
        }
    }

    suspend fun onNewToken(token: String) {
        registerOrRotate(token)
    }

    suspend fun revokeCurrentOnly() {
        val userId = AuthProvider.repository.getCurrentUser()?.id ?: return
        try {
            DataProvider.notificationInstallationRepository.revokeCurrentInstallation(
                userId = userId,
                installationId = currentInstallationId(),
                now = Instant.now()
            )
            lastRegisteredFingerprint.set(null)
            NotificationPendingNavigationStore.clear()
        } catch (e: Exception) {
            AppLog.warning(TAG, "No se pudo revocar instalación actual", e)
        }
    }

    fun currentInstallationId(): String {
        val context = LeoverApplication.instance
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ).orEmpty()
        val seed = androidId.ifBlank { context.packageName }
        return "android-${NotificationInstallationRules.fingerprintOf(seed)}"
    }

    private suspend fun registerOrRotate(token: String) {
        if (token.isBlank()) return
        val userId = AuthProvider.repository.getCurrentUser()?.id ?: return
        val fingerprint = NotificationInstallationRules.fingerprintOf(token)
        if (lastRegisteredFingerprint.get() == fingerprint && !registering.get()) {
            return
        }
        registerMutex.withLock {
            if (lastRegisteredFingerprint.get() == fingerprint) return
            registering.set(true)
            try {
                val installationId = currentInstallationId()
                val result = DataProvider.notificationInstallationRepository.registerInstallation(
                    installationId = installationId,
                    userId = userId,
                    platform = NotificationInstallationPlatform.ANDROID,
                    tokenFingerprint = fingerprint,
                    now = Instant.now(),
                    appVersion = BuildConfig.VERSION_NAME,
                    deviceLabel = "Android",
                    tokenReference = token
                )
                when (result) {
                    is AppResult.Success -> {
                        lastRegisteredFingerprint.set(fingerprint)
                        // Evitar doble push: no upsert legacy device_tokens cuando M06 OK.
                    }
                    is AppResult.Failure -> {
                        AppLog.warning(
                            TAG,
                            "Registro instalación falló: ${result.error.code}"
                        )
                        if (AppConfigProvider.featureFlags().useSupabase) {
                            runCatching {
                                DataProvider.platformRepository.upsertDeviceToken(userId, token)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                AppLog.warning(TAG, "No se pudo registrar instalación", e)
            } finally {
                registering.set(false)
            }
        }
    }

    private suspend fun registerMockInstallation() {
        val userId = AuthProvider.repository.getCurrentUser()?.id ?: return
        val installationId = currentInstallationId()
        val fingerprint = NotificationInstallationRules.fingerprintOf("mock-$installationId-$userId")
        DataProvider.notificationInstallationRepository.registerInstallation(
            installationId = installationId,
            userId = userId,
            platform = NotificationInstallationPlatform.ANDROID,
            tokenFingerprint = fingerprint,
            now = Instant.now(),
            appVersion = BuildConfig.VERSION_NAME,
            deviceLabel = "Android-Mock",
            tokenReference = "mock-token-ref-$installationId"
        )
        lastRegisteredFingerprint.set(fingerprint)
    }
}

/** Facade compatible: delega al coordinador M06. */
object PushTokenRegistrar {
    suspend fun syncCurrentToken() = NotificationInstallationCoordinator.syncAfterLogin()
    suspend fun registerToken(token: String) = NotificationInstallationCoordinator.onNewToken(token)
    suspend fun unlinkForCurrentUser() = NotificationInstallationCoordinator.revokeCurrentOnly()
}
