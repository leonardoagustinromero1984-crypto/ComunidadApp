package com.comunidapp.app.ui

import com.comunidapp.app.data.model.AccountType
import com.comunidapp.app.data.model.ServiceCategory
import com.comunidapp.app.navigation.NavRoutes
import com.comunidapp.app.ui.components.bottomNavItemsFor
import com.comunidapp.app.ui.theme.LeoVerVisualPalette
import com.comunidapp.app.viewmodel.ComunidadUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UX-05 static contracts: social-first Home, category-first Comunidad,
 * compact Profile, Settings context switcher, Color Direction V3 experimental.
 */
class Ux05SocialHomeCommunityProfileSettingsTest {

    @Test
    fun HOME_STORIES_VISIBLE() {
        val home = source("app/src/main/java/com/comunidapp/app/ui/screens/home/HomeScreen.kt")
        assertTrue(home.contains("StoriesRow("))
        assertTrue(home.contains("item(key = \"stories\")"))
    }

    @Test
    fun HOME_FEED_FOLLOWS_STORIES() {
        val home = source("app/src/main/java/com/comunidapp/app/ui/screens/home/HomeScreen.kt")
        val stories = home.indexOf("item(key = \"stories\")")
        val empty = home.indexOf("item(key = \"empty_feed\")")
        val card = home.indexOf("LeoSocialPostCard(")
        assertTrue(stories >= 0)
        assertTrue(empty > stories)
        assertTrue(card > stories)
    }

    @Test
    fun HOME_POST_IMAGE_TAP_OPENS_SAME_DETAIL() {
        val card = source("app/src/main/java/com/comunidapp/app/ui/components/leo/LeoSocialPostCard.kt")
        assertTrue(card.contains("detectTapGestures(onTap = { onPostClick() })"))
        assertTrue(card.contains("HorizontalPager("))
        val home = source("app/src/main/java/com/comunidapp/app/navigation/ComunidappNavGraph.kt")
        val click = home.indexOf("onPostClick = { postId ->")
        assertTrue(click >= 0)
        assertTrue(home.substring(click, click + 280).contains("NavRoutes.postDetail(postId)"))
        val routes = source("app/src/main/java/com/comunidapp/app/navigation/NavRoutes.kt")
        assertTrue(routes.contains("posts/{postId}"))
    }

    @Test
    fun HOME_NO_ADD_PET_SHORTCUT() {
        val home = source("app/src/main/java/com/comunidapp/app/ui/screens/home/HomeScreen.kt")
        assertFalse(home.contains("Agregar mascota"))
        assertFalse(home.contains("V2PetsStrip"))
        assertFalse(home.contains("HomeGreetingAndPetsRow"))
    }

    @Test
    fun HOME_NO_LOST_SHORTCUT() {
        val home = source("app/src/main/java/com/comunidapp/app/ui/screens/home/HomeScreen.kt")
        assertFalse(home.contains("Perdí mi mascota"))
        assertFalse(home.contains("V2UrgentActionRow"))
    }

    @Test
    fun HOME_NO_FOUND_SHORTCUT() {
        val home = source("app/src/main/java/com/comunidapp/app/ui/screens/home/HomeScreen.kt")
        assertFalse(home.contains("Encontré un animal"))
    }

    @Test
    fun HOME_PERSONAL_BOTTOM_NAV() {
        val items = bottomNavItemsFor(AccountType.PERSON)
        assertEquals(
            listOf("Inicio", "Sumate", "Publicar", "Comunidad", "Perfil"),
            items.map { it.label }
        )
        assertEquals(NavRoutes.HOME, items[0].route)
        assertEquals(NavRoutes.PROFILE, items[4].route)
    }

    @Test
    fun COMMUNITY_SERVICE_CATEGORIES_FIRST() {
        val screen = source("app/src/main/java/com/comunidapp/app/ui/screens/comunidad/ComunidadScreen.kt")
        val categories = screen.indexOf("item(key = \"categories\")")
        val filters = screen.indexOf("item(key = \"filters\")")
        val choose = screen.indexOf("¿Qué servicio necesitás?")
        assertTrue(categories >= 0)
        assertTrue(choose > categories)
        assertTrue(filters > categories)
        assertTrue(screen.contains("Veterinarias"))
        assertTrue(screen.contains("Tiendas"))
        assertTrue(screen.contains("Paseadores"))
        assertTrue(screen.contains("Adiestradores"))
        assertTrue(screen.contains("Guarderías"))
        assertTrue(screen.contains("Peluquerías"))
    }

    @Test
    fun COMMUNITY_FILTERS_HIDDEN_BEFORE_SELECTION() {
        val screen = source("app/src/main/java/com/comunidapp/app/ui/screens/comunidad/ComunidadScreen.kt")
        assertTrue(screen.contains("if (uiState.selectedCategory == null)"))
        assertTrue(screen.contains("Elegí un servicio para encontrar opciones cerca tuyo."))
        assertNull(ComunidadUiState().selectedCategory)
    }

    @Test
    fun COMMUNITY_FILTERS_VISIBLE_AFTER_SELECTION() {
        val screen = source("app/src/main/java/com/comunidapp/app/ui/screens/comunidad/ComunidadScreen.kt")
        assertTrue(screen.contains("item(key = \"filters\")"))
        assertTrue(screen.contains("V2LocationCityProvincePicker("))
        assertTrue(screen.contains("Limpiar filtros"))
    }

    @Test
    fun COMMUNITY_PROVINCE_FILTER() {
        val picker = source("app/src/main/java/com/comunidapp/app/ui/components/v2/V2LocationPicker.kt")
        val vm = source("app/src/main/java/com/comunidapp/app/viewmodel/ServiceViewModel.kt")
        assertTrue(
            picker.contains("GeoDivisionLabels.administrativeAreaLabel") ||
                picker.contains("label = \"Provincia\"")
        )
        assertTrue(vm.contains("fun applyGeography("))
    }

    @Test
    fun COMMUNITY_LOCALITY_FILTER() {
        val picker = source("app/src/main/java/com/comunidapp/app/ui/components/v2/V2LocationPicker.kt")
        assertTrue(
            picker.contains("GeoDivisionLabels.localityLabel") ||
                picker.contains("label = \"Localidad\"")
        )
        assertTrue(picker.contains("fun V2LocationCityProvincePicker("))
    }

    @Test
    fun COMMUNITY_RESULTS_AFTER_FILTERS() {
        val screen = source("app/src/main/java/com/comunidapp/app/ui/screens/comunidad/ComunidadScreen.kt")
        val filters = screen.indexOf("item(key = \"filters\")")
        val count = screen.indexOf("item(key = \"result_count\")")
        val cards = screen.indexOf("LeoVerProviderCard(")
        assertTrue(filters >= 0)
        assertTrue(count > filters)
        assertTrue(cards > count)
        assertTrue(screen.contains("resultados"))
    }

    @Test
    fun COMMUNITY_CANONICAL_DATA_ONLY() {
        val screen = source("app/src/main/java/com/comunidapp/app/ui/screens/comunidad/ComunidadScreen.kt")
        assertFalse(screen.contains("MockData."))
        assertTrue(screen.contains("viewModel.services"))
        assertEquals(ServiceCategory.VET, ServiceCategory.VET)
    }

    @Test
    fun PROFILE_MAIN_HAS_NO_CONTEXT_SWITCHER_LIST() {
        val profile = source("app/src/main/java/com/comunidapp/app/ui/screens/profile/ProfileScreen.kt")
        assertTrue(profile.contains("Usar LeoVer como"))
        assertFalse(profile.contains("available.forEach"))
        assertTrue(profile.contains("CompactUseLeoverAsRow"))
    }

    @Test
    fun PROFILE_HAS_COMPACT_ADD_FUNCTION() {
        val settings = source("app/src/main/java/com/comunidapp/app/ui/screens/profile/SettingsScreen.kt")
        assertTrue(settings.contains("Agregar función o perfil"))
        val graph = source("app/src/main/java/com/comunidapp/app/navigation/ComunidappNavGraph.kt")
        val settingsBlock = composableBlock(graph, "NavRoutes.SETTINGS")
        assertTrue(settingsBlock.contains("Onb02FlowKind.ADD_FUNCTION_LATER"))
    }

    @Test
    fun PROFILE_PETS_FOLLOW_HEADER() {
        val profile = source("app/src/main/java/com/comunidapp/app/ui/screens/profile/ProfileScreen.kt")
        val header = profile.indexOf("item(key = \"header\")")
        val add = profile.indexOf("item(key = \"use_leover_as\")")
        val pets = profile.indexOf("item(key = \"pets_header\")")
        assertTrue(header >= 0)
        assertTrue(add > header)
        assertTrue(pets > add)
        assertTrue(profile.contains("Mis mascotas"))
    }

    @Test
    fun PROFILE_SETTINGS_NAV_WORKS() {
        val profile = source("app/src/main/java/com/comunidapp/app/ui/screens/profile/ProfileScreen.kt")
        assertTrue(profile.contains("onSettings = onNavigateToSettings"))
        assertTrue(profile.contains("onClick = onNavigateToSettings"))
        val graph = source("app/src/main/java/com/comunidapp/app/navigation/ComunidappNavGraph.kt")
        assertTrue(graph.contains("composable(NavRoutes.SETTINGS)"))
        assertTrue(graph.contains("onNavigateToSettings = { navController.navigate(NavRoutes.SETTINGS) }"))
        assertTrue(composableBlock(graph, "NavRoutes.SETTINGS").contains("SettingsScreen("))
    }

    @Test
    fun SETTINGS_HAS_USE_LEOVER_AS() {
        val settings = source("app/src/main/java/com/comunidapp/app/ui/screens/profile/SettingsScreen.kt")
        assertTrue(settings.contains("Agregar función o perfil"))
        assertTrue(settings.contains("onClick = onAddFunction"))
        val graph = source("app/src/main/java/com/comunidapp/app/navigation/ComunidappNavGraph.kt")
        assertTrue(composableBlock(graph, "NavRoutes.SETTINGS").contains("ADD_FUNCTION_LATER"))
    }

    @Test
    fun SETTINGS_CONTEXTS_LOAD() {
        val screen = source("app/src/main/java/com/comunidapp/app/ui/screens/onboarding/onb02/Onb02Screens.kt")
        val fn = screen.substringAfter("fun UseLeoverAsScreen(")
        assertTrue(fn.contains("OperationalContextProvider.available"))
        assertTrue(fn.contains("available.forEach"))
    }

    @Test
    fun SETTINGS_CONTEXT_SWITCH_WORKS() {
        val screen = source("app/src/main/java/com/comunidapp/app/ui/screens/onboarding/onb02/Onb02Screens.kt")
        val fn = screen.substringAfter("fun UseLeoverAsScreen(").substringBefore("fun HelpTutorialsScreen(")
        assertTrue(fn.contains("OperationalContextProvider.select(ctx)"))
    }

    @Test
    fun SETTINGS_TUTORIAL_LIBRARY_AVAILABLE() {
        val settings = source("app/src/main/java/com/comunidapp/app/ui/screens/profile/SettingsScreen.kt")
        assertTrue(settings.contains("Tutoriales"))
        assertTrue(settings.contains("onHelpTutorials"))
        val help = source("app/src/main/java/com/comunidapp/app/ui/screens/onboarding/onb02/Onb02Screens.kt")
        assertTrue(help.contains("fun HelpTutorialsScreen("))
        assertTrue(help.contains("TutorialCatalog.libraryEntries()"))
    }

    @Test
    fun SETTINGS_USES_CANONICAL_BACKGROUND() {
        val settings = source("app/src/main/java/com/comunidapp/app/ui/screens/profile/SettingsScreen.kt")
        assertTrue(settings.contains("VisualDirectionPilot"))
        assertTrue(settings.contains("containerColor = visual.background"))
        val v3 = LeoVerVisualPalette.v3Experimental
        assertEquals(androidx.compose.ui.graphics.Color(0xFFFAFBF8), v3.background)
        assertEquals(androidx.compose.ui.graphics.Color(0xFFFF7A00), v3.primary)
    }

    @Test
    fun SETTINGS_NO_LEGACY_FULL_SCREEN_CREAM() {
        val settings = source("app/src/main/java/com/comunidapp/app/ui/screens/profile/SettingsScreen.kt")
        assertFalse(settings.contains("BrandCream"))
        assertFalse(settings.contains("0xFFFFF8E1"))
        assertFalse(settings.contains("0xFFFFF6EA"))
        assertTrue(settings.contains("LeoVerSettingsSection"))
        assertTrue(settings.contains("LeoVerSettingsRow"))
    }

    private fun source(relative: String): String = UiRegressionGateTest.sourceFile(relative).readText()

    private fun composableBlock(source: String, routeConst: String): String {
        val marker = "composable($routeConst)"
        val start = source.indexOf(marker)
        require(start >= 0) { "Missing $marker" }
        val next = source.indexOf("\n    composable(", start + marker.length)
        return if (next >= 0) source.substring(start, next) else source.substring(start)
    }
}
