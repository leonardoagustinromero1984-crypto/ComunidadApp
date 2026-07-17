package com.comunidapp.app.domain.notifications

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class NotificationEventRulesTest {

    private fun baseEvent(
        payload: Map<String, String> = mapOf("title_key" to "t", "body_key" to "b"),
        expiresAt: Instant? = Instant.parse("2030-01-01T00:00:00Z"),
        isInternal: Boolean = false
    ) = NotificationEvent(
        eventId = "evt-1",
        eventKey = "m02.friend.request",
        category = NotificationCategory.SOCIAL,
        priority = NotificationPriority.NORMAL,
        sensitivity = NotificationSensitivity.PRIVATE,
        originModule = NotificationOriginModule.M02,
        originType = "friend_request",
        occurredAt = Instant.parse("2026-01-01T00:00:00Z"),
        expiresAt = expiresAt,
        payload = payload,
        deduplicationKey = "friend-req:a:b",
        idempotencyKey = "idem-1",
        isInternal = isInternal
    )

    @Test
    fun valid_event_ok() {
        assertTrue(NotificationEventRules.validate(baseEvent()).isSuccess)
    }

    @Test
    fun signed_url_payload_rejected() {
        val r = NotificationEventRules.validate(
            baseEvent(payload = mapOf("body" to "https://signed.example/x?token=abc"))
        )
        assertTrue(r.isFailure)
        assertTrue(r.exceptionOrNull()?.message?.contains("PAYLOAD_VALUE_FORBIDDEN") == true)
    }

    @Test
    fun token_key_rejected() {
        val r = NotificationEventRules.validatePayload(mapOf("token" to "abc"))
        assertTrue(r.isFailure)
    }

    @Test
    fun resource_url_rejected() {
        val r = NotificationEventRules.validate(
            baseEvent().copy(resourceId = "https://evil.example/x")
        )
        assertTrue(r.isFailure)
        assertTrue(r.exceptionOrNull()?.message == "RESOURCE_URL_REJECTED")
    }

    @Test
    fun expired_event_rejected() {
        val now = Instant.parse("2026-06-01T00:00:00Z")
        val r = NotificationEventRules.validate(
            baseEvent(expiresAt = Instant.parse("2026-01-02T00:00:00Z")),
            now = now
        )
        assertTrue(r.isFailure)
        assertTrue(r.exceptionOrNull()?.message == "EVENT_EXPIRED")
    }

    @Test
    fun blank_idempotency_rejected() {
        val r = NotificationEventRules.validate(baseEvent().copy(idempotencyKey = " "))
        assertTrue(r.isFailure)
    }
}
