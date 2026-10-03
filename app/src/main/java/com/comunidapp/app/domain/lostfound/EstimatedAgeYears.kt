package com.comunidapp.app.domain.lostfound

/**
 * Found-pet estimated age is entered and shown in years.
 * Persistence stays in months (`estimated_age_months = years * 12`) so historical
 * rows remain valid. Sub-year historical values display as "menos de 1 año"
 * instead of being rewritten.
 */
object EstimatedAgeYears {
    fun yearsToStoredMonths(years: Int?): Int? {
        val value = years ?: return null
        if (value < 0) return null
        return value * 12
    }

    fun displayLabel(storedMonths: Int?): String? {
        val months = storedMonths ?: return null
        if (months < 0) return null
        val years = months / 12
        return when {
            years <= 0 -> "Edad estimada: menos de 1 año"
            years == 1 -> "Edad estimada: 1 año"
            else -> "Edad estimada: $years años"
        }
    }
}
