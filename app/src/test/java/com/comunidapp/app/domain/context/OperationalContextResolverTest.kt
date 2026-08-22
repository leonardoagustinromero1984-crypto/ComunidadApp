package com.comunidapp.app.domain.context

import com.comunidapp.app.data.model.FosterAvailabilityStatus
import com.comunidapp.app.navigation.NavRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationalContextResolverTest {

    @Test
    fun missing_selection_falls_back_to_personal() {
        val available = listOf(
            OperationalContext.Personal,
            OperationalContext.Foster("fh-1", "Mi hogar")
        )
        assertEquals(OperationalContext.Personal, resolveActiveContext(available, null))
    }

    @Test
    fun invalid_saved_context_falls_back_to_personal() {
        val available = listOf(OperationalContext.Personal)
        val saved = ActiveContextSelection(OperationalContextKind.ORGANIZATION, "org-gone")
        assertEquals(OperationalContext.Personal, resolveActiveContext(available, saved))
    }

    @Test
    fun valid_saved_context_is_restored() {
        val foster = OperationalContext.Foster("fh-1", "Mi hogar")
        val available = listOf(OperationalContext.Personal, foster)
        val saved = ActiveContextSelection(OperationalContextKind.FOSTER, "fh-1")
        assertEquals(foster, resolveActiveContext(available, saved))
    }

    @Test
    fun foster_available_derives_from_availability_not_from_identity() {
        val withFosterContext = listOf(
            OperationalContext.Personal,
            OperationalContext.Foster("fh-1", "Mi hogar")
        )
        assertTrue(
            AvailableContextsResolver.deriveCapabilities(withFosterContext, FosterAvailabilityStatus.AVAILABLE)
                .contains(PersonalCapability.FOSTER_AVAILABLE)
        )
        assertFalse(
            AvailableContextsResolver.deriveCapabilities(withFosterContext, FosterAvailabilityStatus.UNAVAILABLE)
                .contains(PersonalCapability.FOSTER_AVAILABLE)
        )
        assertFalse(
            AvailableContextsResolver.deriveCapabilities(listOf(OperationalContext.Personal), null)
                .contains(PersonalCapability.FOSTER_AVAILABLE)
        )
    }

    @Test
    fun personal_navigation_spec_is_preserved() {
        val items = ContextNavigation.personalItems()
        assertEquals(
            listOf("Inicio", "Sumate", "Publicar", "Comunidad", "Perfil"),
            items.map { it.label }
        )
        assertEquals(NavRoutes.HOME, items[0].route)
        assertEquals(NavRoutes.SUMATE, items[1].route)
        assertEquals(NavRoutes.PUBLISH, items[2].route)
        assertEquals(NavRoutes.COMUNIDAD, items[3].route)
        assertEquals(NavRoutes.PROFILE, items[4].route)
        assertTrue(items[2].prominent)
    }

    @Test
    fun veterinary_navigation_matches_agreed_intent() {
        val items = ContextNavigation.itemsFor(
            OperationalContext.Veterinary("clinic-1", "Consultorio")
        )
        assertEquals(
            listOf("Inicio", "Agenda", "Publicar", "Consultorio", "Perfil"),
            items.map { it.label }
        )
    }

    @Test
    fun daycare_navigation_matches_agreed_intent() {
        val items = ContextNavigation.itemsFor(
            OperationalContext.Provider("p1", "Guardería", "DAYCARE")
        )
        assertEquals(
            listOf("Inicio", "Reservas", "Publicar", "Huéspedes", "Perfil"),
            items.map { it.label }
        )
    }
}
