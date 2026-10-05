package com.comunidapp.app.ui.theme

import com.comunidapp.app.ui.UiRegressionGateTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LeoVer 17B.10 — jerarquía tipográfica y filtros que no ocupan el listado.
 * No fija cada dp. No cambia reglas de negocio.
 */
class UiVisualSystem17B10Test {

    @Test
    fun typographyStaysNunitoAndReadable() {
        assertEquals(NunitoSans, LeoPageTitle.fontFamily)
        assertEquals(NunitoSans, LeoBody.fontFamily)
        assertEquals(NunitoSans, LeoCaption.fontFamily)
        assertEquals(NunitoSans, LeoChip.fontFamily)
        assertEquals(NunitoSans, Typography.bodyMedium.fontFamily)
        assertTrue(LeoPageTitle.fontSize.value > LeoSectionTitle.fontSize.value)
        assertTrue(LeoSectionTitle.fontSize.value > LeoCardTitle.fontSize.value)
        assertTrue(LeoCardTitle.fontSize.value > LeoBody.fontSize.value)
        assertTrue(LeoBody.fontSize.value >= 16f)
        assertTrue(LeoCaption.fontSize.value >= 14f)
        assertTrue(LeoChip.fontSize.value >= 14f)
        assertTrue(Typography.labelSmall.fontSize.value >= 13f)
        assertEquals(12f, LeoNavLabel.fontSize.value)
        assertTrue(LeoBody.lineHeight.value >= LeoBody.fontSize.value)
    }

    @Test
    fun publicListsOpenFiltersInsteadOfLeavingThemOnScreen() {
        val adoption = source("app/src/main/java/com/comunidapp/app/ui/screens/adoptions/AdoptionsScreen.kt")
        val search = adoption.substringAfter("fun AdoptionSearchScreen")
        assertTrue(search.contains("LeoFilterBar("))
        assertTrue(search.contains("LeoFilterSheet("))
        assertTrue(search.contains("V2LocationStringPicker"))
        assertFalse(search.contains("horizontalScroll"))
        assertFalse(search.contains("Provincia"))
        assertFalse(search.contains("Especie"))
        assertTrue(adoption.substringBefore("fun AdoptionSearchScreen").contains("Buscar mascota para adoptar"))

        val filters = source("app/src/main/java/com/comunidapp/app/ui/components/leo/LeoVerUx.kt")
        assertTrue(filters.contains("Aplicar filtros"))
        assertTrue(filters.contains("\"Filtros\""))
        assertTrue(filters.contains("\"Limpiar\""))
        assertTrue(filters.contains("value == true"))
        assertTrue(filters.contains("value == false"))
        assertTrue(filters.contains("value == null"))
        assertTrue(filters.contains("onChange(null)"))

        listOf(
            "app/src/main/java/com/comunidapp/app/ui/screens/m16/M16ShelterScreens.kt",
            "app/src/main/java/com/comunidapp/app/ui/screens/m18/M18EventScreens.kt",
            "app/src/main/java/com/comunidapp/app/ui/screens/m17/M17DonationScreens.kt",
            "app/src/main/java/com/comunidapp/app/ui/screens/lostfound/LostFoundScreen.kt"
        ).forEach { path ->
            val screen = source(path)
            assertTrue(path, screen.contains("LeoFilterSheet("))
            assertTrue(path, screen.contains("LeoFilterBar("))
        }

        val shelters = source("app/src/main/java/com/comunidapp/app/ui/screens/m16/M16ShelterScreens.kt")
        val shelterFilters = shelters.substringAfter("fun M16ListFilterRow").substringBefore("fun M16PublicShelterCard")
        assertTrue(shelterFilters.contains("Perros"))
        assertTrue(shelterFilters.contains("visibleLabel()"))
        assertTrue(shelterFilters.contains("Organizaciones verificadas"))
        assertFalse(shelterFilters.contains("Estado operativo"))
        assertFalse(shelterFilters.contains("UNVERIFIED_OR_PENDING"))
    }

    @Test
    fun triStateAndEmptyStateStayShared() {
        val adoption = source("app/src/main/java/com/comunidapp/app/ui/screens/adoptions/AdoptionProfileFields.kt")
        val foster = source("app/src/main/java/com/comunidapp/app/ui/screens/foster/FosterTraitFields.kt")
        assertTrue(adoption.contains("LeoTriStateSelector("))
        assertTrue(foster.contains("LeoTriStateSelector("))
        val empty = source("app/src/main/java/com/comunidapp/app/ui/components/state/FoundationStates.kt")
        assertTrue(empty.contains("fun EmptyState("))
        assertTrue(empty.contains("fun ErrorState("))
        assertTrue(empty.contains("fun LoadingState("))
        assertTrue(empty.contains("Reintentar"))
    }

    private fun source(relative: String): String = UiRegressionGateTest.sourceFile(relative).readText()
}
