package com.comunidapp.app.domain.auth.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailOtpValidatorsTest {

    @Test
    fun five_digits_invalid() {
        assertFalse(EmailOtpValidators.isValid("12345"))
        assertTrue(EmailOtpValidators.validate("12345").isFailure)
    }

    @Test
    fun six_digits_accepted_for_compatibility() {
        assertTrue(EmailOtpValidators.isValid("123456"))
        assertEquals("123456", EmailOtpValidators.validate("123456").getOrNull())
    }

    @Test
    fun eight_digits_accepted() {
        assertTrue(EmailOtpValidators.isValid("12345678"))
        assertEquals("12345678", EmailOtpValidators.validate("12345678").getOrNull())
    }

    @Test
    fun ten_digits_accepted() {
        assertTrue(EmailOtpValidators.isValid("1234567890"))
        assertEquals("1234567890", EmailOtpValidators.validate("1234567890").getOrNull())
    }

    @Test
    fun eleven_digits_blocked() {
        assertFalse(EmailOtpValidators.isValid("12345678901"))
        assertTrue(EmailOtpValidators.validate("12345678901").isFailure)
        assertEquals("1234567890", EmailOtpValidators.sanitizeInput("12345678901"))
    }

    @Test
    fun letters_rejected() {
        assertFalse(EmailOtpValidators.isValid("12ab56"))
        assertTrue(EmailOtpValidators.validate("12ab56").isFailure)
        assertEquals("1256", EmailOtpValidators.sanitizeInput("12ab56"))
    }

    @Test
    fun external_spaces_trimmed_before_verify() {
        assertEquals("12345678", EmailOtpValidators.normalize("  12345678  "))
        assertEquals("12345678", EmailOtpValidators.validate("  12345678  ").getOrNull())
    }

    @Test
    fun paste_eight_digits_does_not_truncate() {
        assertEquals("87654321", EmailOtpValidators.sanitizeInput("87654321"))
        assertEquals(8, EmailOtpValidators.sanitizeInput("87654321").length)
    }

    @Test
    fun prompt_message_does_not_hardcode_six() {
        assertFalse(EmailOtpValidators.PROMPT_MESSAGE.contains("6"))
        assertTrue(EmailOtpValidators.PROMPT_MESSAGE.contains("correo"))
    }
}
