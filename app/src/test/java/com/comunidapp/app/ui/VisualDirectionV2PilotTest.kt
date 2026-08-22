package com.comunidapp.app.ui

import androidx.compose.ui.graphics.Color
import com.comunidapp.app.ui.theme.LeoVerVisualPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UX-05 Color Direction V3 is experimental.
 * Background is approved. Accent remains under physical QA.
 * Sage/teal V2 primaries must not return as active UI authority.
 */
class VisualDirectionV2PilotTest {

    @Test
    fun v3ExperimentalHexesMatchApprovedPalette() {
        val v3 = LeoVerVisualPalette.v3Experimental
        assertEquals(Color(0xFFFAFBF8), v3.background)
        assertEquals(Color(0xFFFFFFFF), v3.surface)
        assertEquals(Color(0xFF263238), v3.textPrimary)
        assertEquals(Color(0xFF667085), v3.textSecondary)
        assertEquals(Color(0xFFE5EAE4), v3.borderSoft)
        assertEquals(Color(0xFF49B749), v3.primary)
        assertEquals(Color(0xFF247A3D), v3.primaryDark)
        assertEquals(Color(0xFFEEF8EE), v3.primarySoft)
        assertEquals(Color(0xFF49B749), v3.secondary)
        assertEquals(Color(0xFFEFA066), v3.accent)
        assertEquals(Color(0xFFD96B68), v3.error)
    }

    @Test
    fun sageAndTealAreNotActivePrimaryTokens() {
        val palette = UiRegressionGateTest.sourceFile(
            "app/src/main/java/com/comunidapp/app/ui/theme/VisualDirectionV2.kt"
        ).readText()
        assertFalse(palette.contains("v2Pilot"))
        assertFalse(palette.contains("0xFF74AD7F"))
        assertFalse(palette.contains("0xFF6D9FA1"))
        assertTrue(palette.contains("v3Experimental"))
        assertTrue(palette.contains("VisualDirectionPilot"))
        val theme = UiRegressionGateTest.sourceFile(
            "app/src/main/java/com/comunidapp/app/ui/theme/Theme.kt"
        ).readText()
        assertFalse(theme.contains("0xFF74AD7F"))
        assertFalse(theme.contains("0xFF6D9FA1"))
    }

    @Test
    fun visualPilotWrapsUx05ReferenceScreens() {
        val screens = listOf(
            "app/src/main/java/com/comunidapp/app/ui/screens/home/HomeScreen.kt",
            "app/src/main/java/com/comunidapp/app/ui/screens/comunidad/ComunidadScreen.kt",
            "app/src/main/java/com/comunidapp/app/ui/screens/profile/ProfileScreen.kt",
            "app/src/main/java/com/comunidapp/app/ui/screens/profile/SettingsScreen.kt",
            "app/src/main/java/com/comunidapp/app/ui/screens/onboarding/onb02/Onb02Screens.kt",
            "app/src/main/java/com/comunidapp/app/ui/screens/organization/CreateOrganizationScreen.kt"
        )
        screens.forEach { path ->
            val text = UiRegressionGateTest.sourceFile(path).readText()
            assertTrue("$path must wrap VisualDirectionPilot", text.contains("VisualDirectionPilot"))
            assertFalse("$path must not keep VisualV2Pilot", text.contains("VisualV2Pilot"))
        }
    }

    @Test
    fun profileDoesNotUseGiantGreenHeader() {
        val profile = UiRegressionGateTest.sourceFile(
            "app/src/main/java/com/comunidapp/app/ui/screens/profile/ProfileScreen.kt"
        ).readText()
        assertFalse(profile.contains(".background(ProfileGreen)"))
        assertFalse(profile.contains("BrandGreenDark"))
        assertTrue(profile.contains("Usar LeoVer como"))
        assertFalse(profile.contains("+ Agregar función o perfil"))
    }
}
