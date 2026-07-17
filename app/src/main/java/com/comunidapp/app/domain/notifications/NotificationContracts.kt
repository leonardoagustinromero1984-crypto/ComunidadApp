package com.comunidapp.app.domain.notifications

import java.time.Instant

/**
 * Contratos de soporte: delivery e ítem de bandeja in-app.
 */
data class NotificationDelivery(
    val deliveryId: String,
    val eventId: String,
    val recipientUserId: String,
    val installationId: String? = null,
    val channel: NotificationChannel,
    val state: NotificationDeliveryState,
    val attemptCount: Int = 0,
    val lastErrorCode: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deliveredAt: Instant? = null
)

data class NotificationInboxItem(
    val notificationId: String,
    val recipientUserId: String,
    val eventId: String,
    val category: NotificationCategory,
    val priority: NotificationPriority,
    val sensitivity: NotificationSensitivity,
    val state: NotificationState,
    val deepLink: NotificationDeepLink,
    val titleKey: String,
    val bodyKey: String,
    val deduplicationKey: String,
    val organizationId: String? = null,
    val resourceType: String? = null,
    val resourceId: String? = null,
    val isInternal: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
    val readAt: Instant? = null,
    val expiresAt: Instant? = null
)

object NotificationDeliveryRules {

    fun validateNew(delivery: NotificationDelivery): Result<NotificationDelivery> {
        if (delivery.deliveryId.isBlank()) {
            return Result.failure(IllegalArgumentException("DELIVERY_ID_REQUIRED"))
        }
        if (delivery.eventId.isBlank() || delivery.recipientUserId.isBlank()) {
            return Result.failure(IllegalArgumentException("DELIVERY_SCOPE_REQUIRED"))
        }
        if (delivery.channel == NotificationChannel.PUSH && delivery.installationId.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("PUSH_REQUIRES_INSTALLATION"))
        }
        return Result.success(delivery)
    }

    /** Entrega push no marca recipient READ. */
    fun applyDelivered(
        delivery: NotificationDelivery,
        inbox: NotificationInboxItem,
        now: Instant
    ): Pair<NotificationDelivery, NotificationInboxItem> {
        val updatedDelivery = delivery.copy(
            state = NotificationDeliveryState.DELIVERED,
            updatedAt = now,
            deliveredAt = now
        )
        // Inbox state unchanged (remains UNREAD unless already read)
        return updatedDelivery to inbox
    }
}
