package com.comunidapp.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * UI-01 static regression gate.
 *
 * Detects known LeoVer visual/UX regressions in production source.
 * No emulator. No screenshots. Fast enough for Leonardo's machine.
 *
 * Future screenshot/golden tests can live under ui/golden/ in CI (macOS/Linux)
 * without changing this gate.
 */
class UiRegressionGateTest {

    @Test
    fun noLegacyFullScreenCreamHexInProductionUi() {
        val hits = productionUiFiles()
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (line.contains("0xFFFFF6EA") || line.contains("#FFF6EA")) {
                        "${file.relativeTo(repoRoot())}:${index + 1}: $line"
                    } else {
                        null
                    }
                }
            }
        assertTrue("Legacy cream hex #FFF6EA:\n${hits.joinToString("\n")}", hits.isEmpty())
    }

    @Test
    fun screensDoNotUseDeprecatedCommunityTiles() {
        val hits = screenFiles().filter { it.readText().contains("LeoServiceTile(") }
        assertTrue(
            "Comunidad must not regress to LeoServiceTile:\n${hits.joinToString { it.path }}",
            hits.isEmpty()
        )
    }

    @Test
    fun screensDoNotExposeRawBackendErrors() {
        val banned = listOf("SQLSTATE", "duplicate key", "PostgREST", "pgrst", "organizations_slug")
        val hits = screenFiles().flatMap { file ->
            val text = file.readText()
            banned.mapNotNull { needle ->
                if (text.contains(needle, ignoreCase = true)) {
                    "$needle in ${file.relativeTo(repoRoot())}"
                } else {
                    null
                }
            }
        }
        assertTrue("Technical strings in UI screens:\n${hits.joinToString("\n")}", hits.isEmpty())
    }

    @Test
    fun screensDoNotCallAccountTypeDropdown() {
        val hits = screenFiles().filter { it.readText().contains("AccountTypeDropdown(") }
        assertTrue("AccountTypeDropdown leaked into screens:\n${hits.joinToString { it.path }}", hits.isEmpty())
    }

    @Test
    fun screensDoNotUseRuntimeMockData() {
        val hits = screenFiles().filter { it.readText().contains("MockData.") }
        assertTrue("MockData fallback in screens:\n${hits.joinToString { it.path }}", hits.isEmpty())
    }

    @Test
    fun migratedCommunityCannotImportLegacyUi() {
        val screen = source("app/src/main/java/com/comunidapp/app/ui/screens/comunidad/ComunidadScreen.kt")
        assertFalse(screen.contains("import com.comunidapp.app.ui.legacy"))
        assertFalse(screen.contains("LeoServiceTile"))
        assertFalse(screen.contains("BrandCream"))
        assertFalse(screen.contains("BrandOrangeContainer"))
        assertFalse(screen.contains("FilterChipDefaults"))
        assertFalse(screen.contains("AssistChip"))
        assertFalse(screen.contains("0xFFFFF6EA"))
        assertFalse(screen.contains("0xFF74AD7F"))
        assertFalse(screen.contains("0xFF6D9FA1"))
        assertTrue(screen.contains("import com.comunidapp.app.ui.components.leo.LeoFilterChip"))
        assertTrue(screen.contains("import com.comunidapp.app.ui.components.leo.LeoVerProviderCard"))
        assertTrue(screen.contains("containerColor = visual.background"))
    }

    @Test
    fun communityCanonicalLayoutIsProtected() {
        val screen = source("app/src/main/java/com/comunidapp/app/ui/screens/comunidad/ComunidadScreen.kt")
        val card = source("app/src/main/java/com/comunidapp/app/ui/components/leo/LeoVerProviderCard.kt")
        assertTrue(screen.contains("Servicios para tu mascota"))
        assertTrue(screen.contains("Veterinarias"))
        assertTrue(screen.contains("Guarderías"))
        assertTrue(screen.contains("Peluquerías"))
        assertTrue(screen.contains("Cerca mío"))
        assertTrue(screen.contains("LeoVerProviderCard"))
        assertTrue(screen.contains("VisualDirectionPilot"))
        assertTrue(screen.contains("containerColor = visual.background"))
        assertTrue(screen.contains("LeoFilterChip"))
        assertTrue(screen.contains("item(key = \"categories\")"))
        assertTrue(screen.contains("Elegí un servicio para encontrar opciones cerca tuyo."))
        assertFalse(screen.contains("LeoServiceTile"))
        assertFalse(screen.contains("0xFF74AD7F"))
        assertFalse(screen.contains("0xFF6D9FA1"))
        assertTrue(card.contains("Ver perfil"))
        assertTrue(card.contains("LeoVerProviderCard"))
    }

    @Test
    fun tutorialCanonicalRendererIsThePager() {
        val pager = source("app/src/main/java/com/comunidapp/app/ui/components/leo/LeoVerTutorialPager.kt")
        val host = source("app/src/main/java/com/comunidapp/app/ui/screens/onboarding/onb02/Onb02Screens.kt")
        assertTrue(pager.contains("fun LeoVerTutorialPager("))
        assertTrue(pager.contains("containerColor = visual.background"))
        assertTrue(pager.contains("LeoPrimaryButton"))
        assertTrue(host.contains("LeoVerTutorialPager("))
        assertTrue(host.contains("VisualDirectionPilot"))
        assertTrue(host.contains("fun TutorialPagerScreen("))
        assertTrue(host.contains("= LeoVerTutorialPager("))
    }

    @Test
    fun homeIsSocialFirstWithoutOperationalShortcuts() {
        val screen = source("app/src/main/java/com/comunidapp/app/ui/screens/home/HomeScreen.kt")
        assertTrue(screen.contains("StoriesRow("))
        assertTrue(screen.contains("LeoSocialPostCard("))
        assertTrue(screen.contains("VisualDirectionPilot"))
        assertFalse(screen.contains("Perdí mi mascota"))
        assertFalse(screen.contains("Encontré un animal"))
        assertFalse(screen.contains("Agregar mascota"))
        assertFalse(screen.contains("V2UrgentActionRow"))
    }

    @Test
    fun settingsUsesCanonicalGroupedRows() {
        val settings = source("app/src/main/java/com/comunidapp/app/ui/screens/profile/SettingsScreen.kt")
        assertTrue(settings.contains("VisualDirectionPilot"))
        assertTrue(settings.contains("LeoVerSettingsSection"))
        assertTrue(settings.contains("Agregar función o perfil"))
        assertFalse(settings.contains("BrandCream"))
        val profile = source("app/src/main/java/com/comunidapp/app/ui/screens/profile/ProfileScreen.kt")
        assertTrue(profile.contains("Usar LeoVer como"))
        assertFalse(profile.contains("+ Agregar función o perfil"))
    }

    @Test
    fun loginBrandTaglineIsPetLoversAndInstitutionalSloganIsPreserved() {
        val strings = source("app/src/main/res/values/strings.xml")
        val login = source("app/src/main/java/com/comunidapp/app/ui/screens/login/LoginScreen.kt")
        val onboarding = source("app/src/main/java/com/comunidapp/app/ui/screens/onboarding/FirstRunOnboardingScreen.kt")
        assertTrue(strings.contains("name=\"brand_tagline\">La comunidad de los Pet Lovers<"))
        assertTrue(strings.contains("name=\"brand_slogan\">Conectamos mascotas, personas y comunidad.<"))
        assertTrue(login.contains("R.string.brand_tagline"))
        assertEquals(1, Regex("R\\.string\\.brand_tagline").findAll(login).count())
        assertFalse(login.contains("R.string.brand_slogan"))
        assertTrue(onboarding.contains("R.string.brand_slogan"))
        assertFalse(onboarding.contains("R.string.brand_tagline"))
    }

    @Test
    fun colorDirectionV3DoesNotRestoreSageTealPrimaries() {
        val visual = source("app/src/main/java/com/comunidapp/app/ui/theme/VisualDirectionV2.kt")
        assertTrue(visual.contains("v3Experimental"))
        assertTrue(visual.contains("0xFF49B749"))
        assertTrue(visual.contains("0xFFFAFBF8"))
        assertFalse(visual.contains("0xFF74AD7F"))
        assertFalse(visual.contains("0xFF6D9FA1"))
        assertFalse(visual.contains("v2Pilot"))
    }

    @Test
    fun topBarDoesNotUseCreamAsFullScreenFill() {
        val bar = source("app/src/main/java/com/comunidapp/app/ui/components/leo/LeoComponents.kt")
        val v2 = source("app/src/main/java/com/comunidapp/app/ui/components/v2/V2Foundation.kt")
        assertTrue(bar.contains(".background(leoVisual().background)"))
        assertFalse(
            bar.substringAfter("fun LeoTopAppBar(").substringBefore("fun LeoSectionHeader(")
                .contains("BrandCream")
        )
        assertTrue(v2.contains("val V2ScreenBackground = BrandBackground"))
        assertFalse(v2.contains("val V2ScreenBackground = BrandCream"))
    }

    private fun productionUiFiles(): List<File> {
        val ui = File(repoRoot(), "app/src/main/java/com/comunidapp/app/ui")
        return ui.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    private fun screenFiles(): List<File> {
        val screens = File(repoRoot(), "app/src/main/java/com/comunidapp/app/ui/screens")
        return screens.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    private fun source(relative: String): String = sourceFile(relative).readText()

    companion object {
        fun repoRoot(): File {
            val cwd = File(System.getProperty("user.dir"))
            val candidates = listOf(
                cwd,
                cwd.parentFile,
                cwd.parentFile?.parentFile,
                File(".").canonicalFile
            )
            return candidates.firstOrNull { File(it, "app/src/main/java/com/comunidapp/app/ui").isDirectory }
                ?: error("repo root not found from cwd=$cwd")
        }

        fun sourceFile(relativePath: String): File {
            val root = repoRoot()
            val file = File(root, relativePath)
            require(file.isFile) { "$relativePath not found under $root" }
            return file
        }
    }
}
