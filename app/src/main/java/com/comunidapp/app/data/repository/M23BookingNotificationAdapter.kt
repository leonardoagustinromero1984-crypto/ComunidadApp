package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.M23Booking

enum class M23BookingNotificationEvent {
    REQUESTED, CONFIRMED, REJECTED, CANCELLED, RESCHEDULED, REMINDER, COMPLETED, NO_SHOW
}

/** Boundary stub: notification dispatch belongs to M06 and is intentionally best-effort here. */
fun interface M23BookingNotificationAdapter {
    fun onBookingEvent(booking: M23Booking, event: M23BookingNotificationEvent)
}

object NoOpM23BookingNotificationAdapter : M23BookingNotificationAdapter {
    override fun onBookingEvent(booking: M23Booking, event: M23BookingNotificationEvent) = Unit
}

class FailingM23BookingNotificationAdapter : M23BookingNotificationAdapter {
    override fun onBookingEvent(booking: M23Booking, event: M23BookingNotificationEvent) {
        throw IllegalStateException("M06_UNAVAILABLE")
    }
}
