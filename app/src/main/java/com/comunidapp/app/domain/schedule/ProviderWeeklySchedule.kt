package com.comunidapp.app.domain.schedule

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Structured weekly hours. Free-text is never authority.
 * Weekday is ISO: 1 = Monday … 7 = Sunday.
 */
data class WeeklyHoursDay(
    val weekday: Int,
    val closed: Boolean,
    val opensAt: String? = null,
    val closesAt: String? = null,
    val open24Hours: Boolean = false
) {
    val label: String
        get() = ProviderWeeklySchedule.WEEKDAY_LABELS[weekday] ?: "Día $weekday"
}

data class ProviderWeeklySchedule(
    val days: List<WeeklyHoursDay> = emptyList()
) {
    val isEmpty: Boolean get() = days.isEmpty()

    fun day(weekday: Int): WeeklyHoursDay? = days.firstOrNull { it.weekday == weekday }

    fun replace(day: WeeklyHoursDay): ProviderWeeklySchedule {
        val next = days.filterNot { it.weekday == day.weekday } + day
        return copy(days = next.sortedBy { it.weekday })
    }

    companion object {
        val WEEKDAY_LABELS = mapOf(
            1 to "Lunes",
            2 to "Martes",
            3 to "Miércoles",
            4 to "Jueves",
            5 to "Viernes",
            6 to "Sábado",
            7 to "Domingo"
        )

        val ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires")
        private val HM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        fun emptyTemplate(): ProviderWeeklySchedule =
            ProviderWeeklySchedule((1..7).map { WeeklyHoursDay(it, closed = true) })

        fun parseHm(value: String?): LocalTime? {
            val raw = value?.trim().orEmpty()
            if (raw.isBlank()) return null
            return runCatching { LocalTime.parse(raw, HM) }.getOrNull()
        }
    }
}

enum class OpenNowStatus { OPEN, CLOSED, UNKNOWN }

object ProviderScheduleClock {
    fun openNow(
        schedule: ProviderWeeklySchedule,
        now: ZonedDateTime = ZonedDateTime.now(ProviderWeeklySchedule.ARGENTINA)
    ): OpenNowStatus {
        if (schedule.isEmpty) return OpenNowStatus.UNKNOWN
        val iso = now.dayOfWeek.isoWeekday()
        val day = schedule.day(iso) ?: return OpenNowStatus.UNKNOWN
        if (day.closed) return OpenNowStatus.CLOSED
        if (day.open24Hours) return OpenNowStatus.OPEN
        val opens = ProviderWeeklySchedule.parseHm(day.opensAt) ?: return OpenNowStatus.UNKNOWN
        val closes = ProviderWeeklySchedule.parseHm(day.closesAt) ?: return OpenNowStatus.UNKNOWN
        val t = now.toLocalTime()
        return if (!t.isBefore(opens) && t.isBefore(closes)) OpenNowStatus.OPEN else OpenNowStatus.CLOSED
    }
}

fun DayOfWeek.isoWeekday(): Int = value
