package com.comunidapp.app.domain.user

import java.time.LocalDate
import java.time.Period

/**
 * Canonical person age. Derived from birth_date. Never persist age_years as SoT.
 * Guardian is not pet owner and does not get automatic message access.
 */
enum class AgeBand {
    UNDER_13,
    TEEN_13_15,
    TEEN_16_17,
    ADULT_18_PLUS
}

enum class AgeAssurance {
    SELF_DECLARED,
    ACCOUNT_CONFIRMED,
    DOCUMENT_VERIFIED
}

enum class PersonProtectionState {
    NONE,
    PROTECTED,
    LOCKED
}

data class PersonAge(
    val birthDate: LocalDate,
    val asOf: LocalDate = LocalDate.now(),
    val assurance: AgeAssurance = AgeAssurance.SELF_DECLARED
) {
    val years: Int get() = Period.between(birthDate, asOf).years
    val band: AgeBand get() = PersonAgeRules.band(birthDate, asOf)
    val autonomousAccountAllowed: Boolean get() = band != AgeBand.UNDER_13
    val requiresAdultResponsible: Boolean get() = band == AgeBand.TEEN_13_15
    val isProtected: Boolean get() = band == AgeBand.TEEN_13_15 || band == AgeBand.TEEN_16_17
    val isAdult: Boolean get() = band == AgeBand.ADULT_18_PLUS
    val protectionState: PersonProtectionState
        get() = when (band) {
            AgeBand.UNDER_13 -> PersonProtectionState.LOCKED
            AgeBand.TEEN_13_15, AgeBand.TEEN_16_17 -> PersonProtectionState.PROTECTED
            AgeBand.ADULT_18_PLUS -> PersonProtectionState.NONE
        }
}

object PersonAgeRules {
    fun band(birthDate: LocalDate, asOf: LocalDate = LocalDate.now()): AgeBand {
        val years = Period.between(birthDate, asOf).years
        return when {
            years < 13 -> AgeBand.UNDER_13
            years < 16 -> AgeBand.TEEN_13_15
            years < 18 -> AgeBand.TEEN_16_17
            else -> AgeBand.ADULT_18_PLUS
        }
    }

    fun parseIso(isoDate: String): Result<LocalDate> = runCatching {
        LocalDate.parse(isoDate.trim())
    }.recoverCatching {
        error("BIRTH_DATE_INVALID")
    }

    fun validateSignupBirthDate(isoDate: String, asOf: LocalDate = LocalDate.now()): Result<PersonAge> {
        val date = parseIso(isoDate).getOrElse { return Result.failure(it) }
        if (date.isAfter(asOf)) {
            return Result.failure(IllegalArgumentException("BIRTH_DATE_IN_FUTURE"))
        }
        val age = PersonAge(date, asOf)
        if (!age.autonomousAccountAllowed) {
            return Result.failure(IllegalArgumentException("UNDER_13_AUTONOMOUS_ACCOUNT_DENIED"))
        }
        return Result.success(age)
    }

    /** Guardian relationship does not grant pet ownership or message read. */
    fun guardianGrantsPetOwnership(): Boolean = false

    fun guardianGrantsMessageAccess(): Boolean = false
}
