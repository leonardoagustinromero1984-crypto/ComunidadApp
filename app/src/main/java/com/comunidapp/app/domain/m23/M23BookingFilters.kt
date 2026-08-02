package com.comunidapp.app.domain.m23

import com.comunidapp.app.data.model.M23Booking
import com.comunidapp.app.data.model.M23BookingFilter
import com.comunidapp.app.data.model.M23BookingListScope
import com.comunidapp.app.data.model.M23BookingMetrics
import com.comunidapp.app.data.model.M23BookingStatus
import com.comunidapp.app.data.model.M23BookingStatusFilter
import com.comunidapp.app.data.model.M23BookingSummary
import com.comunidapp.app.data.model.M23ProviderBookingFilter
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object M23BookingFilters {
    private val terminal = setOf(
        M23BookingStatus.REJECTED,
        M23BookingStatus.CANCELLED_BY_CUSTOMER,
        M23BookingStatus.CANCELLED_BY_PROVIDER,
        M23BookingStatus.COMPLETED,
        M23BookingStatus.NO_SHOW,
        M23BookingStatus.EXPIRED
    )

    fun matchesCustomer(booking: M23Booking, filter: M23BookingFilter, clock: Clock = Clock.systemUTC()): Boolean {
        val now = Instant.now(clock)
        if (filter.providerId != null && booking.providerId != filter.providerId) return false
        if (filter.petId != null && booking.petId != filter.petId) return false
        if (filter.fromDate != null && booking.startsAt.atZone(booking.zoneId).toLocalDate().isBefore(filter.fromDate)) return false
        if (filter.toDate != null && booking.startsAt.atZone(booking.zoneId).toLocalDate().isAfter(filter.toDate)) return false
        when (filter.scope) {
            M23BookingListScope.UPCOMING -> if (booking.startsAt.isBefore(now) || booking.status in terminal) return false
            M23BookingListScope.HISTORY -> if (booking.startsAt.isAfter(now) && booking.status !in terminal) return false
            M23BookingListScope.ALL -> Unit
        }
        return filter.status?.let { statusFilter(booking.status) == it } ?: true
    }

    fun matchesProvider(booking: M23Booking, filter: M23ProviderBookingFilter): Boolean {
        if (filter.branchId != null && booking.branchId != filter.branchId) return false
        if (filter.offeringId != null && booking.offeringId != filter.offeringId) return false
        if (filter.status != null && statusFilter(booking.status) != filter.status) return false
        val day = booking.startsAt.atZone(booking.zoneId).toLocalDate()
        if (filter.day != null && day != filter.day) return false
        if (filter.weekStart != null) {
            val end = filter.weekStart.plusDays(6)
            if (day.isBefore(filter.weekStart) || day.isAfter(end)) return false
        }
        return true
    }

    fun sortCustomer(summaries: List<M23BookingSummary>): List<M23BookingSummary> =
        summaries.sortedByDescending { it.booking.startsAt }

    fun sortProvider(bookings: List<M23Booking>): List<M23Booking> =
        bookings.sortedBy { it.startsAt }

    fun metrics(bookings: List<M23Booking>): M23BookingMetrics = M23BookingMetrics(
        requested = bookings.count { it.status == M23BookingStatus.REQUESTED },
        confirmed = bookings.count { it.status == M23BookingStatus.CONFIRMED },
        cancelled = bookings.count { it.status.name.startsWith("CANCELLED") },
        completed = bookings.count { it.status == M23BookingStatus.COMPLETED },
        noShow = bookings.count { it.status == M23BookingStatus.NO_SHOW },
        expired = bookings.count { it.status == M23BookingStatus.EXPIRED }
    )

    fun weekStart(day: LocalDate): LocalDate = day.minusDays((day.dayOfWeek.value - 1).toLong())

    private fun statusFilter(status: M23BookingStatus): M23BookingStatusFilter = when (status) {
        M23BookingStatus.REQUESTED -> M23BookingStatusFilter.REQUESTED
        M23BookingStatus.CONFIRMED -> M23BookingStatusFilter.CONFIRMED
        M23BookingStatus.COMPLETED -> M23BookingStatusFilter.COMPLETED
        M23BookingStatus.NO_SHOW -> M23BookingStatusFilter.NO_SHOW
        M23BookingStatus.EXPIRED -> M23BookingStatusFilter.EXPIRED
        M23BookingStatus.REJECTED, M23BookingStatus.CANCELLED_BY_CUSTOMER, M23BookingStatus.CANCELLED_BY_PROVIDER ->
            M23BookingStatusFilter.CANCELLED
    }
}
