package com.comunidapp.app.notifications

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Señales de refresh de bandeja tras push / foreground.
 * Realtime queda pendiente de validación RLS; se usa refresh/polling.
 */
object NotificationInboxRefreshCoordinator {

    private val _refreshSignals = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val refreshSignals: SharedFlow<String> = _refreshSignals.asSharedFlow()

    fun requestRefresh(reason: String = "manual") {
        _refreshSignals.tryEmit(reason)
    }

    fun onPushReceived(notificationId: String) {
        requestRefresh("push:$notificationId")
    }
}
