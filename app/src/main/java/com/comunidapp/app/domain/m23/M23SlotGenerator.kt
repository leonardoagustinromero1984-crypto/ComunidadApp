package com.comunidapp.app.domain.m23

import com.comunidapp.app.data.model.M23AvailabilityException
import com.comunidapp.app.data.model.M23AvailabilityRule
import com.comunidapp.app.data.model.M23BookableSlot
import com.comunidapp.app.data.model.M23Booking
import com.comunidapp.app.data.model.M23BookingModality
import com.comunidapp.app.data.model.M23BookingStatus
import com.comunidapp.app.data.model.M23ExceptionType
import com.comunidapp.app.data.model.M23ScheduleDay
import com.comunidapp.app.data.model.M23SlotPage
import com.comunidapp.app.data.model.M23SlotQuery
import java.time.LocalDate
import java.time.LocalDateTime

/** Bounded, deterministic slot projection. It never stores or materializes an unbounded agenda. */
object M23SlotGenerator {
    private val occupying = setOf(M23BookingStatus.REQUESTED, M23BookingStatus.CONFIRMED, M23BookingStatus.COMPLETED, M23BookingStatus.NO_SHOW)

    fun generate(
        query: M23SlotQuery,
        rules: List<M23AvailabilityRule>,
        exceptions: List<M23AvailabilityException>,
        bookings: List<M23Booking>,
        modality: M23BookingModality = M23BookingModality.IN_PERSON,
        maxDays: Int = 31
    ): M23SlotPage {
        require(!query.to.isBefore(query.from)) { "M23_INVALID_DATE_RANGE" }
        val end = minOf(query.to, query.from.plusDays((maxDays - 1).toLong()))
        val days = generateSequence(query.from) { date -> date.plusDays(1).takeIf { !it.isAfter(end) } }.map { date ->
            M23ScheduleDay(date, slotsForDay(date, query, rules, exceptions, bookings, modality))
        }.toList()
        return M23SlotPage(days, if (end.isBefore(query.to)) end.plusDays(1) else null)
    }

    private fun slotsForDay(
        date: LocalDate, query: M23SlotQuery, rules: List<M23AvailabilityRule>,
        exceptions: List<M23AvailabilityException>, bookings: List<M23Booking>, modality: M23BookingModality
    ): List<M23BookableSlot> {
        val relevantExceptions = exceptions.filter { it.providerId == query.providerId && (it.offeringId == null || it.offeringId == query.offeringId) && it.date == date }
        if (relevantExceptions.any { it.type in setOf(M23ExceptionType.BLOCKED, M23ExceptionType.HOLIDAY, M23ExceptionType.PERSONAL_LEAVE, M23ExceptionType.ORGANIZATION_CLOSURE) }) return emptyList()
        val windows = rules.filter { it.providerId == query.providerId && it.offeringId == query.offeringId && it.status.name == "ACTIVE" && it.dayOfWeek == date.dayOfWeek }
            .map { it.startTime to it.endTime } + relevantExceptions.filter { it.type == M23ExceptionType.SPECIAL_OPENING && it.startTime != null && it.endTime != null }.map { it.startTime!! to it.endTime!! }
        return windows.flatMap { (start, end) ->
            val duration = rules.firstOrNull { it.providerId == query.providerId && it.offeringId == query.offeringId && it.dayOfWeek == date.dayOfWeek }?.slotDurationMinutes ?: 30
            generateSequence(start) { it.plusMinutes(duration.toLong()).takeIf { next -> !next.plusMinutes(duration.toLong()).isAfter(end) } }
                .map { time ->
                    val begins = LocalDateTime.of(date, time).atZone(query.zoneId).toInstant()
                    M23BookableSlot(query.providerId, query.offeringId, begins, begins.plusSeconds(duration * 60L), modality)
                }.filter { slot -> bookings.none { it.providerId == slot.providerId && it.status in occupying && overlaps(it.startsAt.epochSecond, it.endsAt.epochSecond, slot.startsAt.epochSecond, slot.endsAt.epochSecond) } }
                .toList()
        }.distinctBy { it.startsAt }.sortedBy { it.startsAt }
    }

    fun overlaps(startA: Long, endA: Long, startB: Long, endB: Long): Boolean = startA < endB && startB < endA
}
