package com.comunidapp.app.domain.notifications

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class NotificationDeduplicationTest {

    @Test
    fun empty_keys_rejected() {
        val r = NotificationDeduplicationRules.validate(
            NotificationDeduplication("", "idem", "user:1", Duration.ofHours(1))
        )
        assertTrue(r.isFailure)
    }

    @Test
    fun same_event_recipient_one_inbox_identity() {
        val a = NotificationDeduplicationRules.inboxIdentity("evt:1", "u1")
        val b = NotificationDeduplicationRules.inboxIdentity("evt:1", "u1")
        assertTrue(a == b)
    }

    @Test
    fun multi_device_deliveries_distinct_not_inbox_dup() {
        val inbox = NotificationDeduplicationRules.inboxIdentity("evt:1", "u1")
        val d1 = NotificationDeduplicationRules.deliveryIdentity(
            "evt:1", "u1", "inst-1", NotificationChannel.PUSH
        )
        val d2 = NotificationDeduplicationRules.deliveryIdentity(
            "evt:1", "u1", "inst-2", NotificationChannel.PUSH
        )
        assertNotEquals(d1, d2)
        assertTrue(d1.startsWith(inbox))
    }
}
