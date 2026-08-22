package com.comunidapp.app.ui

import com.comunidapp.app.domain.context.OperationalContext
import com.comunidapp.app.domain.map.LeoVerMapPolicy
import com.comunidapp.app.domain.publish.ContextPublishMatrix
import com.comunidapp.app.domain.publish.PublishAction
import com.comunidapp.app.domain.schedule.OpenNowStatus
import com.comunidapp.app.domain.schedule.ProviderScheduleClock
import com.comunidapp.app.domain.schedule.ProviderWeeklySchedule
import com.comunidapp.app.domain.schedule.WeeklyHoursDay
import com.comunidapp.app.navigation.NavRoutes
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.BrandGreen
import com.comunidapp.app.ui.theme.BrandText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import androidx.compose.ui.graphics.Color

class LeoVerUx07ContractTest {

    @Test
    fun COMMUNITY_USES_CANONICAL_SCREEN() {
        val screen = source("app/src/main/java/com/comunidapp/app/ui/screens/comunidad/ComunidadScreen.kt")
        assertTrue(screen.contains("LeoVerProviderCard"))
        assertTrue(screen.contains("containerColor = visual.background"))
        assertTrue(screen.contains("LeoFilterChip"))
        assertTrue(screen.contains("Lista"))
        assertTrue(screen.contains("Mapa"))
        assertFalse(screen.contains("FilterChipDefaults"))
        assertFalse(screen.contains("AssistChip"))
    }

    @Test
    fun COMMUNITY_NO_LEGACY_UI_IMPORTS() {
        val screen = source("app/src/main/java/com/comunidapp/app/ui/screens/comunidad/ComunidadScreen.kt")
        assertFalse(screen.contains("import com.comunidapp.app.ui.legacy"))
        assertFalse(screen.contains("LeoServiceTile"))
        assertFalse(screen.contains("BrandOrangeContainer"))
        assertFalse(screen.contains("0xFF74AD7F"))
    }

    @Test
    fun LEGACY_UI_USAGE_FAILS_GATE() {
        val components = source("app/src/main/java/com/comunidapp/app/ui/components/leo/LeoComponents.kt")
        assertTrue(components.contains("DeprecationLevel.ERROR"))
        assertTrue(components.contains("fun LeoServiceTile("))
    }

    @Test
    fun LAUNCHER_BACKGROUND_CANONICAL() {
        val bg = source("app/src/main/res/drawable/ic_launcher_background.xml")
        assertTrue(bg.contains("@color/brand_background"))
        assertFalse(bg.contains("@color/brand_cream"))
        val colors = source("app/src/main/res/values/colors.xml")
        assertTrue(colors.contains("name=\"brand_background\">#FFFAFBF8"))
    }

    @Test
    fun APP_BACKGROUND_CANONICAL() {
        assertEquals(Color(0xFFFAFBF8), BrandBackground)
        assertEquals(Color(0xFF49B749), BrandGreen)
        assertEquals(Color(0xFF263238), BrandText)
    }

    @Test
    fun PERSON_PUBLISH_HAS_SOCIAL() {
        val actions = ContextPublishMatrix.optionsFor(OperationalContext.Personal).map { it.action }
        assertTrue(actions.contains(PublishAction.SOCIAL_POST))
        assertTrue(actions.contains(PublishAction.REEL))
        assertTrue(actions.contains(PublishAction.STORY))
    }

    @Test
    fun PERSON_PUBLISH_HAS_NO_DONATION() {
        val actions = ContextPublishMatrix.optionsFor(OperationalContext.Personal).map { it.action }
        assertFalse(actions.contains(PublishAction.DONATION))
    }

    @Test
    fun PERSON_PUBLISH_HAS_NO_CAMPAIGN() {
        val actions = ContextPublishMatrix.optionsFor(OperationalContext.Personal).map { it.action }
        assertFalse(actions.contains(PublishAction.CAMPAIGN))
    }

    @Test
    fun CONTEXT_PUBLISH_OPTIONS_MATCH_DOMAIN() {
        val provider = ContextPublishMatrix.optionsFor(
            OperationalContext.Provider("p1", "Vet", "VETERINARY")
        ).map { it.action }
        assertTrue(provider.contains(PublishAction.UPDATE_PROVIDER_FICHA))
        val org = ContextPublishMatrix.optionsFor(
            OperationalContext.Organization("o1", "Refugio", "REFUGE_ONG")
        ).map { it.action }
        assertTrue(org.contains(PublishAction.CAMPAIGN))
        assertTrue(org.contains(PublishAction.DONATION))
    }

    @Test
    fun ACTIVE_CONTEXT_NOT_SECURITY_AUTHORITY() {
        val matrix = source("app/src/main/java/com/comunidapp/app/domain/publish/ContextPublishMatrix.kt")
        assertTrue(matrix.contains("ActiveContext is not security authority"))
        val nav = source("app/src/main/java/com/comunidapp/app/domain/context/ContextNavigation.kt")
        assertTrue(nav.contains("DAYCARE_RESERVATIONS"))
        assertTrue(nav.contains("DAYCARE_GUESTS"))
        assertTrue(nav.indexOf("DAYCARE_RESERVATIONS") != nav.indexOf("DAYCARE_GUESTS"))
    }

    @Test
    fun DAYCARE_RESERVATIONS_ROUTE_DISTINCT() {
        assertEquals("daycare_reservations", NavRoutes.DAYCARE_RESERVATIONS)
        assertEquals("daycare_guests", NavRoutes.DAYCARE_GUESTS)
        assertTrue(NavRoutes.DAYCARE_RESERVATIONS != NavRoutes.DAYCARE_GUESTS)
        assertTrue(NavRoutes.DAYCARE_RESERVATIONS != NavRoutes.MY_BUSINESS)
    }

    @Test
    fun PROVIDER_SCHEDULE_STRUCTURED() {
        val schedule = ProviderWeeklySchedule(
            listOf(WeeklyHoursDay(1, closed = false, opensAt = "09:00", closesAt = "18:00"))
        )
        val mondayOpen = ZonedDateTime.of(2026, 8, 17, 10, 0, 0, 0, ZoneId.of("America/Argentina/Buenos_Aires"))
        assertEquals(OpenNowStatus.OPEN, ProviderScheduleClock.openNow(schedule, mondayOpen))
        val mondayClosed = mondayOpen.with(LocalTime.of(20, 0))
        assertEquals(OpenNowStatus.CLOSED, ProviderScheduleClock.openNow(schedule, mondayClosed))
        assertEquals(OpenNowStatus.UNKNOWN, ProviderScheduleClock.openNow(ProviderWeeklySchedule()))
    }

    @Test
    fun MAP_PROVIDER_ABSTRACTED() {
        assertEquals("FREE_ONLY", LeoVerMapPolicy.COST_POLICY)
        assertEquals(0, LeoVerMapPolicy.PAID_MAP_API_DEPENDENCIES)
        assertFalse(LeoVerMapPolicy.GOOGLE_PLACES_ENABLED)
        assertFalse(LeoVerMapPolicy.GOOGLE_GEOCODING_ENABLED)
        val map = source("app/src/main/java/com/comunidapp/app/ui/map/LeoVerMap.kt")
        assertTrue(map.contains("fun LeoVerMap("))
        val community = source("app/src/main/java/com/comunidapp/app/ui/screens/comunidad/ComunidadScreen.kt")
        assertFalse(community.contains("com.google.android.gms.maps"))
        assertFalse(community.contains("com.google.maps.android.compose.GoogleMap"))
    }

    @Test
    fun MAP_KEY_NOT_HARDCODED() {
        val manifest = source("app/src/main/AndroidManifest.xml")
        assertTrue(manifest.contains("\${MAPS_API_KEY}"))
        assertFalse(manifest.contains("AIza"))
        val gradle = source("app/build.gradle.kts")
        assertTrue(gradle.contains("MAPS_API_KEY"))
    }

    @Test
    fun FOSTER_NEW_PLACEMENT_ENTRY_VISIBLE() {
        val home = source("app/src/main/java/com/comunidapp/app/ui/screens/foster/FosterScreens.kt")
        assertTrue(home.contains("Tránsitos"))
        assertTrue(home.contains("Alojamientos temporales de mascotas que están a tu cuidado."))
        assertTrue(home.contains("+ Nuevo tránsito"))
        assertTrue(home.contains("VitaCora ✓ Acceso habilitado"))
        assertEquals("foster_placements/new", NavRoutes.FOSTER_NEW_PLACEMENT)
    }

    @Test
    fun NEAR_ME_DOES_NOT_OPEN_ZONE_LIST() {
        val screen = source("app/src/main/java/com/comunidapp/app/ui/screens/comunidad/ComunidadScreen.kt")
        assertTrue(screen.contains("includeZone = false"))
        assertTrue(screen.contains("Cerca de tu ubicación"))
        assertTrue(screen.contains("ForegroundLocation"))
        assertFalse(screen.contains("includeZone = true"))
    }

    @Test
    fun COMMUNITY_LIST_MAP_TOGGLE() {
        val screen = source("app/src/main/java/com/comunidapp/app/ui/screens/comunidad/ComunidadScreen.kt")
        assertTrue(screen.contains("label = \"Lista\""))
        assertTrue(screen.contains("label = \"Mapa\""))
        assertTrue(screen.contains("CommunityResultsView.MAP"))
        assertTrue(screen.contains("onCameraIdle = { }"))
    }

    private fun source(path: String): String = UiRegressionGateTest.sourceFile(path).readText()
}
