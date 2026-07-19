package com.comunidapp.app.domain.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class NotificationOutboxRulesTest {

    private val event = NotificationEvent(
        eventId = "evt-1",
        eventKey = "m02.social",
        category = NotificationCategory.SOCIAL,
        priority = NotificationPriority.NORMAL,
        sensitivity = NotificationSensitivity.PRIVATE,
        originModule = NotificationOriginModule.M02,
        originType = "friend",
        occurredAt = Instant.parse("2026-01-01T00:00:00Z"),
        expiresAt = Instant.parse("2030-01-01T00:00:00Z"),
        payload = mapOf("title_key" to "t"),
        deduplicationKey = "d1",
        idempotencyKey = "idem-1"
    )

    @Test
    fun idempotency_mandatory() {
        val r = NotificationOutboxRules.validateEnqueue(event, "")
        assertTrue(r.isFailure)
    }

    @Test
    fun pending_to_claimed_ok() {
        assertTrue(
            NotificationOutboxRules.canTransition(
                NotificationOutboxState.PENDING,
                NotificationOutboxState.CLAIMED
            )
        )
    }

    @Test
    fun processed_terminal() {
        assertFalse(
            NotificationOutboxRules.canTransition(
                NotificationOutboxState.PROCESSED,
                NotificationOutboxState.PENDING
            )
        )
    }

    @Test
    fun dead_terminal() {
        assertFalse(
            NotificationOutboxRules.canTransition(
                NotificationOutboxState.DEAD,
                NotificationOutboxState.CLAIMED
            )
        )
    }
}
