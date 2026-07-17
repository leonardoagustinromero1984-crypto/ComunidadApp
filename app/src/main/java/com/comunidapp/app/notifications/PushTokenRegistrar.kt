package com.comunidapp.app.notifications

import android.provider.Settings
import com.comunidapp.app.LeoverApplication
import com.comunidapp.app.BuildConfig
import com.comunidapp.app.core.config.AppConfigProvider
import com.comunidapp.app.core.logging.AppLog
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.domain.notifications.NotificationInstallationPlatform
import com.comunidapp.app.domain.notifications.NotificationInstallationRules
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import java.time.Instant

object PushTokenRegistrar {

    private const val TAG = "PushTokenRegistrar"

    suspend fun syncCurrentToken() {
        if (!AppConfigProvider.featureFlags().useSupabase) return
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            registerToken(token)
        } catch (e: Exception) {
            AppLog.warning(TAG, "No se pudo obtener token FCM", e)
        }
    }

    suspend fun registerToken(token: String) {
        if (token.isBlank()) return
        val userId = AuthProvider.repository.getCurrentUser()?.id ?: return
        try {
            val installationId = currentInstallationId()
            val fingerprint = NotificationInstallationRules.fingerprintOf(token)
            DataProvider.notificationInstallationRepository.registerInstallation(
                installationId = installationId,
                userId = userId,
                platform = NotificationInstallationPlatform.ANDROID,
                tokenFingerprint = fingerprint,
                now = Instant.now(),
                appVersion = BuildConfig.VERSION_NAME,
                deviceLabel = "Android"
            )
            // Legacy FCM path remains during Etapa 3; it is not expanded and is not deleted on logout.
            DataProvider.platformRepository.upsertDeviceToken(userId, token)
        } catch (e: Exception) {
            AppLog.warning(TAG, "No se pudo guardar token FCM", e)
        }
    }

    /** Revoca solo la instalación actual; device_tokens legacy permanece hasta la migración de push. */
    suspend fun unlinkForCurrentUser() {
        if (!AppConfigProvider.featureFlags().useSupabase) return
        val userId = AuthProvider.repository.getCurrentUser()?.id ?: return
        try {
            DataProvider.notificationInstallationRepository.revokeCurrentInstallation(
                userId = userId,
                installationId = currentInstallationId(),
                now = Instant.now()
            )
        } catch (e: Exception) {
            AppLog.warning(TAG, "No se pudo desvincular token FCM", e)
        }
    }

    private fun currentInstallationId(): String {
        val context = LeoverApplication.instance
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ).orEmpty()
        val seed = androidId.ifBlank { context.packageName }
        return "android-${NotificationInstallationRules.fingerprintOf(seed)}"
    }
}
