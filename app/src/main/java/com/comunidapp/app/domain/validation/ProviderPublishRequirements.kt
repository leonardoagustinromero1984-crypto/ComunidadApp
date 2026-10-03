package com.comunidapp.app.domain.validation

import com.comunidapp.app.domain.location.SharedLocationCapture
import com.comunidapp.app.domain.qa.PublicContactPolicy
import com.comunidapp.app.domain.schedule.AppointmentSlotPolicy
import com.comunidapp.app.domain.schedule.ProviderWeeklySchedule
import com.comunidapp.app.domain.schedule.WeeklyHoursBulkApply
import com.comunidapp.app.domain.schedule.WeeklyHoursDay

object ProviderPublishRequirements {

    /**
     * The hours editor shows Lun–Vie 09:00–18:00 before "Aplicar horario" writes the model.
     * A form that still has every day closed is that untouched visual default.
     */
    fun hoursMatchingVisibleDefault(hours: List<WeeklyHoursDay>): List<WeeklyHoursDay> {
        val schedule = ProviderWeeklySchedule.forEditor(hours)
        val hasOpenDay = schedule.days.any { day ->
            !day.closed && (day.open24Hours || !day.opensAt.isNullOrBlank())
        }
        if (hasOpenDay) return schedule.days
        return WeeklyHoursBulkApply.apply(
            schedule,
            WeeklyHoursBulkApply.Range(setOf(1, 2, 3, 4, 5), "09:00", "18:00")
        ).days
    }

    fun locationMatchingPin(location: String, latitude: Double?, longitude: Double?): String {
        val trimmed = location.trim()
        if (trimmed.isNotBlank()) return trimmed
        if (latitude != null && longitude != null && !SharedLocationCapture.isFallback(latitude, longitude)) {
            return "Ubicación marcada"
        }
        return ""
    }

    fun summary(
        name: String,
        location: String,
        phone: String,
        hours: List<WeeklyHoursDay>,
        acceptsBookings: Boolean,
        slotIntervalMinutes: Int?
    ): ValidationSummary {
        val phoneOk = phone.trim().filter { it.isDigit() }.length >= 8
        val hoursOk = hours.any { day ->
            when {
                day.closed -> false
                day.open24Hours -> true
                else -> {
                    val open = ProviderWeeklySchedule.parseHm(day.opensAt)
                    val close = ProviderWeeklySchedule.parseHm(day.closesAt)
                    open != null && close != null && open.isBefore(close)
                }
            }
        }
        val intervalOk = !acceptsBookings ||
            (slotIntervalMinutes != null && AppointmentSlotPolicy.isAllowed(slotIntervalMinutes))
        return MissingRequirements.of(
            (name.isBlank()) to MissingRequirement("name", "Nombre comercial", "name"),
            (location.isBlank()) to MissingRequirement("location", "Localidad", "location"),
            (PublicContactPolicy.PHONE_VISIBLE && !phoneOk) to
                MissingRequirement("phone", "Teléfono", "phone"),
            (!hoursOk) to MissingRequirement("hours", "Horarios", "hours"),
            (acceptsBookings && !intervalOk) to
                MissingRequirement("interval", "Intervalo de turnos", "interval")
        )
    }
}
