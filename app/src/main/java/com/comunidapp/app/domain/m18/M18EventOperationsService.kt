package com.comunidapp.app.domain.m18

import com.comunidapp.app.data.model.M18CapacityCalculator
import com.comunidapp.app.data.model.M18CommunityEvent
import com.comunidapp.app.data.model.M18EventOperationsSummary
import com.comunidapp.app.data.model.M18EventParticipantItem
import com.comunidapp.app.data.model.M18EventRegistration
import com.comunidapp.app.data.model.M18RegistrationStatus
import com.comunidapp.app.data.repository.M18EventValidators

/** Reglas puras de participación M18 — sin red ni UI. */
object M18EventOperationsService {

    fun resolveRegistrationStatus(
        event: M18CommunityEvent,
        existing: M18EventRegistration?,
        registrations: List<M18EventRegistration>
    ): M18RegistrationStatus {
        val summary = M18CapacityCalculator.summarize(
            event.maxCapacity, event.waitlistEnabled, registrations
        )
        return when {
            summary.availableSpots > 0 -> M18RegistrationStatus.REGISTERED
            summary.isWaitlistOpen -> M18RegistrationStatus.WAITLISTED
            else -> throw IllegalStateException("M18_EVENT_FULL")
        }
    }

    fun shouldIdempotentReturn(existing: M18EventRegistration): Boolean =
        existing.status in setOf(
            M18RegistrationStatus.REGISTERED,
            M18RegistrationStatus.WAITLISTED,
            M18RegistrationStatus.CHECKED_IN,
            M18RegistrationStatus.ATTENDED
        )

    fun promoteNextWaitlisted(
        event: M18CommunityEvent,
        registrations: List<M18EventRegistration>
    ): M18EventRegistration? {
        val summary = M18CapacityCalculator.summarize(
            event.maxCapacity, event.waitlistEnabled, registrations
        )
        if (summary.availableSpots <= 0) return null
        val next = registrations
            .filter { it.status == M18RegistrationStatus.WAITLISTED }
            .minByOrNull { it.registeredAt } ?: return null
        return next.copy(status = M18RegistrationStatus.REGISTERED)
    }

    fun buildOperationsSummary(
        event: M18CommunityEvent,
        registrations: List<M18EventRegistration>
    ): M18EventOperationsSummary {
        val capacity = M18CapacityCalculator.summarize(
            event.maxCapacity, event.waitlistEnabled, registrations
        )
        val cancelled = registrations.count { it.status == M18RegistrationStatus.CANCELLED }
        val checkedIn = registrations.count {
            it.status == M18RegistrationStatus.CHECKED_IN ||
                it.status == M18RegistrationStatus.ATTENDED
        }
        val attended = registrations.count { it.status == M18RegistrationStatus.ATTENDED }
        val noShow = registrations.count { it.status == M18RegistrationStatus.NO_SHOW }
        val rejected = registrations.count { it.status == M18RegistrationStatus.REJECTED }
        val occupied = registrations.count { it.status.occupiesCapacity }
        val inconsistent = occupied > event.maxCapacity
        val occupancyPercent = if (event.maxCapacity > 0) {
            ((occupied.toDouble() / event.maxCapacity) * 100).toInt().coerceIn(0, 100)
        } else 0
        val conversion = if (capacity.registeredCount > 0) {
            ((attended.toDouble() / capacity.registeredCount) * 100).toInt().coerceIn(0, 100)
        } else 0
        return M18EventOperationsSummary(
            eventId = event.id,
            maxCapacity = event.maxCapacity,
            registeredCount = capacity.registeredCount,
            waitlistCount = capacity.waitlistCount,
            cancelledCount = cancelled,
            checkedInCount = checkedIn,
            attendedCount = attended,
            noShowCount = noShow,
            rejectedCount = rejected,
            availableSpots = capacity.availableSpots,
            occupancyPercent = occupancyPercent,
            registrationToAttendancePercent = conversion,
            hasCapacityInconsistency = inconsistent
        )
    }

    fun toParticipantItem(
        registration: M18EventRegistration,
        event: M18CommunityEvent,
        now: Long = System.currentTimeMillis()
    ): M18EventParticipantItem {
        val alias = registration.attendeeDisplayName?.trim()?.takeIf { it.isNotEmpty() }
            ?: "Participante"
        val canCheckIn = M18EventValidators.validateCheckIn(event, registration, now) == null &&
            registration.status == M18RegistrationStatus.REGISTERED
        val canMarkAttendance = registration.status == M18RegistrationStatus.CHECKED_IN ||
            registration.status == M18RegistrationStatus.REGISTERED
        val canMarkNoShow = now > event.endsAt &&
            registration.status in setOf(
                M18RegistrationStatus.REGISTERED,
                M18RegistrationStatus.CHECKED_IN
            )
        return M18EventParticipantItem(
            registrationId = registration.id,
            displayAlias = alias,
            status = registration.status,
            registeredAt = registration.registeredAt,
            checkedInAt = registration.checkedInAt,
            canCheckIn = canCheckIn,
            canMarkAttendance = canMarkAttendance,
            canMarkNoShow = canMarkNoShow
        )
    }

    fun validateMarkAttendance(
        event: M18CommunityEvent,
        registration: M18EventRegistration
    ): String? {
        if (registration.status == M18RegistrationStatus.ATTENDED) return null
        if (registration.status == M18RegistrationStatus.CHECKED_IN ||
            registration.status == M18RegistrationStatus.REGISTERED
        ) {
            return null
        }
        return "M18_INVALID_ATTENDANCE_STATE"
    }

    fun validateMarkNoShow(
        event: M18CommunityEvent,
        registration: M18EventRegistration,
        now: Long = System.currentTimeMillis()
    ): String? {
        if (registration.status == M18RegistrationStatus.NO_SHOW) return null
        if (now <= event.endsAt) return "M18_EVENT_NOT_ENDED"
        if (registration.status !in setOf(
                M18RegistrationStatus.REGISTERED,
                M18RegistrationStatus.CHECKED_IN
            )
        ) {
            return "M18_INVALID_NOSHOW_STATE"
        }
        return null
    }
}
