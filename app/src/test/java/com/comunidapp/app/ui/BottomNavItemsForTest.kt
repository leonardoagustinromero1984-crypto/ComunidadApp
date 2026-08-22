package com.comunidapp.app.ui

import com.comunidapp.app.data.model.AccountType
import com.comunidapp.app.navigation.NavRoutes
import com.comunidapp.app.ui.components.bottomNavItemsFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomNavItemsForTest {

    @Test
    fun person_nav_is_inicio_sumate_publicar_comunidad_perfil() {
        val labels = bottomNavItemsFor(AccountType.PERSON).map { it.label }
        assertEquals(
            listOf("Inicio", "Sumate", "Publicar", "Comunidad", "Perfil"),
            labels
        )
        assertTrue(bottomNavItemsFor(AccountType.PERSON).single { it.prominent }.route == NavRoutes.PUBLISH)
    }

    @Test
    fun shelter_nav_is_inicio_animales_publicar_gestion_perfil() {
        val items = bottomNavItemsFor(AccountType.SHELTER)
        assertEquals(
            listOf("Inicio", "Animales", "Publicar", "Gestión", "Perfil"),
            items.map { it.label }
        )
        assertEquals(NavRoutes.MY_SHELTERS, items[1].route)
        assertEquals(NavRoutes.SHELTERS, items[3].route)
        assertTrue(items.single { it.prominent }.route == NavRoutes.PUBLISH)
    }

    @Test
    fun foster_nav_is_inicio_transitos_publicar_solicitudes_perfil() {
        val items = bottomNavItemsFor(AccountType.FOSTER_HOME)
        assertEquals(
            listOf("Inicio", "Tránsitos", "Publicar", "Solicitudes", "Perfil"),
            items.map { it.label }
        )
        assertEquals(NavRoutes.FOSTER_PLACEMENTS, items[1].route)
        assertEquals(NavRoutes.FOSTER_REQUESTS_RECEIVED, items[3].route)
        assertTrue(items.single { it.prominent }.route == NavRoutes.PUBLISH)
    }

    @Test
    fun business_nav_keeps_five_tabs_with_publish_center() {
        val vet = bottomNavItemsFor(AccountType.VET)
        assertEquals(
            listOf("Inicio", "Agenda", "Publicar", "Consultorio", "Perfil"),
            vet.map { it.label }
        )
        assertTrue(vet.single { it.prominent }.route == NavRoutes.PUBLISH)
        assertEquals(NavRoutes.MY_VETERINARY_APPOINTMENTS, vet[1].route)
        assertEquals(NavRoutes.MY_BUSINESS, vet[3].route)

        listOf(AccountType.SHOP, AccountType.TRAINER, AccountType.WALKER).forEach { type ->
            val items = bottomNavItemsFor(type)
            assertEquals(
                listOf("Inicio", "Agenda", "Publicar", "Mi servicio", "Perfil"),
                items.map { it.label }
            )
            assertTrue(items.single { it.prominent }.route == NavRoutes.PUBLISH)
            assertEquals(NavRoutes.MY_BUSINESS, items[3].route)
        }
    }
}
