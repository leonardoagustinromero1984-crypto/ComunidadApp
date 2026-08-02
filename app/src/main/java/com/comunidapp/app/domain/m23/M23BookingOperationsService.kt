package com.comunidapp.app.domain.m23

import com.comunidapp.app.data.model.M23Booking
import com.comunidapp.app.data.model.M23BookingHistoryEntry
import com.comunidapp.app.data.model.M23BookingPolicy
import com.comunidapp.app.data.model.M23BookingStatus
import java.time.Clock
import java.time.Instant

/** Lifecycle rules shared by mock and future remote transaction implementations. */
class M23BookingOperationsService(private val clock: Clock = Clock.systemUTC()) {
    fun ensureAvailable(candidate: M23Booking, existing: List<M23Booking>) {
        val occupied = setOf(M23BookingStatus.REQUESTED, M23BookingStatus.CONFIRMED, M23BookingStatus.COMPLETED, M23BookingStatus.NO_SHOW)
        if (existing.any { it.id != candidate.id && it.providerId == candidate.providerId && it.status in occupied && M23SlotGenerator.overlaps(it.startsAt.epochSecond, it.endsAt.epochSecond, candidate.startsAt.epochSecond, candidate.endsAt.epochSecond) }) {
            fail("M23_SLOT_UNAVAILABLE")
        }
    }

    fun confirm(booking: M23Booking): M23Booking = transition(booking, M23BookingStatus.CONFIRMED)
    fun reject(booking: M23Booking): M23Booking = transition(booking, M23BookingStatus.REJECTED)
    fun complete(booking: M23Booking): M23Booking = transition(booking, M23BookingStatus.COMPLETED)
    fun noShow(booking: M23Booking, policy: M23BookingPolicy): M23Booking {
        if (Instant.now(clock).isBefore(booking.startsAt.plusSeconds(policy.noShow.graceMinutes * 60L))) fail("M23_NO_SHOW_TOO_EARLY")
        return transition(booking, M23BookingStatus.NO_SHOW)
    }
    fun cancel(booking: M23Booking, byProvider: Boolean, policy: M23BookingPolicy): M23Booking {
        if (Instant.now(clock).isAfter(booking.startsAt.minusSeconds(policy.cancellation.minimumNoticeMinutes * 60L))) fail("M23_CANCELLATION_WINDOW_CLOSED")
        return transition(booking, if (byProvider) M23BookingStatus.CANCELLED_BY_PROVIDER else M23BookingStatus.CANCELLED_BY_CUSTOMER)
    }
    fun reschedule(booking: M23Booking, replacement: M23Booking, policy: M23BookingPolicy, existing: List<M23Booking>): M23Booking {
        if (Instant.now(clock).isAfter(booking.startsAt.minusSeconds(policy.reschedule.minimumNoticeMinutes * 60L))) fail("M23_RESCHEDULE_WINDOW_CLOSED")
        ensureAvailable(replacement, existing)
        return replacement.copy(status = M23BookingStatus.REQUESTED, updatedAt = Instant.now(clock))
    }
    fun history(before: M23Booking, after: M23Booking, reason: String? = null) =
        M23BookingHistoryEntry(Instant.now(clock), before.status, after.status, reason)

    private fun transition(booking: M23Booking, target: M23BookingStatus): M23Booking {
        if (booking.status == target) return booking
        val valid = when (target) {
            M23BookingStatus.CONFIRMED, M23BookingStatus.REJECTED -> booking.status == M23BookingStatus.REQUESTED
            M23BookingStatus.CANCELLED_BY_CUSTOMER, M23BookingStatus.CANCELLED_BY_PROVIDER -> booking.status in setOf(M23BookingStatus.REQUESTED, M23BookingStatus.CONFIRMED)
            M23BookingStatus.COMPLETED, M23BookingStatus.NO_SHOW -> booking.status == M23BookingStatus.CONFIRMED
            M23BookingStatus.EXPIRED -> booking.status == M23BookingStatus.REQUESTED
            M23BookingStatus.REQUESTED -> false
        }
        if (!valid) fail("M23_INVALID_STATUS_TRANSITION")
        return booking.copy(status = target, updatedAt = Instant.now(clock))
    }

    private fun fail(code: String): Nothing = throw IllegalStateException(code)
}
