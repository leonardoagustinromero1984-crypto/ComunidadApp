package com.comunidapp.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.comunidapp.app.domain.context.ContextNavigation
import com.comunidapp.app.domain.context.OperationalContext
import com.comunidapp.app.navigation.NavRoutes
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.BrandCream
import com.comunidapp.app.ui.theme.BrandGreen
import com.comunidapp.app.ui.theme.BrandOrange
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.BrandWhite
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.LeoVerTheme
import com.comunidapp.app.ui.theme.ProfileGreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LeoVerDesignSystemContractTest {

    @Test
    fun canonicalColorTokensMatchUi01() {
        assertEquals(Color(0xFFFAFBF8), BrandBackground)
        assertEquals(Color(0xFFFFFFFF), BrandWhite)
        assertEquals(Color(0xFFFF7A00), BrandOrange)
        assertEquals(Color(0xFF49B749), BrandGreen)
        assertEquals(Color(0xFF66B978), ProfileGreen)
        assertEquals(Color(0xFFFFF8E1), BrandCream)
        assertEquals(Color(0xFF263238), BrandText)
        assertEquals(LeoVerTheme.colors.background, BrandBackground)
        assertEquals(LeoVerTheme.colors.surface, BrandWhite)
        assertEquals(LeoVerTheme.colors.profileGreen, ProfileGreen)
        assertNotEquals(BrandGreen, ProfileGreen)
    }

    @Test
    fun spacingAndShapeScale() {
        assertEquals(4.dp, LeoDimens.SpaceXs)
        assertEquals(8.dp, LeoDimens.SpaceS)
        assertEquals(12.dp, LeoDimens.SpaceM)
        assertEquals(16.dp, LeoDimens.SpaceL)
        assertEquals(24.dp, LeoDimens.SpaceXl)
        assertEquals(32.dp, LeoDimens.SpaceXxl)
        assertEquals(12.dp, LeoVerTheme.shapes.small)
        assertEquals(16.dp, LeoVerTheme.shapes.card)
        assertEquals(22.dp, LeoVerTheme.shapes.large)
        assertEquals(LeoDimens.RadiusLarge, LeoDimens.RadiusCardFeature)
    }

    @Test
    fun colorKtSourceUsesTokensNotOldTextHex() {
        val colors = UiRegressionGateTest.sourceFile(
            "app/src/main/java/com/comunidapp/app/ui/theme/Color.kt"
        ).readText()
        assertTrue(colors.contains("val BrandText = Color(0xFF263238)"))
        assertTrue(colors.contains("val ProfileGreen = Color(0xFF66B978)"))
        assertTrue(colors.contains("val BrandBackground = Color(0xFFFAFBF8)"))
    }

    @Test
    fun contextualNavSharesPersonalChromePrinciples() {
        val personal = ContextNavigation.itemsFor(OperationalContext.Personal)
        val refuge = ContextNavigation.itemsFor(
            OperationalContext.Organization("org-1", "Refugio · Huellas", "SHELTER")
        )
        val foster = ContextNavigation.itemsFor(
            OperationalContext.Foster("foster-1", "Hogar")
        )
        assertEquals(5, personal.size)
        assertEquals(5, refuge.size)
        assertEquals(5, foster.size)
        assertEquals("Inicio", personal.first().label)
        assertEquals("Perfil", personal.last().label)
        assertEquals("Inicio", refuge.first().label)
        assertEquals("Perfil", refuge.last().label)
        assertTrue(personal.any { it.route == NavRoutes.COMUNIDAD })
        assertTrue(refuge.any { it.label == "Gestión" })
        assertTrue(foster.any { it.label == "Tránsitos" })
        personal.forEach { assertTrue(it.route.isNotBlank()) }
    }
}
