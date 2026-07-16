package com.comunidapp.app.viewmodel.moderation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LogoutAdministrativeStateTest {

    @Before
    fun setUp() {
        AdministrativeSessionCleanup.resetForTests()
    }

    @Test
    fun clear_increments_generation() {
        val before = AdministrativeSessionCleanup.clearedGenerationForTests()
        AdministrativeSessionCleanup.clear()
        assertEquals(before + 1, AdministrativeSessionCleanup.clearedGenerationForTests())
        AdministrativeSessionCleanup.clear()
        assertTrue(AdministrativeSessionCleanup.clearedGenerationForTests() > before)
    }
}
