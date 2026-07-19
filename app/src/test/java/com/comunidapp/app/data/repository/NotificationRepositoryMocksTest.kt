package com.comunidapp.app.data.repository

import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.domain.notifications.NotificationCategory
import com.comunidapp.app.domain.notifications.NotificationChannel
import com.comunidapp.app.domain.notifications.NotificationDeepLink
import com.comunidapp.app.domain.notifications.NotificationDeepLinkRoute
import com.comunidapp.app.domain.notifications.NotificationDeliveryState
import com.comunidapp.app.domain.notifications.NotificationEvent
import com.comunidapp.app.domain.notifications.NotificationInstallationPlatform
import com.comunidapp.app.domain.notifications.NotificationInstallationRules
import com.comunidapp.app.domain.notifications.NotificationOriginModule
import com.comunidapp.app.domain.notifications.NotificationOutboxState
import com.comunidapp.app.domain.notifications.NotificationPriority
import com.comunidapp.app.domain.notifications.NotificationRetryPolicy
import com.comunidapp.app.domain.notifications.NotificationSensitivity
import com.comunidapp.app.domain.notifications.NotificationState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant

class NotificationRepositoryMocksTest {

    private lateinit var mocks: MockNotificationRepositories
    private val now = Instant.parse("2026-07-15T12:00:00Z")

    private fun event(id: String = "evt-1", dedup: String = "dedup-1", idem: String = "idem-1") =
        NotificationEvent(
            eventId = id,
            eventKey = "m02.friend",
            category = NotificationCategory.SOCIAL,
            priority = NotificationPriority.NORMAL,
            sensitivity = NotificationSensitivity.PRIVATE,
            originModule = NotificationOriginModule.M02,
            originType = "friend_request",
            occurredAt = now,
            expiresAt = Instant.parse("2030-01-01T00:00:00Z"),
            payload = mapOf("title_key" to "t", "body_key" to "b"),
            deduplicationKey = dedup,
            idempotencyKey = idem
        )

    @Before
    fun setUp() {
        mocks = MockNotificationRepositories.create(
            clock = { now },
            retryPolicy = NotificationRetryPolicy(maxAttempts = 2, initialDelay = Duration.ofMillis(10))
        )
    }

    @Test
    fun inbox_unique_despite_multi_device_upsert() = runBlocking {
        val deepLink = NotificationDeepLink(NotificationDeepLinkRoute.PROFILE, resourceId = "u2")
        val a = mocks.inbox.upsertFromEvent("u1", event(), deepLink, now) as AppResult.Success
        val b = mocks.inbox.upsertFromEvent("u1", event(), deepLink, now) as AppResult.Success
        assertEquals(a.data.notificationId, b.data.notificationId)
        val list = (mocks.inbox.listNotifications("u1") as AppResult.Success).data
        assertEquals(1, list.size)
    }

    @Test
    fun logout_revokes_only_current_installation() = runBlocking {
        mocks.installation.registerInstallation(
            "inst-1", "u1", NotificationInstallationPlatform.ANDROID, "abcd1234", now
        )
        mocks.installation.registerInstallation(
            "inst-2", "u1", NotificationInstallationPlatform.ANDROID, "deadbeef", now
        )
        mocks.installation.revokeCurrentInstallation("u1", "inst-1", now)
        val list = (mocks.installation.listOwnInstallations("u1") as AppResult.Success).data
        assertTrue(list.first { it.installationId == "inst-1" }.revokedAt != null)
        assertTrue(list.first { it.installationId == "inst-2" }.isActive)
    }

    @Test
    fun multi_device_push_deliveries_separate() = runBlocking {
        val planned = mocks.delivery.planDeliveries(
            event(),
            "u1",
            setOf(NotificationChannel.PUSH),
            listOf("inst-1", "inst-2"),
            now
        ) as AppResult.Success
        assertEquals(2, planned.data.size)
        assertEquals(2, planned.data.map { it.installationId }.toSet().size)
    }

    @Test
    fun delivered_push_does_not_mark_inbox_read() = runBlocking {
        val deepLink = NotificationDeepLink(NotificationDeepLinkRoute.PROFILE)
        val inbox = (mocks.inbox.upsertFromEvent("u1", event(), deepLink, now) as AppResult.Success).data
        val delivery = (mocks.delivery.planDeliveries(
            event(), "u1", setOf(NotificationChannel.PUSH), listOf("inst-1"), now
        ) as AppResult.Success).data.first()
        mocks.delivery.recordAttempt(delivery.deliveryId, now)
        mocks.delivery.markDelivered(delivery.deliveryId, now)
        val after = (mocks.inbox.listNotifications("u1") as AppResult.Success).data.first()
        assertEquals(NotificationState.UNREAD, after.state)
        assertEquals(inbox.notificationId, after.notificationId)
        val d = mocks.store.deliveries[delivery.deliveryId]!!
        assertEquals(NotificationDeliveryState.DELIVERED, d.state)
    }

    @Test
    fun outbox_enqueue_idempotent_and_dead_letter() = runBlocking {
        val e = event()
        val a = mocks.outbox.enqueueEvent(e, now) as AppResult.Success
        val b = mocks.outbox.enqueueEvent(e, now) as AppResult.Success
        assertEquals(a.data.outboxId, b.data.outboxId)

        val claimed = (mocks.outbox.claimNext(now) as AppResult.Success).data!!
        mocks.outbox.markFailed(claimed.outboxId, "NETWORK", now)
        val claimed2 = (mocks.outbox.claimNext(now) as AppResult.Success).data!!
        val failed = mocks.outbox.markFailed(claimed2.outboxId, "NETWORK", now) as AppResult.Success
        assertEquals(NotificationOutboxState.DEAD, failed.data.state)
    }

    @Test
    fun token_fingerprint_only_no_raw_in_model() = runBlocking {
        val raw = "super-secret-fcm-token"
        val fp = NotificationInstallationRules.fingerprintOf(raw)
        val inst = (mocks.installation.registerInstallation(
            "inst-1", "u1", NotificationInstallationPlatform.ANDROID, fp, now
        ) as AppResult.Success).data
        assertFalse(inst.toString().contains(raw))
        assertTrue(inst.tokenFingerprint == fp)
    }

    @Test
    fun mark_read_and_unread_count() = runBlocking {
        val deepLink = NotificationDeepLink(NotificationDeepLinkRoute.SAFE_HOME)
        val item = (mocks.inbox.upsertFromEvent("u1", event(), deepLink, now) as AppResult.Success).data
        assertEquals(1, (mocks.inbox.getUnreadCount("u1") as AppResult.Success).data)
        mocks.inbox.markRead("u1", item.notificationId, now)
        assertEquals(0, (mocks.inbox.getUnreadCount("u1") as AppResult.Success).data)
    }
}
