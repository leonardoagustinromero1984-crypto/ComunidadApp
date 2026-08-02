package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.M23Booking

/** Boundary stub: notification dispatch belongs to M06 and is intentionally best-effort here. */
fun interface M23BookingNotificationAdapter {
    suspend fun onBookingChanged(booking: M23Booking)
}

object NoOpM23BookingNotificationAdapter : M23BookingNotificationAdapter {
    override suspend fun onBookingChanged(booking: M23Booking) = Unit
}
