package com.comunidapp.app.domain.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationStateTransitionTest {

    @Test
    fun unread_to_read_ok() {
        assertTrue(
            NotificationStateTransitions.canTransition(
                NotificationState.UNREAD,
                NotificationState.READ
            )
        )
    }

    @Test
    fun deleted_is_terminal() {
        assertFalse(
            NotificationStateTransitions.canTransition(
                NotificationState.DELETED,
                NotificationState.READ
            )
        )
    }

    @Test
    fun archive_does_not_delete() {
        assertTrue(
            NotificationStateTransitions.canTransition(
                NotificationState.UNREAD,
                NotificationState.ARCHIVED
            )
        )
        assertTrue(
            NotificationStateTransitions.canTransition(
                NotificationState.ARCHIVED,
                NotificationState.DELETED
            )
        )
    }

    @Test
    fun delivery_delivered_does_not_mark_read() {
        assertFalse(NotificationDeliveryStateTransitions.deliveredPushMarksRecipientRead())
    }

    @Test
    fun delivery_pending_to_processing_ok() {
        assertTrue(
            NotificationDeliveryStateTransitions.canTransition(
                NotificationDeliveryState.PENDING,
                NotificationDeliveryState.PROCESSING
            )
        )
    }

    @Test
    fun delivery_delivered_terminal() {
        assertFalse(
            NotificationDeliveryStateTransitions.canTransition(
                NotificationDeliveryState.DELIVERED,
                NotificationDeliveryState.PENDING
            )
        )
    }
}
