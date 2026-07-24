package com.comunidapp.app.data.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * LeoVer M12 Bloque 3 — agenda, disponibilidad y turnos (sin cobro ni historia clínica).
 */

enum class VeterinaryAvailabilityExceptionType {
    CLOSED, CUSTOM_HOURS, CAPACITY_OVERRIDE, UNKNOWN;

    companion object {
        fun fromString(value: String?): VeterinaryAvailabilityExceptionType =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

enum class VeterinaryAppointmentStatus {
    REQUESTED,
    CONFIRMED,
    REJECTED,
    CANCELLED_BY_USER,
    CANCELLED_BY_CLINIC,
    COMPLETED,
    NO_SHOW,
    EXPIRED,
    UNKNOWN;

    companion object {
        fun fromString(value: String?): VeterinaryAppointmentStatus =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }

    val consumesCapacity: Boolean
        get() = this == REQUESTED || this == CONFIRMED

    val isFinal: Boolean
        get() = this == COMPLETED || this == NO_SHOW || this == REJECTED ||
            this == CANCELLED_BY_USER || this == CANCELLED_BY_CLINIC || this == EXPIRED
}

data class VeterinaryScheduleSettings(
    val clinicId: String,
    val timezoneName: String = "America/Argentina/Buenos_Aires",
    val bookingHorizonDays: Int = 30,
    val minimumNoticeMinutes: Int = 60,
    val cancellationNoticeMinutes: Int = 120,
    val defaultSlotDurationMinutes: Int = 30,
    val active: Boolean = true
)

data class VeterinaryAvailabilityRule(
    val id: String,
    val clinicId: String,
    val professionalId: String? = null,
    val serviceId: String? = null,
    val dayOfWeek: DayOfWeek,
    val startsAt: LocalTime,
    val endsAt: LocalTime,
    val slotDurationMinutes: Int,
    val capacityPerSlot: Int,
    val validFrom: LocalDate? = null,
    val validUntil: LocalDate? = null,
    val active: Boolean = true
)

data class VeterinaryAvailabilityException(
    val id: String,
    val clinicId: String,
    val ruleId: String? = null,
    val exceptionDate: LocalDate,
    val type: VeterinaryAvailabilityExceptionType,
    val startsAt: LocalTime? = null,
    val endsAt: LocalTime? = null,
    val capacityPerSlot: Int? = null,
    val reason: String? = null,
    val active: Boolean = true
)

/** Proyección calculada — no tabla de slots pre-generados. */
data class VeterinaryAppointmentSlot(
    val clinicId: String,
    val professionalId: String? = null,
    val serviceId: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val capacity: Int,
    val reserved: Int,
    val available: Int
)

data class VeterinaryAppointment(
    val id: String,
    val clinicId: String,
    val professionalId: String? = null,
    val serviceId: String,
    val petId: String,
    val requesterUserId: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val status: VeterinaryAppointmentStatus,
    val requestNote: String? = null,
    val clinicOperationalNote: String? = null,
    val rejectionReason: String? = null,
    val cancellationReason: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class VeterinaryAppointmentStatusHistory(
    val id: String,
    val appointmentId: String,
    val fromStatus: VeterinaryAppointmentStatus? = null,
    val toStatus: VeterinaryAppointmentStatus,
    val changedBy: String,
    val reason: String? = null,
    val changedAt: Instant
)

object VeterinaryAppointmentAuditEvents {
    const val REQUESTED = "VETERINARY_APPOINTMENT_REQUESTED"
    const val CONFIRMED = "VETERINARY_APPOINTMENT_CONFIRMED"
    const val REJECTED = "VETERINARY_APPOINTMENT_REJECTED"
    const val CANCELLED = "VETERINARY_APPOINTMENT_CANCELLED"
    const val REMINDER_DUE = "VETERINARY_APPOINTMENT_REMINDER_DUE"
    const val COMPLETED = "VETERINARY_APPOINTMENT_COMPLETED"
    const val NO_SHOW = "VETERINARY_APPOINTMENT_NO_SHOW"
    const val EXPIRED = "VETERINARY_APPOINTMENT_EXPIRED"
}

object VeterinaryAppointmentM06Hooks {
    const val REQUESTED = "VETERINARY_APPOINTMENT_REQUESTED"
    const val CONFIRMED = "VETERINARY_APPOINTMENT_CONFIRMED"
    const val REJECTED = "VETERINARY_APPOINTMENT_REJECTED"
    const val CANCELLED = "VETERINARY_APPOINTMENT_CANCELLED"
    const val REMINDER_DUE = "VETERINARY_APPOINTMENT_REMINDER_DUE"
    const val COMPLETED = "VETERINARY_APPOINTMENT_COMPLETED"
}

object VeterinarySchedulePermissionCodes {
    const val SCHEDULE_READ = "veterinary.schedule.read"
    const val SCHEDULE_MANAGE = "veterinary.schedule.manage"
    const val APPOINTMENT_READ = "veterinary.appointment.read"
    const val APPOINTMENT_REQUEST = "veterinary.appointment.request"
    const val APPOINTMENT_MANAGE = "veterinary.appointment.manage"

    val all = setOf(
        SCHEDULE_READ, SCHEDULE_MANAGE,
        APPOINTMENT_READ, APPOINTMENT_REQUEST, APPOINTMENT_MANAGE
    )
}
