package com.comunidapp.app.notifications

import com.comunidapp.app.core.config.AppConfigProvider
import com.comunidapp.app.core.logging.AppLog
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

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
            DataProvider.platformRepository.upsertDeviceToken(userId, token)
        } catch (e: Exception) {
            AppLog.warning(TAG, "No se pudo guardar token FCM", e)
        }
    }
}
