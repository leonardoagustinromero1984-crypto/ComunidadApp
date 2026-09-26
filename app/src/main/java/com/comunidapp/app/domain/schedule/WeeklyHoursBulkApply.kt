package com.comunidapp.app.domain.schedule

object WeeklyHoursBulkApply {
    data class Range(
        val weekdays: Set<Int>,
        val opensAt: String,
        val closesAt: String
    )

    fun apply(existing: ProviderWeeklySchedule, range: Range): ProviderWeeklySchedule {
        val open = ProviderWeeklySchedule.parseHm(range.opensAt)
        val close = ProviderWeeklySchedule.parseHm(range.closesAt)
        if (open == null || close == null || !open.isBefore(close) || range.weekdays.isEmpty()) {
            return existing
        }
        var next = existing
        range.weekdays.forEach { day ->
            val current = next.day(day) ?: WeeklyHoursDay(day, closed = true)
            next = next.replace(
                current.copy(
                    closed = false,
                    open24Hours = false,
                    opensAt = range.opensAt,
                    closesAt = range.closesAt
                )
            )
        }
        return next
    }

    fun daysWithoutRule(schedule: ProviderWeeklySchedule): Set<Int> =
        (1..7).filter { weekday ->
            val day = schedule.day(weekday)
            day == null || day.closed || (day.opensAt.isNullOrBlank() && !day.open24Hours)
        }.toSet()
}
