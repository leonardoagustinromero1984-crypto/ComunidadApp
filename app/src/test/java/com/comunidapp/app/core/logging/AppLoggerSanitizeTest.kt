package com.comunidapp.app.core.logging

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLoggerSanitizeTest {

    @Test
    fun sanitize_redactsJwtAndBearer() {
        val raw = "token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.aaa.bbb Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.ccc.ddd"
        val clean = sanitizeLogMessage(raw)
        assertFalse(clean.contains("eyJhbGci"))
        assertTrue(clean.contains("REDACTED"))
    }

    @Test
    fun sanitize_redactsEmailAndPasswordAssignment() {
        val raw = "user maria@email.com password=secreto123"
        val clean = sanitizeLogMessage(raw)
        assertFalse(clean.contains("maria@email.com"))
        assertFalse(clean.contains("secreto123"))
        assertTrue(clean.contains("REDACTED_EMAIL"))
        assertTrue(clean.contains("REDACTED"))
    }

    @Test
    fun sanitize_redactsCoordinates() {
        val raw = "punto -34.603722, -58.381592"
        val clean = sanitizeLogMessage(raw)
        assertFalse(clean.contains("-34.603722"))
        assertTrue(clean.contains("REDACTED_COORDS"))
    }
}
