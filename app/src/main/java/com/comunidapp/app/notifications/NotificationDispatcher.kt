package com.comunidapp.app.notifications

import com.comunidapp.app.data.model.NotificationType
import com.comunidapp.app.data.provider.DataProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Crea notificaciones in-app. El push FCM se dispara vía webhook de Supabase
 * al insertar en la tabla `notifications` (Edge Function `push`).
 */
object NotificationDispatcher {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val platform get() = DataProvider.platformRepository

    fun notify(
        userId: String,
        type: NotificationType,
        title: String,
        body: String,
        relatedId: String? = null,
        relatedType: String? = null
    ) {
        if (userId.isBlank()) return
        scope.launch {
            runCatching {
                platform.createNotification(userId, type, title, body, relatedId, relatedType)
            }
        }
    }
}
