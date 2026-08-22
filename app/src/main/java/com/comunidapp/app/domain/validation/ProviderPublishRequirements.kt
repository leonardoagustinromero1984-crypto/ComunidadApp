package com.comunidapp.app.domain.validation

import com.comunidapp.app.domain.schedule.ProviderWeeklySchedule
import com.comunidapp.app.domain.schedule.WeeklyHoursDay
import com.comunidapp.app.domain.qa.PublicContactPolicy
import com.comunidapp.app.domain.schedule.AppointmentSlotPolicy

object ProviderPublishRequirements {

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
