package com.comunidapp.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class LeoVerTypographyTest {
    @Test
    fun typographyUsesNunitoSansFamily() {
        assertEquals(NunitoSans, LeoBody.fontFamily)
        assertEquals(NunitoSans, Typography.bodyMedium.fontFamily)
        assertEquals(NunitoSans, LeoButton.fontFamily)
    }
}
