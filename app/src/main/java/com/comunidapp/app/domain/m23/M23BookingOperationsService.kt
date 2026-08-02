package com.comunidapp.app.domain.m23

import com.comunidapp.app.data.model.M23Booking
import com.comunidapp.app.data.model.M23BookingHistoryEntry
import com.comunidapp.app.data.model.M23BookingPolicy
import com.comunidapp.app.data.model.M23BookingStatus
import java.time.Clock
import java.time.Instant

/** Lifecycle rules shared by mock and remote transaction implementations. */
class M23BookingOperationsService(private val clock: Clock = Clock.systemUTC()) {
    private val occupied = setOf(
        M23BookingStatus.REQUESTED,
        M23BookingStatus.CONFIRMED,
        M23BookingStatus.COMPLETED,
        M23BookingStatus.NO_SHOW
    )

    fun ensureAvailable(candidate: M23Booking, existing: List<M23Booking>) {
        if (existing.any {
                it.id != candidate.id &&
                    it.rescheduledFromBookingId != candidate.id &&
                    it.providerId == candidate.providerId &&
                    it.status in occupied &&
                    M23SlotGenerator.overlaps(
                        it.startsAt.epochSecond,
                        it.endsAt.epochSecond,
                        candidate.startsAt.epochSecond,
                        candidate.endsAt.epochSecond
                    )
            }
        ) {
            fail("M23_SLOT_UNAVAILABLE")
        }
    }

    fun confirm(booking: M23Booking, existing: List<M23Booking>): M23Booking {
        if (Instant.now(clock).isAfter(booking.startsAt)) fail("M23_INVALID_STATUS_TRANSITION")
        val confirmed = transition(booking, M23BookingStatus.CONFIRMED)
        ensureAvailable(confirmed, existing.filter { it.id != booking.id })
        return confirmed
    }

    fun reject(booking: M23Booking): M23Booking = transition(booking, M23BookingStatus.REJECTED)

    fun complete(booking: M23Booking): M23Booking {
        if (Instant.now(clock).isBefore(booking.endsAt)) fail("M23_COMPLETE_TOO_EARLY")
        return transition(booking, M23BookingStatus.COMPLETED)
    }

    fun noShow(booking: M23Booking, policy: M23BookingPolicy): M23Booking {
        if (Instant.now(clock).isBefore(booking.startsAt.plusSeconds(policy.noShow.graceMinutes * 60L))) {
            fail("M23_NO_SHOW_TOO_EARLY")
        }
        return transition(booking, M23BookingStatus.NO_SHOW)
    }

    fun expire(booking: M23Booking, expirationMinutes: Long = 24 * 60): M23Booking {
        if (booking.status != M23BookingStatus.REQUESTED) fail("M23_INVALID_STATUS_TRANSITION")
        if (Instant.now(clock).isBefore(booking.createdAt.plusSeconds(expirationMinutes * 60))) {
            fail("M23_EXPIRE_TOO_EARLY")
        }
        return transition(booking, M23BookingStatus.EXPIRED)
    }

    fun cancel(booking: M23Booking, byProvider: Boolean, policy: M23BookingPolicy): M23Booking {
        if (!byProvider && Instant.now(clock).isAfter(booking.startsAt.minusSeconds(policy.cancellation.minimumNoticeMinutes * 60L))) {
            fail("M23_CANCELLATION_WINDOW_CLOSED")
        }
        return transition(
            booking,
            if (byProvider) M23BookingStatus.CANCELLED_BY_PROVIDER else M23BookingStatus.CANCELLED_BY_CUSTOMER
        )
    }

    fun reschedule(
        booking: M23Booking,
        replacement: M23Booking,
        policy: M23BookingPolicy,
        existing: List<M23Booking>
    ): Pair<M23Booking, M23Booking> {
        if (Instant.now(clock).isAfter(booking.startsAt.minusSeconds(policy.reschedule.minimumNoticeMinutes * 60L))) {
            fail("M23_RESCHEDULE_WINDOW_CLOSED")
        }
        if (booking.status !in setOf(M23BookingStatus.REQUESTED, M23BookingStatus.CONFIRMED)) {
            fail("M23_INVALID_STATUS_TRANSITION")
        }
        ensureAvailable(replacement, existing.filter { it.id != booking.id })
        val cancelled = transition(
            booking,
            if (booking.status == M23BookingStatus.CONFIRMED) M23BookingStatus.CANCELLED_BY_CUSTOMER
            else M23BookingStatus.CANCELLED_BY_CUSTOMER
        )
        val requested = replacement.copy(
            status = M23BookingStatus.REQUESTED,
            rescheduledFromBookingId = booking.id,
            updatedAt = Instant.now(clock)
        )
        return cancelled to requested
    }

    fun history(before: M23Booking, after: M23Booking, reason: String? = null) =
        M23BookingHistoryEntry(Instant.now(clock), before.status, after.status, reason)

    private fun transition(booking: M23Booking, target: M23BookingStatus): M23Booking {
        if (booking.status == target) return booking
        val valid = when (target) {
            M23BookingStatus.CONFIRMED, M23BookingStatus.REJECTED -> booking.status == M23BookingStatus.REQUESTED
            M23BookingStatus.CANCELLED_BY_CUSTOMER, M23BookingStatus.CANCELLED_BY_PROVIDER ->
                booking.status in setOf(M23BookingStatus.REQUESTED, M23BookingStatus.CONFIRMED)
            M23BookingStatus.COMPLETED, M23BookingStatus.NO_SHOW -> booking.status == M23BookingStatus.CONFIRMED
            M23BookingStatus.EXPIRED -> booking.status == M23BookingStatus.REQUESTED
            M23BookingStatus.REQUESTED -> false
        }
        if (!valid) fail("M23_INVALID_STATUS_TRANSITION")
        return booking.copy(status = target, updatedAt = Instant.now(clock))
    }

    private fun fail(code: String): Nothing = throw IllegalStateException(code)
}
