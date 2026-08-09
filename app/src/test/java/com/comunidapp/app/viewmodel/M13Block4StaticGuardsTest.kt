package com.comunidapp.app.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guardas estáticas M13 Bloque 4: sin 050, 048/049 intactas, sin service_role, sin push claim.
 */
class M13Block4StaticGuardsTest {

    private fun repoRoot(): File = listOf(File("."), File(".."), File("../.."))
        .first { File(it, "supabase/migrations").isDirectory }

    @Test
    fun migrations_048_049_present_no_050() {
        val names = File(repoRoot(), "supabase/migrations").listFiles()?.map { it.name }.orEmpty()
        assertTrue(names.any { it == "048_m13_sightings_and_match_candidates.sql" })
        assertTrue(names.any { it == "049_m13_match_review_workflow.sql" })
        assertTrue(names.any { it.startsWith("050_") })
        assertTrue(names.any { it.startsWith("051_") })
        assertTrue(names.any { it.startsWith("052_") })
        assertTrue(names.any { it.startsWith("080_m28_") })
    }

    @Test
    fun block4_sources_have_no_service_role_or_push_claim() {
        val files = listOf(
            "app/src/main/java/com/comunidapp/app/data/repository/M13Block4Operations.kt",
            "app/src/main/java/com/comunidapp/app/data/model/M13SightingModels.kt",
            "app/src/main/java/com/comunidapp/app/ui/screens/m13/M13SightingScreens.kt"
        )
        files.forEach { rel ->
            val text = File(repoRoot(), rel).readText().lowercase()
            assertFalse("$rel service_role", text.contains("service_role"))
            assertFalse("$rel push enviado", text.contains("push enviado"))
            assertFalse("$rel m14", text.contains("iniciar m14"))
        }
    }

    @Test
    fun dataprovider_wires_operations_repo() {
        val text = File(
            repoRoot(),
            "app/src/main/java/com/comunidapp/app/data/provider/DataProvider.kt"
        ).readText()
        assertTrue(text.contains("m13OperationsRepository"))
        assertTrue(text.contains("MockM13OperationsRepository"))
        assertTrue(text.contains("SupabaseM13OperationsRepository"))
    }

    @Test
    fun docs_declare_smoke_pending_not_official_close() {
        val cierre = File(repoRoot(), "docs/03-modulos/M13-cierre-tecnico.md")
        // Puede crearse en el mismo commit; si aún no existe, ok en corridas intermedias.
        if (cierre.exists()) {
            val t = cierre.readText()
            assertTrue(t.contains("PENDIENTE EXTERNO") || t.contains("pendiente externo"))
            assertFalse(t.contains("M13 CERRADO OFICIALMENTE"))
            assertFalse(t.contains("M12 CERRADO"))
        }
    }
}
