package com.comunidapp.app.domain.pets

import com.comunidapp.app.ui.components.toDisplayName
import com.comunidapp.app.data.model.SterilizationStatus
import com.comunidapp.app.ui.util.isoDateFromMillis
import com.comunidapp.app.ui.util.millisFromIsoDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class PetFormRulesTest {

    @Test
    fun years_0_to_99_pass_and_100_fails() {
        assertTrue(PetAgeRules.isValidYears(0))
        assertTrue(PetAgeRules.isValidYears(1))
        assertTrue(PetAgeRules.isValidYears(9))
        assertTrue(PetAgeRules.isValidYears(10))
        assertTrue(PetAgeRules.isValidYears(99))
        assertFalse(PetAgeRules.isValidYears(100))
        assertEquals(10, PetAgeRules.parseYears("10"))
        assertEquals(99, PetAgeRules.parseYears("99"))
        assertEquals(null, PetAgeRules.parseYears("100"))
    }

    @Test
    fun months_0_to_11_pass_and_12_fails() {
        (0..11).forEach { assertTrue(PetAgeRules.isValidMonths(it)) }
        assertFalse(PetAgeRules.isValidMonths(12))
        assertEquals(10, PetAgeRules.parseMonths("10"))
        assertEquals(11, PetAgeRules.parseMonths("11"))
        assertEquals(null, PetAgeRules.parseMonths("12"))
    }

    @Test
    fun historicalFutureDateFails() {
        val today = LocalDate.of(2026, 8, 14)
        assertTrue(HistoricalDateRules.isNotFuture("2026-08-14", today))
        assertTrue(HistoricalDateRules.isNotFuture("2026-08-06", today))
        assertFalse(HistoricalDateRules.isNotFuture("2026-08-15", today))
    }

    @Test
    fun unknownDisplayIsDesconocido() {
        assertEquals("Desconocido", SterilizationStatus.UNKNOWN.toDisplayName())
        assertEquals("Castrado", SterilizationStatus.YES.toDisplayName())
        assertEquals("No castrado", SterilizationStatus.NO.toDisplayName())
    }

    @Test
    fun selectedCalendarDatePersistsSameDay() {
        val millis = LocalDate.of(2026, 8, 6)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
        assertEquals("2026-08-06", isoDateFromMillis(millis))
        assertEquals(millis, millisFromIsoDate("2026-08-06"))
    }

    @Test
    fun vaccineNextBoosterAddsOneYear() {
        assertEquals("2027-08-06", PetHealthSchedule.nextVaccineBooster("2026-08-06"))
    }

    @Test
    fun dewormingAddsFourMonths() {
        assertEquals("2026-12-06", PetHealthSchedule.nextDeworming("2026-08-06"))
    }

    @Test
    fun fleaAddsThirtyDays() {
        assertEquals("2026-09-05", PetHealthSchedule.nextFleaApplication("2026-08-06"))
    }

    @Test
    fun leapYearUsesLocalDateSemantics() {
        assertEquals("2025-02-28", PetHealthSchedule.nextVaccineBooster("2024-02-29"))
        assertEquals("2024-06-29", PetHealthSchedule.nextDeworming("2024-02-29"))
        assertEquals("2024-03-30", PetHealthSchedule.nextFleaApplication("2024-02-29"))
    }
}
