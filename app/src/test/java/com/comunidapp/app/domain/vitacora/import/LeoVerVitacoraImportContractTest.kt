package com.comunidapp.app.domain.vitacora.import

import com.comunidapp.app.domain.onboarding.onb02.TutorialCatalog
import com.comunidapp.app.domain.onboarding.onb02.TutorialId
import com.comunidapp.app.navigation.NavRoutes
import com.comunidapp.app.ui.UiRegressionGateTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LeoVerVitacoraImportContractTest {

    @Test
    fun copyAndNavigationUseVitaCoraAndImportEntryPoints() {
        assertEquals("Importar mascotas", VitacoraImportCopy.TITLE)
        assertEquals("Descargar plantilla Excel", VitacoraImportCopy.DOWNLOAD)
        assertEquals("Ver cómo completar la plantilla", VitacoraImportCopy.HOW_TO)
        assertEquals("Seleccionar archivo", VitacoraImportCopy.SELECT_FILE)
        assertEquals("Solicitar carga asistida", VitacoraImportCopy.ASSISTED_CTA)
        assertTrue(VitacoraImportCopy.CREATE_N.contains("VitaCora"))
        assertEquals("Importación completada", VitacoraImportCopy.COMPLETED)
        assertEquals("Necesita foto", VitacoraImportCopy.NEEDS_PHOTO)
        assertTrue(NavRoutes.VITACORA_IMPORT.contains("vitacora_import"))
        assertEquals("admin_vitacora_imports", NavRoutes.ADMIN_VITACORA_IMPORTS)
        val screens = UiRegressionGateTest.sourceFile(
            "app/src/main/java/com/comunidapp/app/ui/screens/shelters/ShelterOperationsScreens.kt"
        ).readText()
        assertTrue(screens.contains("Importar mascotas"))
        val admin = UiRegressionGateTest.sourceFile(
            "app/src/main/java/com/comunidapp/app/ui/screens/admin/PlatformAdminScreen.kt"
        ).readText()
        assertTrue(admin.contains("Importaciones"))
        assertFalse(screens.contains("Leover"))
    }

    @Test
    fun tutorialT20ExistsAndUsesExistingPager() {
        val def = TutorialCatalog.definition(TutorialId.T20_VITACORA_IMPORT)
        assertEquals(11, def.steps.size)
        assertTrue(def.steps.first().title.contains("plantilla"))
        assertTrue(def.steps.any { it.body.contains("VitaCora") })
        assertFalse(def.steps.any { it.body.contains("Próximamente") })
        val pager = UiRegressionGateTest.sourceFile(
            "app/src/main/java/com/comunidapp/app/ui/screens/vitacora/VitacoraImportScreens.kt"
        ).readText()
        assertTrue(pager.contains("LeoVerTutorialPager"))
        assertTrue(pager.contains("T20_VITACORA_IMPORT"))
    }

    @Test
    fun singleServerEngineAndLimits() {
        assertEquals("LEOVER_VITACORA_IMPORT", VitacoraImportPolicy.TEMPLATE_TYPE)
        assertEquals(1, VitacoraImportPolicy.TEMPLATE_VERSION)
        assertEquals(500, VitacoraImportPolicy.MAX_ROWS)
        assertEquals(5L * 1024L * 1024L, VitacoraImportPolicy.MAX_FILE_SIZE_BYTES)
        assertEquals("org.pets.import", VitacoraImportPolicy.ORG_IMPORT_PERMISSION)
        assertTrue(VitacoraImportPolicy.NO_AUTO_ADOPTION_PUBLICATION)
        assertTrue(VitacoraImportPolicy.NO_FAKE_MEDICAL_EVENTS)
        val engine = UiRegressionGateTest.sourceFile(
            "app/src/main/java/com/comunidapp/app/domain/vitacora/import/VitacoraImportEngine.kt"
        ).readText()
        assertTrue(engine.contains("SELF_SERVICE"))
        assertTrue(engine.contains("ASSISTED"))
        assertTrue(engine.contains("ADMIN"))
        val sql = UiRegressionGateTest.sourceFile(
            "infra/supabase-canonical/supabase/migrations/20260818220000_1036_vitacora_public_number_bulk_import.sql"
        ).readText()
        assertTrue(sql.contains("vitacora_public_number_seq"))
        assertTrue(sql.contains("canon_import_confirm"))
        assertFalse(sql.contains("MAX(public_vitacora_number)"))
        assertFalse(sql.contains("COUNT(*) + 1"))
    }

    @Test
    fun writesAndValidatesCanonicalTemplateArtifact() {
        val bytes = VitacoraImportXlsx.writeTemplate()
        val readme = UiRegressionGateTest.sourceFile(
            "internal/templates/vitacora-import/v1/README.md"
        )
        val internal = File(readme.parentFile, VitacoraImportPolicy.PUBLISHED_FILENAME)
        internal.writeBytes(bytes)
        val music = UiRegressionGateTest.sourceFile(
            "app/src/main/assets/leover_music/leover_music_catalog.json"
        )
        val assetsDir = File(music.parentFile.parentFile, "vitacora_import")
        assetsDir.mkdirs()
        val assets = File(assetsDir, VitacoraImportPolicy.PUBLISHED_FILENAME)
        assets.writeBytes(bytes)
        assertTrue(internal.exists())
        assertTrue(assets.exists())
        val inspected = VitacoraImportXlsx.inspect(internal.readBytes(), VitacoraImportPolicy.PUBLISHED_FILENAME)
        assertEquals(VitacoraImportPolicy.TEMPLATE_TYPE, inspected.templateType)
        assertEquals(1, inspected.templateVersion)
        assertTrue(readme.exists())
        assertFalse(VitacoraImportColumns.DOWNLOAD_HEADERS.containsValue("País"))
        assertTrue(VitacoraImportColumns.HEADERS.containsValue("País"))
    }
}
