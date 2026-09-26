package com.comunidapp.app.ui

import com.comunidapp.app.data.model.AccountType
import com.comunidapp.app.navigation.NavRoutes
import com.comunidapp.app.ui.components.bottomNavItemsFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guarda que el bottom nav de PERSON abre las 5 superficies autoritativas
 * alineadas al board LeoVer UI V2 — Cuenta Persona.
 */
class PersonaBottomSurfacesTest {

    @Test
    fun person_nav_order_and_routes() {
        val items = bottomNavItemsFor(AccountType.PERSON)
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
        assertFalse(items.any { it.label == "Mensajes" })
    }

    @Test
    fun navGraph_wires_all_five_persona_screens() {
        assertTrue(composableBlock("NavRoutes.HOME").contains("HomeScreen("))
        assertTrue(composableBlock("NavRoutes.SUMATE").contains("SumateScreen("))
        assertTrue(composableBlock("NavRoutes.PUBLISH").contains("PublishScreen("))
        assertTrue(composableBlock("NavRoutes.COMUNIDAD").contains("ComunidadScreen("))
        assertTrue(composableBlock("NavRoutes.PROFILE").contains("ProfileScreen("))
    }

    @Test
    fun home_matches_persona_reference() {
        val screen = sourceFile(
            "app/src/main/java/com/comunidapp/app/ui/screens/home/HomeScreen.kt"
        ).readText()
        val header = sourceFile(
            "app/src/main/java/com/comunidapp/app/ui/screens/home/SocialHomeComponents.kt"
        ).readText()
        assertTrue(header.contains("Hola,"))
        assertTrue(header.contains("Tu comunidad"))
        assertTrue(screen.contains("StoriesRow("))
        assertTrue(screen.contains("LeoSocialPostCard("))
        assertTrue(screen.contains("Tu comunidad empieza acá"))
        assertFalse(screen.contains("Mis mascotas"))
        assertFalse(screen.contains("Perdí mi mascota"))
        assertFalse(screen.contains("Encontré un animal"))
        assertFalse(screen.contains("V2PetsStrip"))
        assertFalse(screen.contains("V2UrgentActionRow"))
        assertFalse(screen.contains("FeedAudienceSelector"))
        assertTrue(screen.contains("VisualDirectionPilot"))
    }

    @Test
    fun sumate_matches_persona_reference() {
        val screen = sourceFile(
            "app/src/main/java/com/comunidapp/app/ui/screens/sumate/SumateScreen.kt"
        ).readText()
        assertTrue(screen.contains("Elegí cómo querés ayudar hoy"))
        assertTrue(screen.contains("title = \"Adopción\""))
        assertTrue(screen.contains("Refugios / ONG"))
        assertTrue(screen.contains("Hogares de tránsito"))
        assertTrue(screen.contains("Perdidos / Encontrados"))
        assertTrue(screen.contains("Donaciones"))
        assertTrue(screen.contains("Eventos"))
        assertFalse(screen.contains("Voluntariado / ayuda"))
    }

    @Test
    fun publish_matches_persona_reference() {
        val screen = sourceFile(
            "app/src/main/java/com/comunidapp/app/ui/screens/publish/PublishScreen.kt"
        ).readText()
        val block = composableBlock("NavRoutes.PUBLISH")
        assertTrue(screen.contains("Elegí el tipo de publicación"))
        assertTrue(screen.contains("ContextPublishMatrix"))
        assertTrue(screen.contains("PublishAction.SOCIAL_POST"))
        assertTrue(screen.contains("PublishAction.REEL"))
        assertTrue(screen.contains("PublishAction.STORY"))
        assertFalse(screen.contains("Mascota en adopción"))
        assertFalse(screen.contains("Animal perdido"))
        assertFalse(screen.contains("Animal encontrado"))
        assertFalse(screen.contains("Crear campaña"))
        assertFalse(screen.contains("Crear donación"))
        assertTrue(block.contains("PublishScreen("))
    }

    @Test
    fun comunidad_screen_is_services_directory_not_social_feed() {
        val screen = sourceFile(
            "app/src/main/java/com/comunidapp/app/ui/screens/comunidad/ComunidadScreen.kt"
        ).readText()
        val card = sourceFile(
            "app/src/main/java/com/comunidapp/app/ui/components/leo/LeoVerProviderCard.kt"
        ).readText()
        assertTrue(screen.contains("Servicios para tu mascota"))
        assertTrue(screen.contains("Veterinarias"))
        assertTrue(screen.contains("Tiendas"))
        assertTrue(screen.contains("Paseadores"))
        assertTrue(screen.contains("Adiestradores"))
        assertTrue(screen.contains("Guarderías"))
        assertTrue(screen.contains("Peluquerías"))
        assertTrue(screen.contains("Cerca mío"))
        assertTrue(screen.contains("V2LocationCityProvincePicker"))
        assertTrue(screen.contains("LeoVerProviderCard"))
        assertTrue(card.contains("Ver perfil"))
        assertTrue(card.contains("text = \"Ver perfil\""))
        assertFalse(screen.contains("LeoServiceTile"))
        assertFalse(screen.contains("feed social"))
        assertFalse(screen.contains("selectedContainerColor = BrandGreen"))
        assertFalse(screen.contains("FilterChipDefaults"))
        assertTrue(screen.contains("LeoFilterChip"))
        assertTrue(screen.contains("VisualDirectionPilot"))
        assertTrue(screen.contains("item(key = \"categories\")"))
        assertTrue(screen.contains("Elegí un servicio para encontrar opciones cerca tuyo."))
    }

    @Test
    fun personal_hides_received_adoptions_and_foster_management() {
        val menu = sourceFile(
            "app/src/main/java/com/comunidapp/app/ui/screens/profile/ProfileMenuSheet.kt"
        ).readText()
        assertTrue(menu.contains("showReceivedApplications"))
        val adoptions = composableBlock("NavRoutes.ADOPTIONS")
        assertTrue(adoptions.contains("showReceivedApplications = !context.isPersonal"))
        val sumate = sourceFile(
            "app/src/main/java/com/comunidapp/app/ui/screens/sumate/SumateScreen.kt"
        ).readText()
        assertTrue(sumate.contains("Ofrecer hogar de tránsito"))
        assertTrue(sumate.contains("onCreateFoster"))
        val graph = sourceFile(
            "app/src/main/java/com/comunidapp/app/navigation/ComunidappNavGraph.kt"
        ).readText()
        assertTrue(graph.contains("onCreateFoster = { navController.navigate(NavRoutes.FOSTER_HOME_FORM) }"))
        assertTrue(graph.contains("MyPublicationsScreen("))
        assertTrue(graph.contains("ComunidadScreen("))
        assertTrue(graph.contains("ProfileScreen("))
        val m16 = sourceFile(
            "app/src/main/java/com/comunidapp/app/navigation/M16NavGraph.kt"
        ).readText()
        assertTrue(m16.contains("if (context.isPersonal)"))
        assertTrue(m16.contains("onManage = if (context.isPersonal)"))
    }

    @Test
    fun profile_screen_is_persona_layout_without_invented_followers() {
        val screen = sourceFile(
            "app/src/main/java/com/comunidapp/app/ui/screens/profile/ProfileScreen.kt"
        ).readText()
        assertTrue(screen.contains("VisualDirectionPilot"))
        assertFalse(screen.contains(".background(ProfileGreen)"))
        assertFalse(screen.contains("BrandGreenDark"))
        assertTrue(screen.contains("Mis mascotas"))
        assertTrue(screen.contains("Mensajes"))
        assertFalse(screen.contains("Seguidores y siguiendo"))
        assertTrue(screen.contains("Donaciones"))
        assertTrue(screen.contains("Configuración"))
        assertTrue(screen.contains("Mis publicaciones"))
        assertTrue(screen.contains("Editar perfil"))
        assertTrue(screen.contains("onNavigateToMyPublications"))
        assertTrue(screen.contains("Usar LeoVer como"))
        assertFalse(screen.contains("+ Agregar función o perfil"))
        assertFalse(screen.contains("posts_grid"))
        assertFalse(screen.contains("items(uiState.posts"))
        assertTrue(screen.contains("V2PetsStrip"))
        assertTrue(screen.contains("onNavigateToChat"))
        assertFalse(screen.contains("followersCount = uiState.friends.size"))
        assertFalse(screen.contains("followingCount = uiState.friends.size"))
    }

    private fun composableBlock(routeConst: String): String {
        val source = sourceFile(
            "app/src/main/java/com/comunidapp/app/navigation/ComunidappNavGraph.kt"
        ).readText()
        val marker = "composable($routeConst)"
        val start = source.indexOf(marker)
        require(start >= 0) { "Missing $marker in ComunidappNavGraph" }
        val next = source.indexOf("\n    composable(", start + marker.length)
        return if (next >= 0) source.substring(start, next) else source.substring(start)
    }

    private fun sourceFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("../$relativePath"),
            File("../../$relativePath"),
            File(System.getProperty("user.dir"), relativePath),
            File(System.getProperty("user.dir"), "../$relativePath")
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("$relativePath not found. cwd=${System.getProperty("user.dir")}")
    }
}
