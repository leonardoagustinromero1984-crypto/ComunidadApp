package com.comunidapp.app.data.notifications

import com.comunidapp.app.data.model.AppNotification
import com.comunidapp.app.data.model.NotificationType
import com.comunidapp.app.domain.notifications.NotificationCategory
import com.comunidapp.app.domain.notifications.NotificationDeepLink
import com.comunidapp.app.domain.notifications.NotificationDeepLinkRoute
import com.comunidapp.app.domain.notifications.NotificationPriority
import com.comunidapp.app.domain.notifications.NotificationSensitivity

/**
 * Referencia de lectura compatible con el modelo legacy de bandeja.
 * No concede permisos ni emite eventos nuevos.
 */
data class LegacyNotificationReference(
    val id: String,
    val userId: String,
    val legacyType: NotificationType,
    val category: NotificationCategory,
    val priority: NotificationPriority,
    val sensitivity: NotificationSensitivity,
    val deepLink: NotificationDeepLink,
    val title: String,
    val body: String,
    val relatedId: String?,
    val relatedType: String?,
    val readAt: Long?,
    val createdAt: Long?
)

/**
 * Mapea [NotificationType] exactos a categorías M06.
 */
object LegacyNotificationTypeAdapter {

    fun toCategory(type: NotificationType): NotificationCategory = when (type) {
        NotificationType.FRIEND_REQUEST -> NotificationCategory.SOCIAL
        NotificationType.FRIEND_ACCEPTED -> NotificationCategory.SOCIAL
        NotificationType.CHAT_MESSAGE -> NotificationCategory.MESSAGE
        NotificationType.ADOPTION_REQUEST -> NotificationCategory.ADOPTION
        NotificationType.FOSTER_REQUEST -> NotificationCategory.FOSTER
        NotificationType.BOOKING -> NotificationCategory.APPOINTMENT
        NotificationType.SIGHTING -> NotificationCategory.LOST_FOUND
        NotificationType.SYSTEM -> NotificationCategory.SYSTEM
    }

    fun toPriority(type: NotificationType): NotificationPriority = when (type) {
        NotificationType.ADOPTION_REQUEST,
        NotificationType.FOSTER_REQUEST,
        NotificationType.SIGHTING -> NotificationPriority.HIGH
        NotificationType.SYSTEM -> NotificationPriority.LOW
        else -> NotificationPriority.NORMAL
    }

    fun toSensitivity(type: NotificationType): NotificationSensitivity = when (type) {
        NotificationType.CHAT_MESSAGE -> NotificationSensitivity.PRIVATE
        NotificationType.SYSTEM -> NotificationSensitivity.PRIVATE
        else -> NotificationSensitivity.PRIVATE
    }

    fun map(notification: AppNotification): LegacyNotificationReference {
        val type = notification.type
        return LegacyNotificationReference(
            id = notification.id,
            userId = notification.userId,
            legacyType = type,
            category = toCategory(type),
            priority = toPriority(type),
            sensitivity = toSensitivity(type),
            deepLink = LegacyNotificationDeepLinkAdapter.toDeepLink(type, notification.relatedId, notification.relatedType),
            title = notification.title,
            body = notification.body,
            relatedId = notification.relatedId,
            relatedType = notification.relatedType,
            readAt = notification.readAt,
            createdAt = notification.createdAt
        )
    }

    /** Lectura compatible: nunca concede permiso. */
    fun grantsPermission(): Boolean = false

    /** No emite eventos inseguros nuevos desde legacy. */
    fun mayEmitNewEvents(): Boolean = false
}

/**
 * Deep links tipados desde tipos/payloads legacy.
 * Payload desconocido → SAFE_HOME.
 */
object LegacyNotificationDeepLinkAdapter {

    fun toDeepLink(
        type: NotificationType,
        relatedId: String?,
        relatedType: String?
    ): NotificationDeepLink {
        val route = when (type) {
            NotificationType.FRIEND_REQUEST,
            NotificationType.FRIEND_ACCEPTED -> NotificationDeepLinkRoute.PROFILE
            NotificationType.CHAT_MESSAGE -> NotificationDeepLinkRoute.CHAT
            NotificationType.ADOPTION_REQUEST,
            NotificationType.FOSTER_REQUEST -> NotificationDeepLinkRoute.ADOPTION
            NotificationType.BOOKING -> NotificationDeepLinkRoute.SAFE_HOME
            NotificationType.SIGHTING -> NotificationDeepLinkRoute.LOST_FOUND_CASE
            NotificationType.SYSTEM -> NotificationDeepLinkRoute.NOTIFICATIONS_INBOX
        }
        val resourceId = relatedId?.takeIf { it.isNotBlank() && !looksLikeUri(it) }
        val resourceType = relatedType?.takeIf { it.isNotBlank() && !looksLikeUri(it) }
        if (relatedId != null && looksLikeUri(relatedId)) {
            return NotificationDeepLink(routeType = NotificationDeepLinkRoute.SAFE_HOME)
        }
        if (relatedType != null && isUnknownRelatedType(relatedType) && type == NotificationType.SYSTEM) {
            return NotificationDeepLink(routeType = NotificationDeepLinkRoute.SAFE_HOME)
        }
        return NotificationDeepLink(
            routeType = route,
            resourceType = resourceType,
            resourceId = resourceId,
            fallbackRoute = NotificationDeepLinkRoute.SAFE_HOME
        )
    }

    /** Payload / tipo desconocido → SAFE_HOME. */
    fun fromUnknownPayload(): NotificationDeepLink =
        NotificationDeepLink(routeType = NotificationDeepLinkRoute.SAFE_HOME)

    fun fromRawTypeName(raw: String?, relatedId: String? = null, relatedType: String? = null): NotificationDeepLink {
        val type = NotificationType.entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) }
            ?: return fromUnknownPayload()
        return toDeepLink(type, relatedId, relatedType)
    }

    private fun looksLikeUri(value: String): Boolean =
        value.contains("://") || value.startsWith("http", ignoreCase = true)

    private fun isUnknownRelatedType(relatedType: String): Boolean {
        val known = setOf(
            "friend", "user", "chat", "conversation", "adoption", "foster",
            "booking", "sighting", "lost_found", "pet", "post", "system"
        )
        return relatedType.trim().lowercase() !in known
    }
}
