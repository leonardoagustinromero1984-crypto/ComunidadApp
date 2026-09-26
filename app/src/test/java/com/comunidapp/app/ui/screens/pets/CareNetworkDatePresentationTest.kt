package com.comunidapp.app.ui.screens.pets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CareNetworkDatePresentationTest {

    @Test
    fun epochZeroDoesNotShowActivoDesde() {
        assertNull(formatEpochDate(0L))
        assertNull(validSinceLabel(0L, null))
    }

    @Test
    fun validDateIsShown() {
        val epoch = java.time.Instant.parse("2024-08-24T00:00:00Z").toEpochMilli()
        assertEquals("2024-08-24", formatEpochDate(epoch))
        assertEquals("Desde: 2024-08-24", validSinceLabel(epoch, null))
    }
}
