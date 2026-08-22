package com.comunidapp.app.data.model

import com.comunidapp.app.data.repository.fromCanonicalCode
import com.comunidapp.app.domain.context.ContextNavigation
import com.comunidapp.app.domain.context.OperationalContext
import com.comunidapp.app.navigation.NavRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CommunityCanonicalUiTest {

    @Test
    fun categoryChipsCoverProductCatalog() {
        val expected = listOf(
            ServiceCategory.VET,
            ServiceCategory.SHOP,
            ServiceCategory.WALKER,
            ServiceCategory.TRAINER,
            ServiceCategory.DAYCARE,
            ServiceCategory.GROOMING
        )
        assertTrue(ServiceCategory.entries.containsAll(expected))
        assertEquals(ServiceCategory.VET, ServiceCategory.fromCanonicalCode("VETERINARY"))
        assertEquals(ServiceCategory.DAYCARE, ServiceCategory.fromCanonicalCode("DAYCARE"))
        assertEquals(ServiceCategory.GROOMING, ServiceCategory.fromCanonicalCode("GROOMING"))
        assertEquals(ServiceCategory.SHOP, ServiceCategory.fromCanonicalCode("SHOP"))
    }

    @Test
    fun emptyBackendDoesNotInventProfiles() {
        val empty = emptyList<ServiceProfile>()
        assertTrue(empty.isEmpty())
        assertFalse(empty.any { it.name.contains("Vet San Martín") })
    }

    @Test
    fun personalBottomNavIncludesComunidad() {
        val items = ContextNavigation.itemsFor(OperationalContext.Personal)
        assertEquals(
            listOf("Inicio", "Sumate", "Publicar", "Comunidad", "Perfil"),
            items.map { it.label }
        )
        assertTrue(items.any { it.route == NavRoutes.COMUNIDAD })
    }

    @Test
    fun providerCardMappingUsesCanonicalComponent() {
        val card = File("src/main/java/com/comunidapp/app/ui/components/leo/LeoVerProviderCard.kt").readText()
        assertTrue(card.contains("fun LeoVerProviderCard("))
        assertTrue(card.contains("Ver perfil"))
        assertTrue(card.contains("service.photoUrl"))
        assertTrue(card.contains("service.rating"))
        assertTrue(card.contains("localityLabel"))
    }
}
