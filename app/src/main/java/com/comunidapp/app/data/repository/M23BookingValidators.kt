package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.M23AvailabilityRule
import com.comunidapp.app.data.model.M23Booking
import com.comunidapp.app.data.model.M23BookingPolicy
import java.time.Clock
import java.time.Duration
import java.time.Instant

object M23BookingValidators {
    fun validateRule(rule: M23AvailabilityRule): String? = when {
        !rule.startTime.isBefore(rule.endTime) || rule.slotDurationMinutes !in 5..480 -> "M23_INVALID_AVAILABILITY_RULE"
        Duration.between(rule.startTime, rule.endTime).toMinutes() % rule.slotDurationMinutes != 0L -> "M23_RULE_NOT_ALIGNED"
        else -> null
    }
    fun validateBooking(booking: M23Booking, policy: M23BookingPolicy, clock: Clock): String? = when {
        !booking.startsAt.isBefore(booking.endsAt) -> "M23_INVALID_SLOT"
        booking.startsAt.isBefore(Instant.now(clock).plusSeconds(policy.advance.minimumAdvanceMinutes * 60L)) -> "M23_ADVANCE_TOO_SHORT"
        booking.startsAt.isAfter(Instant.now(clock).plusSeconds(policy.advance.maximumAdvanceDays * 86_400L)) -> "M23_ADVANCE_TOO_LONG"
        booking.customerNote?.length ?: 0 > 500 -> "M23_NOTE_TOO_LONG"
        else -> null
    }
}
