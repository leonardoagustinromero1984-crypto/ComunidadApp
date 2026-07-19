package com.comunidapp.app.domain.notifications

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class NotificationRecipientRulesTest {

    private val event = NotificationEvent(
        eventId = "evt-1",
        eventKey = "m04.support.internal",
        category = NotificationCategory.SUPPORT,
        priority = NotificationPriority.NORMAL,
        sensitivity = NotificationSensitivity.SENSITIVE,
        originModule = NotificationOriginModule.M04,
        originType = "internal_note",
        occurredAt = Instant.parse("2026-01-01T00:00:00Z"),
        payload = mapOf("title_key" to "t"),
        deduplicationKey = "support-int:1",
        idempotencyKey = "idem-1",
        isInternal = true
    )

    @Test
    fun internal_to_requester_forbidden() {
        val r = NotificationRecipientRules.validate(
            NotificationRecipient(
                kind = NotificationRecipientKind.PLATFORM_PERMISSION,
                requiredPermission = "support.view_sensitive",
                reason = "internal note",
                isRequester = true,
                isStaff = true
            ),
            event
        )
        assertTrue(r.isFailure)
        assertTrue(r.exceptionOrNull()?.message == "INTERNAL_TO_REQUESTER_FORBIDDEN")
    }

    @Test
    fun arbitrary_direct_recipient_rejected() {
        val openEvent = event.copy(isInternal = false, category = NotificationCategory.SOCIAL)
        val r = NotificationRecipientRules.validate(
            NotificationRecipient(
                recipientUserId = "victim-user",
                kind = NotificationRecipientKind.DIRECT_USER,
                reason = "client chose target"
            ),
            openEvent,
            allowDirectUserId = false
        )
        assertTrue(r.isFailure)
        assertTrue(r.exceptionOrNull()?.message == "ARBITRARY_DIRECT_RECIPIENT_REJECTED")
    }

    @Test
    fun internal_requires_staff() {
        val r = NotificationRecipientRules.validate(
            NotificationRecipient(
                kind = NotificationRecipientKind.PLATFORM_PERMISSION,
                requiredPermission = "support.view_sensitive",
                reason = "staff only",
                isStaff = false
            ),
            event
        )
        assertTrue(r.isFailure)
    }
}
