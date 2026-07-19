package com.comunidapp.app.data.notifications

import com.comunidapp.app.data.model.AppNotification
import com.comunidapp.app.data.model.NotificationType
import com.comunidapp.app.domain.notifications.NotificationCategory
import com.comunidapp.app.domain.notifications.NotificationDeepLinkRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationLegacyCompatibilityTest {

    @Test
    fun maps_all_exact_legacy_types() {
        assertEquals(
            NotificationCategory.SOCIAL,
            LegacyNotificationTypeAdapter.toCategory(NotificationType.FRIEND_REQUEST)
        )
        assertEquals(
            NotificationCategory.SOCIAL,
            LegacyNotificationTypeAdapter.toCategory(NotificationType.FRIEND_ACCEPTED)
        )
        assertEquals(
            NotificationCategory.MESSAGE,
            LegacyNotificationTypeAdapter.toCategory(NotificationType.CHAT_MESSAGE)
        )
        assertEquals(
            NotificationCategory.ADOPTION,
            LegacyNotificationTypeAdapter.toCategory(NotificationType.ADOPTION_REQUEST)
        )
        assertEquals(
            NotificationCategory.FOSTER,
            LegacyNotificationTypeAdapter.toCategory(NotificationType.FOSTER_REQUEST)
        )
        assertEquals(
            NotificationCategory.APPOINTMENT,
            LegacyNotificationTypeAdapter.toCategory(NotificationType.BOOKING)
        )
        assertEquals(
            NotificationCategory.LOST_FOUND,
            LegacyNotificationTypeAdapter.toCategory(NotificationType.SIGHTING)
        )
        assertEquals(
            NotificationCategory.SYSTEM,
            LegacyNotificationTypeAdapter.toCategory(NotificationType.SYSTEM)
        )
    }

    @Test
    fun unknown_payload_safe_home() {
        val link = LegacyNotificationDeepLinkAdapter.fromUnknownPayload()
        assertEquals(NotificationDeepLinkRoute.SAFE_HOME, link.routeType)
        val fromRaw = LegacyNotificationDeepLinkAdapter.fromRawTypeName("TOTALLY_UNKNOWN")
        assertEquals(NotificationDeepLinkRoute.SAFE_HOME, fromRaw.routeType)
    }

    @Test
    fun legacy_never_grants_permission_nor_emits() {
        assertFalse(LegacyNotificationTypeAdapter.grantsPermission())
        assertFalse(LegacyNotificationTypeAdapter.mayEmitNewEvents())
    }

    @Test
    fun map_app_notification_read_compatible() {
        val mapped = LegacyNotificationTypeAdapter.map(
            AppNotification(
                id = "1",
                userId = "u1",
                type = NotificationType.CHAT_MESSAGE,
                title = "Chat",
                body = "Hola",
                relatedId = "conv-1",
                relatedType = "conversation"
            )
        )
        assertEquals(NotificationCategory.MESSAGE, mapped.category)
        assertEquals(NotificationDeepLinkRoute.CHAT, mapped.deepLink.routeType)
        assertTrue(mapped.relatedId == "conv-1")
    }

    @Test
    fun uri_related_id_falls_back_safe_home() {
        val link = LegacyNotificationDeepLinkAdapter.toDeepLink(
            NotificationType.SIGHTING,
            "https://evil.example/x",
            "sighting"
        )
        assertEquals(NotificationDeepLinkRoute.SAFE_HOME, link.routeType)
    }
}