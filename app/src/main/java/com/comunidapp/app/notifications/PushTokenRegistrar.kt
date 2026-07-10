package com.comunidapp.app.notifications

import android.util.Log
import com.comunidapp.app.BuildConfig
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

object PushTokenRegistrar {

    private const val TAG = "PushTokenRegistrar"

    suspend fun syncCurrentToken() {
        if (!BuildConfig.SUPABASE_ENABLED) return
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            registerToken(token)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo obtener token FCM", e)
        }
    }

    suspend fun registerToken(token: String) {
        if (token.isBlank()) return
        val userId = AuthProvider.repository.getCurrentUser()?.id ?: return
        try {
            DataProvider.platformRepository.upsertDeviceToken(userId, token)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo guardar token FCM", e)
        }
    }
}
