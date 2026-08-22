package com.comunidapp.app.domain.pets

import com.comunidapp.app.data.model.PetReminder
import java.time.LocalDate

object PetAgeRules {
    const val YEARS_MIN = 0
    const val YEARS_MAX = 99
    const val MONTHS_MIN = 0
    const val MONTHS_MAX = 11

    fun parseYears(raw: String): Int? {
        val digits = raw.filter { it.isDigit() }
        if (digits.isEmpty()) return 0
        if (digits.length > 2) return null
        val value = digits.toInt()
        return value.takeIf { it in YEARS_MIN..YEARS_MAX }
    }

    fun parseMonths(raw: String): Int? {
        val digits = raw.filter { it.isDigit() }
        if (digits.isEmpty()) return 0
        if (digits.length > 2) return null
        val value = digits.toInt()
        return value.takeIf { it in MONTHS_MIN..MONTHS_MAX }
    }

    fun isValidYears(value: Int): Boolean = value in YEARS_MIN..YEARS_MAX
    fun isValidMonths(value: Int): Boolean = value in MONTHS_MIN..MONTHS_MAX
}

object PetHealthSchedule {
    fun nextVaccineBooster(applicationIso: String): String =
        LocalDate.parse(applicationIso).plusYears(1).toString()

    fun nextDeworming(performedIso: String): String =
        LocalDate.parse(performedIso).plusMonths(4).toString()

    fun nextFleaApplication(appliedIso: String): String =
        LocalDate.parse(appliedIso).plusDays(30).toString()
}

object HistoricalDateRules {
    fun isNotFuture(isoDate: String, today: LocalDate = LocalDate.now()): Boolean {
        if (isoDate.isBlank()) return true
        return runCatching { !LocalDate.parse(isoDate).isAfter(today) }.getOrDefault(false)
    }
}

object PetHealthReminders {
    const val NEXT_DEWORMING = "NEXT_DEWORMING"
    const val NEXT_FLEA = "NEXT_FLEA"

    fun dateOf(reminders: List<PetReminder>, type: String): String =
        reminders.firstOrNull { it.type.equals(type, ignoreCase = true) }?.date.orEmpty()

    fun upsert(
        reminders: List<PetReminder>,
        type: String,
        date: String,
        title: String
    ): List<PetReminder> {
        val without = reminders.filterNot { it.type.equals(type, ignoreCase = true) }
        if (date.isBlank()) return without
        val existing = reminders.firstOrNull { it.type.equals(type, ignoreCase = true) }
        return without + PetReminder(
            id = existing?.id?.takeIf { it.isNotBlank() } ?: type.lowercase(),
            title = title,
            date = date,
            type = type
        )
    }
}
