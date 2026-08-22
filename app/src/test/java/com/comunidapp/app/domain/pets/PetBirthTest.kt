package com.comunidapp.app.domain.pets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class PetBirthTest {

    private val asOf = LocalDate.of(2026, 8, 15)

    @Test
    fun unknownDoesNotInventDate() {
        assertEquals("Edad desconocida", PetBirth.UNKNOWN.display(asOf))
        assertNull(PetBirth.UNKNOWN.approximateYearsMonths(asOf))
    }

    @Test
    fun exactDateFormatsYearsAndMonths() {
        val birth = PetBirth.exact(LocalDate.of(2021, 4, 15))
        assertEquals("5 años y 4 meses", birth.display(asOf))
    }

    @Test
    fun estimatedKeepsApproxPrefix() {
        val birth = PetBirth.estimatedFromDisplayAge(2, 0, asOf)
        assertEquals("Aprox. 2 años", birth.display(asOf))
    }

    @Test
    fun yearPrecisionDoesNotInventMonth() {
        val birth = PetBirth(
            precision = PetBirthPrecision.YEAR_PRECISION,
            birthYear = 2021
        )
        assertEquals("5 años", birth.display(asOf))
    }
}
