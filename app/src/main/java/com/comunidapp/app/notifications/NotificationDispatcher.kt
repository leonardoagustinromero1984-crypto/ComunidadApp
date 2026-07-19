package com.comunidapp.app.notifications

import com.comunidapp.app.core.logging.AppLog
import com.comunidapp.app.data.model.NotificationType
import com.comunidapp.app.data.provider.DataProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Compatibilidad temporal. No elige recipients server-derived ni envía FCM.
 * Con Supabase, createNotification cliente queda denegado (M06).
 * Eventos M03–M05 se cablean server-side (migración 027). Social/chat/amistad: PENDIENTE.
 */
object NotificationDispatcher {

    private const val TAG = "NotificationDispatcher"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val platform get() = DataProvider.platformRepository

    enum class DispatchOutcome {
        ACCEPTED_LEGACY_MOCK,
        DENIED_CLIENT_CREATE,
        DENIED_NO_SERVER_EVENT,
        FAILED
    }

    fun notify(
        userId: String,
        type: NotificationType,
        title: String,
        body: String,
        relatedId: String? = null,
        relatedType: String? = null
    ) {
        notifyWithOutcome(userId, type, title, body, relatedId, relatedType)
    }

    fun notifyWithOutcome(
        userId: String,
        type: NotificationType,
        title: String,
        body: String,
        relatedId: String? = null,
        relatedType: String? = null
    ): DispatchOutcome {
        if (userId.isBlank()) return DispatchOutcome.FAILED
        var outcome = DispatchOutcome.DENIED_NO_SERVER_EVENT
        scope.launch {
            runCatching {
                platform.createNotification(userId, type, title, body, relatedId, relatedType)
            }.onSuccess { result ->
                outcome = if (result.isSuccess) {
                    DispatchOutcome.ACCEPTED_LEGACY_MOCK
                } else {
                    AppLog.warning(
                        TAG,
                        "Notificación cliente denegada por M06: ${result.exceptionOrNull()?.message}"
                    )
                    DispatchOutcome.DENIED_CLIENT_CREATE
                }
            }.onFailure {
                outcome = DispatchOutcome.FAILED
                AppLog.warning(TAG, "No se pudo solicitar notificación cliente", it)
            }
        }
        return outcome
    }

    /** Inventario Etapa 4 — call sites legacy (no FCM directo). */
    fun remainingClientCallSites(): List<String> = listOf(
        "ChatViewModel (CHAT_MESSAGE) — PENDIENTE server event",
        "FriendRequestsViewModel (FRIEND_ACCEPTED) — PENDIENTE",
        "SearchFriendsViewModel (FRIEND_REQUEST) — PENDIENTE",
        "UserPublicProfileViewModel (FRIEND_REQUEST/ACCEPTED) — PENDIENTE",
        "AdoptionDetailViewModel (ADOPTION_REQUEST) — PENDIENTE o parcial M05/M03",
        "CommunityViewModel (FOSTER_REQUEST) — PENDIENTE",
        "ServiceViewModel (BOOKING) — PENDIENTE"
    )
}
