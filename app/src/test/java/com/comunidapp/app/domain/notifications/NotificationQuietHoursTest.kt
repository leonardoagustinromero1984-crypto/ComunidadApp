package com.comunidapp.app.domain.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class NotificationQuietHoursTest {

    private val zone = ZoneId.of("America/Argentina/Buenos_Aires")

    private val overnight = NotificationQuietHours(
        startLocalTime = LocalTime.of(22, 0),
        endLocalTime = LocalTime.of(7, 0),
        timezone = zone,
        daysOfWeek = DayOfWeek.entries.toSet()
    )

    @Test
    fun crosses_midnight_inside_late_window() {
        // 23:00 ART = 02:00 UTC next day in standard... use ZonedDateTime via Instant
        val at = Instant.parse("2026-07-15T02:00:00Z") // 23:00 ART (UTC-3)
        assertTrue(overnight.crossesMidnight)
        val eval = NotificationQuietHoursRules.evaluate(
            overnight,
            at,
            NotificationSensitivity.PRIVATE,
            categoryAllowsQuietHoursException = false
        )
        assertEquals(QuietHoursDecision.DEFER_UNTIL, eval.decision)
        assertTrue(eval.deferUntil != null)
    }

    @Test
    fun crosses_midnight_inside_early_window() {
        val at = Instant.parse("2026-07-15T08:00:00Z") // 05:00 ART
        val eval = NotificationQuietHoursRules.evaluate(
            overnight,
            at,
            NotificationSensitivity.PRIVATE,
            categoryAllowsQuietHoursException = false
        )
        assertEquals(QuietHoursDecision.DEFER_UNTIL, eval.decision)
    }

    @Test
    fun outside_quiet_hours_allow() {
        val at = Instant.parse("2026-07-15T15:00:00Z") // 12:00 ART
        val eval = NotificationQuietHoursRules.evaluate(
            overnight,
            at,
            NotificationSensitivity.PRIVATE,
            categoryAllowsQuietHoursException = false
        )
        assertEquals(QuietHoursDecision.ALLOW_NOW, eval.decision)
    }

    @Test
    fun security_critical_exception_only_when_explicit() {
        val at = Instant.parse("2026-07-15T02:00:00Z")
        val without = NotificationQuietHoursRules.evaluate(
            overnight,
            at,
            NotificationSensitivity.SECURITY_CRITICAL,
            categoryAllowsQuietHoursException = true,
            explicitCriticalException = false
        )
        assertEquals(QuietHoursDecision.DEFER_UNTIL, without.decision)

        val with = NotificationQuietHoursRules.evaluate(
            overnight,
            at,
            NotificationSensitivity.SECURITY_CRITICAL,
            categoryAllowsQuietHoursException = true,
            explicitCriticalException = true
        )
        assertEquals(QuietHoursDecision.ALLOW_NOW, with.decision)
    }

    @Test
    fun invalid_timezone_fallback_not_silent_send_flag() {
        val (zoneId, ok) = NotificationQuietHoursRules.resolveTimezoneOrFallback("Not/AZone")
        assertEquals(ZoneId.of("UTC"), zoneId)
        assertFalse(ok)
    }
}
