package com.comunidapp.app.domain.user

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PersonAgeTest {

    private val asOf = LocalDate.of(2026, 8, 15)

    @Test
    fun under13Denied() {
        val birth = asOf.minusYears(12)
        val result = PersonAgeRules.validateSignupBirthDate(birth.toString(), asOf)
        assertTrue(result.isFailure)
        assertEquals("UNDER_13_AUTONOMOUS_ACCOUNT_DENIED", result.exceptionOrNull()?.message)
        assertEquals(AgeBand.UNDER_13, PersonAgeRules.band(birth, asOf))
    }

    @Test
    fun teen13RequiresAdultResponsible() {
        val age = PersonAge(asOf.minusYears(14), asOf)
        assertEquals(AgeBand.TEEN_13_15, age.band)
        assertTrue(age.requiresAdultResponsible)
        assertTrue(age.isProtected)
        assertFalse(age.isAdult)
    }

    @Test
    fun teen16ProtectedWithoutRequiredGuardian() {
        val age = PersonAge(asOf.minusYears(16).minusMonths(2), asOf)
        assertEquals(AgeBand.TEEN_16_17, age.band)
        assertFalse(age.requiresAdultResponsible)
        assertTrue(age.isProtected)
    }

    @Test
    fun adult18() {
        val result = PersonAgeRules.validateSignupBirthDate(asOf.minusYears(18).toString(), asOf)
        assertTrue(result.isSuccess)
        assertEquals(AgeBand.ADULT_18_PLUS, result.getOrThrow().band)
    }

    @Test
    fun guardianIsNotOwnerOrInbox() {
        assertFalse(PersonAgeRules.guardianGrantsPetOwnership())
        assertFalse(PersonAgeRules.guardianGrantsMessageAccess())
    }
}
