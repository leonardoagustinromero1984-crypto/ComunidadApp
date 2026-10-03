package com.comunidapp.app.data.repository

import com.comunidapp.app.core.result.AppError
import com.comunidapp.app.core.result.AppErrorKind
import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.model.AppNotification
import com.comunidapp.app.data.model.NotificationType
import com.comunidapp.app.data.remote.supabase.SupabaseRowDecoding
import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.domain.canonical.CanonicalBackend
import com.comunidapp.app.domain.notifications.NotificationCategory
import com.comunidapp.app.domain.notifications.NotificationDeepLink
import com.comunidapp.app.domain.notifications.NotificationDeepLinkRoute
import com.comunidapp.app.domain.notifications.NotificationEvent
import com.comunidapp.app.domain.notifications.NotificationInboxItem
import com.comunidapp.app.domain.notifications.NotificationPriority
import com.comunidapp.app.domain.notifications.NotificationSensitivity
import com.comunidapp.app.domain.notifications.NotificationState
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

/**
 * Single list path for canonical notifications.
 * Reads `canon_list_my_notifications`. Does not call M06 and does not grant table SELECT.
 * Mark-read, archive, and delete have no canonical RPC, so those calls fail instead of
 * pretending the row changed.
 */
object CanonicalNotificationInbox {
    const val MARK_READ_UNAVAILABLE = "CANONICAL_MARK_READ_UNAVAILABLE"
    const val MUTATION_UNAVAILABLE = "CANONICAL_NOTIFICATION_MUTATION_UNAVAILABLE"

    fun mapVisible(element: JsonElement): List<AppNotification> =
        SupabaseRowDecoding.decodeRows<CanonicalNotificationRow>(element).map { it.toVisible() }

    suspend fun fetchVisible(): List<AppNotification> {
        val element: JsonElement = supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_LIST_MY_NOTIFICATIONS,
            parameters = buildJsonObject { put("p_limit", 100) }
        ).decodeAs()
        return mapVisible(element)
    }
}

class CanonicalNotificationInboxRepository : NotificationInboxRepository {
    override suspend fun listNotifications(userId: String): AppResult<List<NotificationInboxItem>> =
        when (val loaded = load()) {
            is AppResult.Success -> AppResult.Success(loaded.data.map { it.toInboxItem() })
            is AppResult.Failure -> loaded
        }

    override suspend fun getUnreadCount(userId: String): AppResult<Int> =
        when (val loaded = load()) {
            is AppResult.Success -> AppResult.Success(loaded.data.count { it.readAt == null })
            is AppResult.Failure -> loaded
        }

    override suspend fun markRead(
        userId: String,
        notificationId: String,
        now: Instant
    ): AppResult<NotificationInboxItem> = unavailable()

    override suspend fun markAllRead(userId: String, now: Instant): AppResult<Int> = unavailable()

    override suspend fun archive(
        userId: String,
        notificationId: String,
        now: Instant
    ): AppResult<NotificationInboxItem> = unavailable()

    override suspend fun deleteLogical(
        userId: String,
        notificationId: String,
        now: Instant
    ): AppResult<NotificationInboxItem> = unavailable()

    override suspend fun upsertFromEvent(
        userId: String,
        event: NotificationEvent,
        deepLink: NotificationDeepLink,
        now: Instant
    ): AppResult<NotificationInboxItem> = unavailable()

    private suspend fun load(): AppResult<List<AppNotification>> = try {
        AppResult.Success(CanonicalNotificationInbox.fetchVisible())
    } catch (error: Throwable) {
        AppResult.Failure(
            AppError(
                kind = AppErrorKind.UNKNOWN,
                userMessage = "No pudimos cargar las notificaciones.",
                technicalMessage = error.message ?: "CANONICAL_NOTIFICATION_LIST_FAILED",
                code = "CANONICAL_NOTIFICATION_LIST_FAILED"
            )
        )
    }

    private fun <T> unavailable(): AppResult<T> = AppResult.Failure(
        AppError(
            kind = AppErrorKind.FEATURE_TEMPORARILY_DISABLED,
            userMessage = "Esta acción todavía no está disponible.",
            technicalMessage = CanonicalNotificationInbox.MUTATION_UNAVAILABLE,
            code = CanonicalNotificationInbox.MUTATION_UNAVAILABLE
        )
    )
}

@Serializable
private data class CanonicalNotificationRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    val title: String? = null,
    val body: String? = null,
    val state: String? = null,
    val category: String? = null,
    @SerialName("read_at") val readAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("related_id") val relatedId: String? = null,
    @SerialName("related_type") val relatedType: String? = null,
    @SerialName("deep_link_type") val deepLinkType: String? = null,
    @SerialName("deep_link_resource_id") val deepLinkResourceId: String? = null,
    @SerialName("deep_link_resource_type") val deepLinkResourceType: String? = null
)

private fun CanonicalNotificationRow.toVisible(): AppNotification {
    val resourceId = deepLinkResourceId?.takeIf { it.isNotBlank() } ?: relatedId
    val resourceType = deepLinkResourceType?.takeIf { it.isNotBlank() } ?: relatedType ?: deepLinkType
    val read = readAt.toEpochMilli()
    return AppNotification(
        id = id,
        userId = userId,
        type = visibleType(category, deepLinkType, resourceType),
        title = title?.takeIf { it.isNotBlank() } ?: "Notificación",
        body = body.orEmpty(),
        relatedId = resourceId,
        relatedType = resourceType,
        readAt = if (read != null) read else if (state.equals("READ", ignoreCase = true)) 0L else null,
        createdAt = createdAt.toEpochMilli()
    )
}

private fun visibleType(category: String?, deepLinkType: String?, resourceType: String?): NotificationType {
    val route = deepLinkType ?: resourceType
    if (route.equals(NotificationDeepLinkRoute.ORGANIZATION_INVITATION.name, ignoreCase = true) ||
        route.equals("ORG_INVITE", ignoreCase = true)
    ) {
        return NotificationType.ORG_INVITE
    }
    if (category.equals(NotificationCategory.LOST_FOUND.name, ignoreCase = true) ||
        route.equals(NotificationDeepLinkRoute.LOST_FOUND_CASE.name, ignoreCase = true)
    ) {
        return NotificationType.SIGHTING
    }
    return NotificationType.SYSTEM
}

private fun AppNotification.toInboxItem(): NotificationInboxItem {
    val created = createdAt?.let { Instant.ofEpochMilli(it) } ?: Instant.EPOCH
    val route = NotificationDeepLinkRoute.fromString(relatedType) ?: NotificationDeepLinkRoute.SAFE_HOME
    return NotificationInboxItem(
        notificationId = id,
        recipientUserId = userId,
        eventId = id,
        category = if (type == NotificationType.SIGHTING) {
            NotificationCategory.LOST_FOUND
        } else {
            NotificationCategory.OTHER
        },
        priority = NotificationPriority.HIGH,
        sensitivity = NotificationSensitivity.PRIVATE,
        state = if (readAt == null) NotificationState.UNREAD else NotificationState.READ,
        deepLink = NotificationDeepLink(
            routeType = route,
            resourceType = relatedType,
            resourceId = relatedId
        ),
        titleKey = title,
        bodyKey = body,
        deduplicationKey = "canonical:$id",
        resourceType = relatedType,
        resourceId = relatedId,
        createdAt = created,
        updatedAt = created,
        readAt = readAt?.let { Instant.ofEpochMilli(it) }
    )
}

private fun String?.toEpochMilli(): Long? =
    this?.let { raw -> runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull() }
