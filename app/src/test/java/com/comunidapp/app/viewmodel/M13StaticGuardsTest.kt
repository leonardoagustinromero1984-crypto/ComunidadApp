package com.comunidapp.app.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guardas estáticas M13 Bloque 1: exclusiones, sin migración 048, legacy intacto, rutas.
 */
class M13StaticGuardsTest {

    private fun repoRoot(): File = listOf(
        File("."),
        File(".."),
        File("../..")
    ).first { File(it, "supabase/migrations").isDirectory }

    @Test
    fun no_migration_048() {
        val names = File(repoRoot(), "supabase/migrations").listFiles()?.map { it.name }.orEmpty()
        assertFalse(names.any { it.startsWith("048_") })
        assertTrue(names.any { it.startsWith("047_") })
        assertTrue(names.any { it.startsWith("012_") })
    }

    @Test
    fun legacy_lost_found_types_still_present() {
        val root = repoRoot()
        assertTrue(File(root, "app/src/main/java/com/comunidapp/app/data/model/PlatformModels.kt").readText()
            .contains("data class LostFoundSighting"))
        assertTrue(File(root, "app/src/main/java/com/comunidapp/app/ui/screens/lostfound/LostFoundScreen.kt").exists())
        assertTrue(File(root, "app/src/main/java/com/comunidapp/app/data/repository/PlatformRepository.kt").readText()
            .contains("fun addSighting"))
    }

    @Test
    fun m13_routes_registered() {
        val routes = File(repoRoot(), "app/src/main/java/com/comunidapp/app/navigation/NavRoutes.kt").readText()
        assertTrue(routes.contains("M13_SIGHTINGS"))
        assertTrue(routes.contains("m13/sightings"))
        assertTrue(routes.contains("m13/cases/{caseId}/matches"))
        assertTrue(routes.contains("m13/matches/{candidateId}"))
    }

    @Test
    fun no_ai_or_biometric_in_m13_sources() {
        val dir = File(repoRoot(), "app/src/main/java/com/comunidapp/app/data/repository")
        val text = listOf(
            File(dir, "M13MatchingEngine.kt").readText().lowercase(),
            File(dir, "M13Repositories.kt").readText().lowercase()
        ).joinToString("\n")
        assertFalse(text.contains("facial"))
        assertFalse(text.contains("biometric"))
        assertFalse(text.contains("openai"))
        assertFalse(text.contains("tensorflow"))
    }

    @Test
    fun no_service_role_in_m13() {
        val m13Files = File(repoRoot(), "app/src/main/java").walkTopDown()
            .filter { it.isFile && it.name.contains("M13") && it.extension == "kt" }
            .toList()
        assertTrue(m13Files.isNotEmpty())
        m13Files.forEach { file ->
            val lower = file.readText().lowercase()
            assertFalse(file.name, lower.contains("service_role"))
            assertFalse(file.name, lower.contains("service-role"))
        }
    }

    @Test
    fun public_model_has_no_lat_lon_fields() {
        val models = File(
            repoRoot(),
            "app/src/main/java/com/comunidapp/app/data/model/M13SightingModels.kt"
        ).readText()
        val publicBlock = models.substringAfter("data class M13SightingPublic").substringBefore("data class M13MatchCandidate")
        assertFalse(publicBlock.contains("latitude"))
        assertFalse(publicBlock.contains("longitude"))
        assertTrue(publicBlock.contains("hasApproximateLocation"))
    }
}
