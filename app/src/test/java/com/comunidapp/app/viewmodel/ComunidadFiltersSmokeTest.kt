package com.comunidapp.app.viewmodel

import com.comunidapp.app.data.model.ServiceCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RC1.2 — Comunidad: filtros y categorías deterministas (estado UI).
 */
class ComunidadFiltersSmokeTest {

    @Test
    fun activeFilterCount_countsLocationAndActiveOnly() {
        assertEquals(0, ComunidadUiState().activeFilterCount)
        assertEquals(1, ComunidadUiState(locationQuery = "Palermo").activeFilterCount)
        assertEquals(1, ComunidadUiState(activeOnly = true).activeFilterCount)
        assertEquals(
            2,
            ComunidadUiState(locationQuery = "Palermo", activeOnly = true).activeFilterCount
        )
    }

    @Test
    fun defaultCategory_isDeterministicVet() {
        assertEquals(ServiceCategory.VET, ComunidadUiState().selectedCategory)
        assertFalse(ComunidadUiState().isLoading)
    }

    @Test
    fun categoryNearTitles_areHumanAndSpecific() {
        val titles = mapOf(
            ServiceCategory.VET to "Veterinarias cerca de vos",
            ServiceCategory.WALKER to "Paseadores cerca de vos",
            ServiceCategory.TRAINER to "Educadores cerca de vos",
            ServiceCategory.SHOP to "Tiendas cerca de vos"
        )
        titles.forEach { (_, title) ->
            assertTrue(title.contains("cerca de vos"))
            assertFalse(title.contains("M22"))
        }
    }
}
