package com.comunidapp.app.domain.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderWeeklyScheduleDaysTest {

    @Test
    fun startShowsSevenDays() {
        assertEquals(7, ProviderWeeklySchedule.emptyTemplate().days.size)
        assertEquals(
            listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"),
            ProviderWeeklySchedule.emptyTemplate().days.map { it.label }
        )
    }

    @Test
    fun selectingMondayKeepsSevenDays() {
        val next = ProviderWeeklySchedule.emptyTemplate().replace(
            WeeklyHoursDay(weekday = 1, closed = false, opensAt = "09:00", closesAt = "18:00")
        )
        assertEquals(7, next.days.size)
        assertFalse(next.day(1)!!.closed)
        (2..7).forEach { assertTrue(next.day(it)!!.closed) }
    }

    @Test
    fun selectingMondayThenWednesdayKeepsSevenDaysAndBothSelected() {
        val monday = ProviderWeeklySchedule.emptyTemplate().replace(
            WeeklyHoursDay(weekday = 1, closed = false, opensAt = "09:00", closesAt = "18:00")
        )
        val both = monday.replace(
            WeeklyHoursDay(weekday = 3, closed = false, opensAt = "09:00", closesAt = "18:00")
        )
        assertEquals(7, both.days.size)
        assertFalse(both.day(1)!!.closed)
        assertFalse(both.day(3)!!.closed)
        listOf(2, 4, 5, 6, 7).forEach { assertTrue(both.day(it)!!.closed) }
    }

    @Test
    fun replaceOnEmptyScheduleStillShowsSevenDays() {
        val next = ProviderWeeklySchedule().replace(
            WeeklyHoursDay(weekday = 1, closed = false, opensAt = "09:00", closesAt = "18:00")
        )
        assertEquals(7, next.days.size)
    }

    @Test
    fun editorStateFromSingleSelectedDayStillRendersSeven() {
        val stored = listOf(
            WeeklyHoursDay(weekday = 1, closed = false, opensAt = "09:00", closesAt = "18:00")
        )
        val initial = ProviderWeeklySchedule.forEditor(stored)
        assertEquals(7, initial.visibleDays().size)
        val afterMonday = initial.replace(
            WeeklyHoursDay(weekday = 1, closed = false, opensAt = "08:00", closesAt = "17:00")
        )
        assertEquals(7, afterMonday.visibleDays().size)
        val afterWednesday = afterMonday.replace(
            WeeklyHoursDay(weekday = 3, closed = false, opensAt = "10:00", closesAt = "16:00")
        )
        assertEquals(7, afterWednesday.visibleDays().size)
        assertEquals(7, afterWednesday.days.size)
        assertFalse(afterWednesday.day(1)!!.closed)
        assertFalse(afterWednesday.day(3)!!.closed)
    }
}
