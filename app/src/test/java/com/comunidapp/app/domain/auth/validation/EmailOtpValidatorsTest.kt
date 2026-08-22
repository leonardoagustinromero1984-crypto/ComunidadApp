package com.comunidapp.app.domain.auth.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailOtpValidatorsTest {

    @Test
    fun five_digits_invalid_and_not_submittable() {
        assertFalse(EmailOtpValidators.isValid("12345"))
        assertTrue(EmailOtpValidators.validate("12345").isFailure)
    }

    @Test
    fun six_digits_accepted() {
        assertTrue(EmailOtpValidators.isValid("123456"))
        assertEquals("123456", EmailOtpValidators.validate("123456").getOrNull())
    }

    @Test
    fun eight_digits_accepted_and_not_truncated() {
        assertTrue(EmailOtpValidators.isValid("12345678"))
        assertEquals("12345678", EmailOtpValidators.validate("12345678").getOrNull())
        assertEquals("12345678", EmailOtpValidators.sanitizeInput("12345678"))
    }

    @Test
    fun ten_digits_accepted() {
        assertTrue(EmailOtpValidators.isValid("1234567890"))
        assertEquals("1234567890", EmailOtpValidators.validate("1234567890").getOrNull())
    }

    @Test
    fun more_than_ten_digits_rejected_and_not_truncated_to_valid() {
        assertEquals("12345678901", EmailOtpValidators.sanitizeInput("12345678901"))
        assertFalse(EmailOtpValidators.isValid("12345678901"))
        assertTrue(EmailOtpValidators.validate("12345678901").isFailure)
    }

    @Test
    fun paste_keeps_eight_digits_and_strips_separators() {
        assertEquals("12345678", EmailOtpValidators.sanitizeInput("12 34-5678"))
        assertEquals("12345678", EmailOtpValidators.validate("12 34-5678").getOrNull())
    }

    @Test
    fun letters_stripped_then_validated() {
        assertEquals("1256", EmailOtpValidators.sanitizeInput("12ab56"))
        assertTrue(EmailOtpValidators.validate("12ab56").isFailure)
        assertFalse(EmailOtpValidators.isValid(EmailOtpValidators.sanitizeInput("abcdef")))
    }

    @Test
    fun spaces_trimmed_before_verify() {
        assertEquals("123456", EmailOtpValidators.validate("  123456  ").getOrNull())
        assertEquals("87654321", EmailOtpValidators.validate("  87654321  ").getOrNull())
    }

    @Test
    fun prompt_does_not_hardcode_six_digits() {
        assertFalse(EmailOtpValidators.PROMPT_MESSAGE.contains("6 dígitos"))
        assertTrue(EmailOtpValidators.PROMPT_MESSAGE.contains("correo"))
        assertEquals(6, EmailOtpValidators.MIN_LENGTH)
        assertEquals(10, EmailOtpValidators.MAX_LENGTH)
    }
}
