package com.comunidapp.shared.deeplink

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bridge iOS → shared: URLs entrantes vía `onOpenURL` / notification tap.
 * iosMain — exportado al framework ObjC para Swift.
 */
object IosDeepLinkBridge {
    private val pending = MutableStateFlow<String?>(null)

    fun offerUrl(url: String) {
        val trimmed = url.trim()
        if (trimmed.isNotEmpty()) {
            pending.value = trimmed
        }
    }

    fun observe(): Flow<String?> = pending.asStateFlow()

    fun peek(): String? = pending.value

    fun consume() {
        pending.value = null
    }
}

/**
 * Entry point estable para Swift (`IosDeepLinkBridgeKt.offerDeepLinkUrl` / top-level).
 */
fun offerDeepLinkUrl(url: String) {
    IosDeepLinkBridge.offerUrl(url)
}

/**
 * Notification tap → DeepLinkPendingStore (tipos M06, sin URL cruda).
 */
fun offerNotificationDeepLink(deepLinkType: String?, resourceId: String?) {
    val target = NotificationIntentParser.fromPushExtras(deepLinkType, resourceId)
    DeepLinkPendingStore.set(target)
}
