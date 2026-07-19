package com.comunidapp.app.notifications

import com.comunidapp.app.domain.notifications.NotificationDeepLink
import java.util.concurrent.atomic.AtomicReference

/**
 * Navegación pendiente desde push/tap. Se limpia en logout y cambio de cuenta.
 * Evita doble navegación por reentrega o doble tap.
 */
object NotificationPendingNavigationStore {

    data class PendingNavigation(
        val notificationId: String,
        val deepLink: NotificationDeepLink,
        val consumed: Boolean = false
    )

    private val pending = AtomicReference<PendingNavigation?>(null)
    private val lastConsumedNotificationId = AtomicReference<String?>(null)

    fun offer(notificationId: String, deepLink: NotificationDeepLink): Boolean {
        val id = notificationId.trim()
        if (id.isEmpty()) return false
        if (lastConsumedNotificationId.get() == id) return false
        val current = pending.get()
        if (current != null && current.notificationId == id && !current.consumed) {
            return false
        }
        pending.set(PendingNavigation(id, deepLink))
        return true
    }

    fun peek(): PendingNavigation? = pending.get()?.takeIf { !it.consumed }

    fun consume(): PendingNavigation? {
        val current = pending.get() ?: return null
        if (current.consumed) return null
        if (!pending.compareAndSet(current, current.copy(consumed = true))) return null
        lastConsumedNotificationId.set(current.notificationId)
        pending.set(null)
        return current
    }

    fun clear() {
        pending.set(null)
        lastConsumedNotificationId.set(null)
    }

    fun clearForAccountChange() = clear()
}
