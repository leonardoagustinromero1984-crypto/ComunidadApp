package com.comunidapp.app.domain.location

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLocationCompatTest {
    @Test
    fun api28UsesLocationEnabled() {
        assertTrue(DeviceLocationCompat.enabled(sdkInt = 28, api28Enabled = true, legacyMode = 0))
        assertFalse(DeviceLocationCompat.enabled(sdkInt = 33, api28Enabled = false, legacyMode = 3))
    }

    @Test
    fun api26UsesLegacyLocationMode() {
        assertFalse(
            DeviceLocationCompat.enabled(
                sdkInt = 26,
                api28Enabled = true,
                legacyMode = DeviceLocationCompat.LOCATION_MODE_OFF
            )
        )
        assertTrue(DeviceLocationCompat.enabled(sdkInt = 27, api28Enabled = false, legacyMode = 3))
    }
}
