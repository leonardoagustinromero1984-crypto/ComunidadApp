package com.comunidapp.app.domain.onboarding.onb02

import com.comunidapp.app.navigation.NavRoutes
import com.comunidapp.app.ui.UiRegressionGateTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UX-05 tutorial copy + shared renderer contracts.
 * Switching lives in Settings. Adding a function stays on Profile.
 */
class TutorialCopyAndRoutingTest {

    @Test
    fun TUTORIAL_ACTIVE_COPY_USES_SETTINGS_PATH() {
        val catalog = catalogSource()
        assertTrue(catalog.contains("TUTORIAL_SWITCH_PATH"))
        assertFalse(
            "Obsolete Settings switch path must not remain after UX-06",
            catalog.contains("Perfil → Configuración → Usar LeoVer como")
        )
        val switchBodies = listOf(
            TutorialCatalog.definition(TutorialId.T11_USE_LEOVER_AS).steps.last().body
        )
        switchBodies.forEach { body ->
            assertTrue(body.contains(Onb02Copy.TUTORIAL_SWITCH_PATH))
            assertFalse(body.contains("Perfil → Configuración → Usar LeoVer como"))
        }
    }

    @Test
    fun TUTORIAL_ADD_FUNCTION_COPY_USES_PROFILE_ACTION() {
        val catalog = catalogSource()
        assertTrue(catalog.contains("TUTORIAL_ADD_FUNCTION_PATH"))
        val addBodies = listOf(
            TutorialCatalog.definition(TutorialId.T01_PROFILE_PERSONAL).steps.last().body
        )
        addBodies.forEach { body ->
            assertTrue(body.contains(Onb02Copy.TUTORIAL_ADD_FUNCTION_PATH))
        }
        val settings = UiRegressionGateTest.sourceFile(
            "app/src/main/java/com/comunidapp/app/ui/screens/profile/SettingsScreen.kt"
        ).readText()
        assertTrue(settings.contains("Agregar función o perfil"))
        val profile = UiRegressionGateTest.sourceFile(
            "app/src/main/java/com/comunidapp/app/ui/screens/profile/ProfileScreen.kt"
        ).readText()
        assertTrue(profile.contains("Usar LeoVer como"))
        assertFalse(profile.contains("+ Agregar función o perfil"))
    }

    @Test
    fun COMMON_INTRO_COVERS_REQUIRED_STORY() {
        val steps = TutorialCatalog.definition(TutorialId.T00_MULTI_FUNCTION_INTRO).steps
        assertTrue(steps[0].title.contains("Bienvenido a LeoVer"))
        assertTrue(steps[1].titleIsVitacoraWordmark)
        assertTrue(steps[1].body.contains("VitaCora"))
        assertTrue(steps[1].body.contains("Su vida. Su historia. Sus cuidados."))
        assertFalse(steps[1].body.contains("bitácora"))
        val last = steps.last()
        assertTrue(last.title.contains("Elegí cómo querés usar LeoVer"))
        assertTrue(last.body.contains("Persona"))
        assertTrue(last.body.contains("Rescatista independiente"))
        assertTrue(last.body.contains("Refugio / Organización de rescate"))
        assertTrue(last.body.contains("Profesional independiente"))
        assertTrue(last.body.contains("Organización / Negocio"))
        assertTrue(last.highlight.orEmpty().contains("agregar otros perfiles"))
        assertEquals("Empezar", last.primaryCta)
        assertEquals("Tu privacidad en LeoVer", steps[2].title)
        assertEquals("Una comunidad que está cuando hace falta", steps[3].title)
    }

    @Test
    fun FUNCTION_TUTORIALS_SHARE_POLISHED_RENDERER() {
        val audited = listOf(
            TutorialId.T01_PROFILE_PERSONAL,
            TutorialId.T02_RESCUER,
            TutorialId.T03_FOSTER,
            TutorialId.T04_VETERINARY_PROFESSIONAL,
            TutorialId.T05_WALKER,
            TutorialId.T09_DAYCARE,
            TutorialId.T10_ORGANIZATION,
            TutorialId.T11_USE_LEOVER_AS
        )
        audited.forEach { id ->
            val def = TutorialCatalog.definition(id)
            assertTrue(
                "$id must have chips or highlight on every page",
                def.steps.all { it.chips.isNotEmpty() || !it.highlight.isNullOrBlank() }
            )
            assertTrue(
                "$id must not stay on GENERIC-only slides",
                def.steps.any { it.visual != TutorialVisual.GENERIC }
            )
        }
        val pager = UiRegressionGateTest.sourceFile(
            "app/src/main/java/com/comunidapp/app/ui/components/leo/LeoVerTutorialPager.kt"
        ).readText()
        val host = UiRegressionGateTest.sourceFile(
            "app/src/main/java/com/comunidapp/app/ui/screens/onboarding/onb02/Onb02Screens.kt"
        ).readText()
        assertTrue(pager.contains("fun LeoVerTutorialPager("))
        assertTrue(pager.contains("TutorialVisualComposition("))
        assertTrue(pager.contains("TutorialFeatureChip("))
        assertTrue(pager.contains("Omitir"))
        assertTrue(host.contains("LeoVerTutorialPager("))
        assertFalse(host.contains("fun CustomRescuerTutorial"))
    }

    @Test
    fun HELP_REPLAY_RETURNS_TO_LIBRARY_WITHOUT_SETUP() {
        val graph = UiRegressionGateTest.sourceFile(
            "app/src/main/java/com/comunidapp/app/navigation/ComunidappNavGraph.kt"
        ).readText()
        val reopen = graph.substringAfter("route = NavRoutes.ONB02_REOPEN")
            .substringBefore("composable(NavRoutes.USE_LEOVER_AS)")
        assertTrue(reopen.contains("Onb02FlowKind.REOPEN_FROM_HELP"))
        assertTrue(reopen.contains("onFinished = { setupRoute ->"))
        assertTrue(reopen.contains("navController.popBackStack()"))
        assertFalse(reopen.contains("NavRoutes.CREATE_ORGANIZATION"))
        assertFalse(reopen.contains("Onb02FlowKind.FULL_ONBOARDING"))
        assertFalse(reopen.contains("Onb02FlowKind.ADD_FUNCTION_LATER"))
        val help = graph.substringAfter("composable(NavRoutes.HELP_TUTORIALS)")
            .substringBefore("composable(NavRoutes.HOME)")
        assertTrue(help.contains("NavRoutes.onb02Reopen"))
        assertTrue(graph.contains("NavRoutes.HELP_TUTORIALS"))
        assertTrue(NavRoutes.HELP_TUTORIALS.isNotBlank())
    }

    private fun catalogSource(): String = UiRegressionGateTest.sourceFile(
        "app/src/main/java/com/comunidapp/app/domain/onboarding/onb02/TutorialCatalog.kt"
    ).readText()
}
