package com.comunidapp.app.domain.notifications

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

enum class QuietHoursDecision {
    ALLOW_NOW,
    DEFER_UNTIL,
    SKIP
}

data class QuietHoursEvaluation(
    val decision: QuietHoursDecision,
    val deferUntil: Instant? = null
)

/**
 * Quiet hours con timezone IANA. Soporta cruce de medianoche.
 * SECURITY_CRITICAL solo exceptúa con política explícita de categoría + flag.
 */
data class NotificationQuietHours(
    val startLocalTime: LocalTime,
    val endLocalTime: LocalTime,
    val timezone: ZoneId,
    val daysOfWeek: Set<DayOfWeek> = DayOfWeek.entries.toSet()
) {
    val crossesMidnight: Boolean
        get() = startLocalTime != endLocalTime && startLocalTime.isAfter(endLocalTime)
}

object NotificationQuietHoursRules {

    fun evaluate(
        quietHours: NotificationQuietHours?,
        at: Instant,
        sensitivity: NotificationSensitivity,
        categoryAllowsQuietHoursException: Boolean,
        explicitCriticalException: Boolean = false
    ): QuietHoursEvaluation {
        if (quietHours == null) {
            return QuietHoursEvaluation(QuietHoursDecision.ALLOW_NOW)
        }
        if (sensitivity == NotificationSensitivity.SECURITY_CRITICAL &&
            categoryAllowsQuietHoursException &&
            explicitCriticalException
        ) {
            return QuietHoursEvaluation(QuietHoursDecision.ALLOW_NOW)
        }
        val zdt = at.atZone(quietHours.timezone)
        if (!isInsideQuietWindow(zdt, quietHours)) {
            return QuietHoursEvaluation(QuietHoursDecision.ALLOW_NOW)
        }
        val deferUntil = nextQuietHoursEnd(zdt, quietHours)
        return QuietHoursEvaluation(QuietHoursDecision.DEFER_UNTIL, deferUntil)
    }

    fun isInsideQuietWindow(zdt: ZonedDateTime, quietHours: NotificationQuietHours): Boolean {
        val local = zdt.toLocalTime()
        val start = quietHours.startLocalTime
        val end = quietHours.endLocalTime
        if (start == end) return false
        return if (start.isBefore(end)) {
            val dayOk = quietHours.daysOfWeek.isEmpty() || zdt.dayOfWeek in quietHours.daysOfWeek
            dayOk && !local.isBefore(start) && local.isBefore(end)
        } else {
            val inLate = !local.isBefore(start) &&
                (quietHours.daysOfWeek.isEmpty() || zdt.dayOfWeek in quietHours.daysOfWeek)
            val inEarly = local.isBefore(end) &&
                (quietHours.daysOfWeek.isEmpty() ||
                    zdt.minusDays(1).dayOfWeek in quietHours.daysOfWeek)
            inLate || inEarly
        }
    }

    fun nextQuietHoursEnd(zdt: ZonedDateTime, quietHours: NotificationQuietHours): Instant {
        val end = quietHours.endLocalTime
        val start = quietHours.startLocalTime
        return if (start.isBefore(end)) {
            zdt.withHour(end.hour).withMinute(end.minute).withSecond(end.second).withNano(0).toInstant()
        } else {
            val local = zdt.toLocalTime()
            val base = if (local.isBefore(end)) zdt else zdt.plusDays(1)
            base.withHour(end.hour).withMinute(end.minute).withSecond(end.second).withNano(0).toInstant()
        }
    }

    fun parseTimezoneOrNull(iana: String?): ZoneId? =
        try {
            if (iana.isNullOrBlank()) null else ZoneId.of(iana.trim())
        } catch (_: Exception) {
            null
        }

    /** Timezone inválida → fallback UTC; caller debe no enviar de inmediato en silencio. */
    fun resolveTimezoneOrFallback(iana: String?): Pair<ZoneId, Boolean> {
        val parsed = parseTimezoneOrNull(iana)
        return if (parsed != null) parsed to true else ZoneId.of("UTC") to false
    }
}
