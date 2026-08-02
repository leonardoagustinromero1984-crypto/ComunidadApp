package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.M18CommunityEvent
import com.comunidapp.app.data.model.M18EventRegistration
import com.comunidapp.app.data.model.M18EventStatus
import com.comunidapp.app.data.model.M18RegistrationStatus

object M18EventValidators {

    fun validateTitle(title: String): String? = when {
        title.trim().isEmpty() -> "M18_INVALID_TITLE"
        title.trim().length > 120 -> "M18_INVALID_TITLE"
        else -> null
    }

    fun validateDescription(description: String): String? = when {
        description.trim().length < 10 -> "M18_INVALID_DESCRIPTION"
        description.length > 5000 -> "M18_INVALID_DESCRIPTION"
        else -> null
    }

    fun validateCapacity(maxCapacity: Int): String? =
        if (maxCapacity <= 0) "M18_INVALID_CAPACITY" else null

    fun validateDateRange(startsAt: Long, endsAt: Long): String? =
        if (endsAt <= startsAt) "M18_INVALID_DATE_RANGE" else null

    fun validateCheckInWindow(
        checkInOpensAt: Long?,
        checkInClosesAt: Long?,
        startsAt: Long,
        endsAt: Long
    ): String? {
        if (checkInOpensAt == null && checkInClosesAt == null) return null
        if (checkInOpensAt != null && checkInClosesAt != null && checkInClosesAt <= checkInOpensAt) {
            return "M18_INVALID_CHECKIN_WINDOW"
        }
        if (checkInOpensAt != null && checkInOpensAt > endsAt) return "M18_INVALID_CHECKIN_WINDOW"
        if (checkInClosesAt != null && checkInClosesAt < startsAt) return "M18_INVALID_CHECKIN_WINDOW"
        return null
    }

    fun validateStateTransition(
        current: M18EventStatus,
        target: M18EventStatus
    ): String? {
        if (current == target) return null
        if (current.isTerminal) return "M18_STATE_ALREADY_FINAL"
        return when (target) {
            M18EventStatus.DRAFT -> null
            M18EventStatus.PUBLISHED ->
                if (current == M18EventStatus.DRAFT || current == M18EventStatus.PAUSED) null
                else "M18_INVALID_STATE_TRANSITION"
            M18EventStatus.PAUSED ->
                if (current == M18EventStatus.PUBLISHED) null else "M18_INVALID_STATE_TRANSITION"
            M18EventStatus.COMPLETED, M18EventStatus.CANCELLED ->
                if (current == M18EventStatus.PUBLISHED || current == M18EventStatus.PAUSED) null
                else "M18_INVALID_STATE_TRANSITION"
        }
    }

    fun validateCapacityReduction(
        event: M18CommunityEvent,
        newCapacity: Int,
        registrations: List<M18EventRegistration>
    ): String? {
        validateCapacity(newCapacity)?.let { return it }
        val occupied = registrations.count { it.status.occupiesCapacity }
        if (newCapacity < occupied) return "M18_CAPACITY_BELOW_REGISTERED"
        return null
    }

    fun validateRegistration(event: M18CommunityEvent): String? = when (event.status) {
        M18EventStatus.PUBLISHED -> null
        M18EventStatus.PAUSED -> "M18_EVENT_NOT_OPEN"
        M18EventStatus.COMPLETED, M18EventStatus.CANCELLED -> "M18_EVENT_TERMINAL"
        else -> "M18_EVENT_NOT_PUBLIC"
    }

    fun validateCheckIn(
        event: M18CommunityEvent,
        registration: M18EventRegistration,
        now: Long = System.currentTimeMillis()
    ): String? {
        if (event.status != M18EventStatus.PUBLISHED && event.status != M18EventStatus.COMPLETED) {
            return "M18_EVENT_NOT_OPEN"
        }
        if (registration.status != M18RegistrationStatus.REGISTERED) {
            return if (registration.status == M18RegistrationStatus.CHECKED_IN) null
            else "M18_INVALID_CHECKIN_STATE"
        }
        val opens = event.checkInOpensAt ?: (event.startsAt - 3_600_000L)
        val closes = event.checkInClosesAt ?: event.endsAt
        if (now < opens || now > closes) return "M18_CHECKIN_WINDOW_CLOSED"
        return null
    }

    fun formatEventDateRange(startsAt: Long, endsAt: Long): String {
        val start = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(startsAt))
        val end = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(endsAt))
        return "$start – $end"
    }
}
