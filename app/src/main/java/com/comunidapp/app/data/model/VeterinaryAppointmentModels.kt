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

    /** Compat Bloque 3 — mapea a REMINDER_24H_DUE en Bloque 4. */
    const val REMINDER_DUE = "VETERINARY_APPOINTMENT_REMINDER_24H_DUE"
    const val REMINDER_24H_DUE = "VETERINARY_APPOINTMENT_REMINDER_24H_DUE"
    const val REMINDER_2H_DUE = "VETERINARY_APPOINTMENT_REMINDER_2H_DUE"
    const val REMINDER_CANCELLED = "VETERINARY_APPOINTMENT_REMINDER_CANCELLED"
    const val REMINDER_INFRASTRUCTURE = "VETERINARY_APPOINTMENT_REMINDER_INFRASTRUCTURE"
    const val COMPLETED = "VETERINARY_APPOINTMENT_COMPLETED"
}

// --- M12 Bloque 4 — recordatorios (sin push real), métricas y timeline ---

enum class VeterinaryReminderType { REMINDER_24H, REMINDER_2H }

enum class VeterinaryReminderDeliveryStatus {
    /** Hook registrado, push NO entregado (Bloque 4 no reclama push real). */
    PREPARED,

    /** Vencimiento disparado local/idempotente, sin reclamo de push. */
    FIRED_PREPARED,
    CANCELLED,
    NOT_ELIGIBLE
}

data class VeterinaryReminderState(
    val appointmentId: String,
    val type: VeterinaryReminderType,
    val dueAt: Instant,
    val status: VeterinaryReminderDeliveryStatus,
    val timezoneName: String,
    /** Siempre false en B4 — no hay push real. */
    val pushClaimed: Boolean = false
)

data class VeterinaryReminderSchedule(
    val appointmentId: String,
    val reminders: List<VeterinaryReminderState>,
    /** false cuando la infraestructura push M06 no está disponible para M12. */
    val infrastructureAvailable: Boolean
)

data class VeterinaryAppointmentOperationalMetrics(
    val clinicId: String,
    val from: Instant,
    val to: Instant,
    val requested: Int,
    val confirmed: Int,
    val rejected: Int,
    val cancelledByUser: Int,
    val cancelledByClinic: Int,
    val completed: Int,
    val noShow: Int,
    val expired: Int,
    /**
     * Ocupación en [0.0, 1.0]:
     * confirmed+completed sobre el total de turnos de la ventana (por startsAt).
     * occupancy = (confirmed+completed) / max(1, requested+confirmed+rejected+
     *   cancelledByUser+cancelledByClinic+completed+noShow+expired)
     */
    val occupancyRate: Double,
    /** null si no hubo confirmaciones en la ventana. */
    val averageConfirmationMinutes: Double?
)

data class VeterinaryAppointmentTimelineStep(
    val status: VeterinaryAppointmentStatus,
    val at: Instant,
    val label: String,
    val reason: String? = null
)

object VeterinaryAppointmentFollowUp {
    fun nextStep(status: VeterinaryAppointmentStatus, isManager: Boolean): String = when (status) {
        VeterinaryAppointmentStatus.REQUESTED ->
            if (isManager) "Confirmá o rechazá la solicitud."
            else "Esperá la confirmación de la clínica."
        VeterinaryAppointmentStatus.CONFIRMED ->
            if (isManager) "Registrá asistencia o no-show cuando corresponda."
            else "Podés cancelar dentro de la ventana permitida."
        else -> "Este turno ya está finalizado."
    }
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
