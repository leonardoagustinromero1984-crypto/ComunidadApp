package com.comunidapp.app.domain.pets

import java.time.LocalDate
import java.time.Period
import java.time.YearMonth

enum class PetBirthPrecision {
    EXACT_DATE,
    MONTH_PRECISION,
    YEAR_PRECISION,
    ESTIMATED,
    UNKNOWN
}

data class PetBirth(
    val precision: PetBirthPrecision = PetBirthPrecision.UNKNOWN,
    val birthDate: LocalDate? = null,
    val birthYear: Int? = null,
    val birthMonth: Int? = null,
    val estimatedAgeMonths: Int? = null,
    val estimatedAsOf: LocalDate? = null
) {
    fun isValid(): Boolean = when (precision) {
        PetBirthPrecision.EXACT_DATE -> birthDate != null
        PetBirthPrecision.MONTH_PRECISION -> birthYear != null && birthMonth in 1..12
        PetBirthPrecision.YEAR_PRECISION -> birthYear != null
        PetBirthPrecision.ESTIMATED -> estimatedAgeMonths != null && estimatedAgeMonths >= 0 && estimatedAsOf != null
        PetBirthPrecision.UNKNOWN -> true
    }

    fun display(asOf: LocalDate = LocalDate.now()): String = PetBirthDisplay.format(this, asOf)

    fun approximateYearsMonths(asOf: LocalDate = LocalDate.now()): Pair<Int, Int>? {
        val period = period(asOf) ?: return null
        return period.years to period.months
    }

    fun period(asOf: LocalDate = LocalDate.now()): Period? = when (precision) {
        PetBirthPrecision.EXACT_DATE -> birthDate?.let { Period.between(it, asOf) }
        PetBirthPrecision.MONTH_PRECISION -> {
            val year = birthYear ?: return null
            val month = birthMonth ?: return null
            Period.between(YearMonth.of(year, month).atDay(1), asOf)
        }
        PetBirthPrecision.YEAR_PRECISION -> birthYear?.let {
            Period.between(LocalDate.of(it, 1, 1), asOf)
        }
        PetBirthPrecision.ESTIMATED -> {
            val months = estimatedAgeMonths ?: return null
            val origin = estimatedAsOf ?: return null
            val extra = Period.between(origin, asOf)
            val totalMonths = months + extra.years * 12 + extra.months
            Period.of(totalMonths / 12, totalMonths % 12, 0)
        }
        PetBirthPrecision.UNKNOWN -> null
    }

    companion object {
        val UNKNOWN = PetBirth()

        fun estimatedFromDisplayAge(years: Int, months: Int, asOf: LocalDate = LocalDate.now()): PetBirth {
            val total = (years.coerceAtLeast(0) * 12) + months.coerceIn(0, 11)
            if (total <= 0 && years == 0 && months == 0) {
                return UNKNOWN
            }
            return PetBirth(
                precision = PetBirthPrecision.ESTIMATED,
                estimatedAgeMonths = total,
                estimatedAsOf = asOf
            )
        }

        fun exact(date: LocalDate): PetBirth =
            PetBirth(precision = PetBirthPrecision.EXACT_DATE, birthDate = date)
    }
}

object PetBirthDisplay {
    fun format(birth: PetBirth, asOf: LocalDate = LocalDate.now()): String {
        return when (birth.precision) {
            PetBirthPrecision.UNKNOWN -> "Edad desconocida"
            PetBirthPrecision.ESTIMATED -> {
                val pair = birth.approximateYearsMonths(asOf)
                if (pair == null) "Edad desconocida"
                else "Aprox. " + yearsMonths(pair.first, pair.second)
            }
            PetBirthPrecision.YEAR_PRECISION -> {
                val years = birth.approximateYearsMonths(asOf)?.first ?: return "Edad desconocida"
                yearsLabel(years)
            }
            PetBirthPrecision.MONTH_PRECISION, PetBirthPrecision.EXACT_DATE -> {
                val pair = birth.approximateYearsMonths(asOf) ?: return "Edad desconocida"
                yearsMonths(pair.first, pair.second)
            }
        }
    }

    private fun yearsMonths(years: Int, months: Int): String = when {
        years <= 0 && months <= 0 -> "Menos de 1 mes"
        years <= 0 -> if (months == 1) "1 mes" else "$months meses"
        months <= 0 -> yearsLabel(years)
        else -> "${yearsLabel(years)} y ${if (months == 1) "1 mes" else "$months meses"}"
    }

    private fun yearsLabel(years: Int): String =
        if (years == 1) "1 año" else "$years años"
}
