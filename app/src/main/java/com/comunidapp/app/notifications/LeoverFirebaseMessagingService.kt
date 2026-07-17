package com.comunidapp.app.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * FCM service gobernado M06: parsea payload tipado, no marca READ, refresca bandeja.
 */
class LeoverFirebaseMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch {
            NotificationInstallationCoordinator.onNewToken(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val parsed = NotificationPushPayloadParser.parse(
            data = message.data,
            notificationTitle = message.notification?.title,
            notificationBody = message.notification?.body
        ).getOrElse {
            // Payload inválido: no mostrar contenido arbitrario.
            return
        }

        // Dedup de navegación pendiente (reentrega/doble tap); el tray usa id estable.
        NotificationPendingNavigationStore.offer(parsed.notificationId, parsed.deepLink)

        val androidId = parsed.notificationId.hashCode() and Int.MAX_VALUE
        LeoverNotificationHelper.showGoverned(
            context = applicationContext,
            title = parsed.title,
            body = parsed.body,
            androidNotificationId = androidId,
            category = parsed.category,
            priority = parsed.priority,
            sensitivity = parsed.sensitivity,
            m06NotificationId = parsed.notificationId,
            deepLinkType = parsed.deepLink.routeType.name,
            resourceId = parsed.deepLink.resourceId,
            organizationId = parsed.deepLink.organizationId
        )
        // Push entregado NUNCA marca READ.
        NotificationInboxRefreshCoordinator.onPushReceived(parsed.notificationId)
    }
}
