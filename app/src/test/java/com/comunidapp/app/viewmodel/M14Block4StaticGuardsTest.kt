package com.comunidapp.app.viewmodel

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guardas estáticas M14 Bloque 4: 001–052 intactas, M28 080 presente, sin service_role en cliente, sin push claim.
 */
class M14Block4StaticGuardsTest {

    private fun repoRoot(): File = listOf(File("."), File(".."), File("../.."))
        .first { File(it, "supabase/migrations").isDirectory }

    @Test
    fun migrations_001_to_052_present_and_m28_080() {
        val names = File(repoRoot(), "supabase/migrations").listFiles()?.map { it.name }.orEmpty()
        val nums = names
            .filter { it.matches(Regex("^\\d{3}_.*\\.sql$")) }
            .map { it.substring(0, 3).toInt() }
        assertTrue(nums.contains(50))
        assertTrue(nums.contains(51))
        assertTrue(nums.contains(52))
        assertTrue(nums.contains(80))
        assertTrue(names.any { it == "052_m14_credential_verification_and_public_access.sql" })
        assertTrue(names.any { it.startsWith("080_m28_veterinary_professional_health_management") })
        (1..52).forEach { n ->
            assertTrue(
                "falta migración ${n.toString().padStart(3, '0')}",
                nums.contains(n)
            )
        }
    }

    @Test
    fun block4_sources_have_no_service_role_or_push_claim() {
        val files = listOf(
            "app/src/main/java/com/comunidapp/app/data/repository/M14Block4Operations.kt",
            "app/src/main/java/com/comunidapp/app/data/model/M14PassportModels.kt",
            "app/src/main/java/com/comunidapp/app/viewmodel/M14Block3ViewModels.kt",
            "app/src/main/java/com/comunidapp/app/ui/screens/m14/M14Block3Screens.kt"
        )
        files.forEach { rel ->
            val text = File(repoRoot(), rel).readText().lowercase()
            assertFalse("$rel service_role", text.contains("service_role"))
            assertFalse("$rel push enviado", text.contains("push enviado"))
            assertFalse("$rel iniciar m15", text.contains("iniciar m15"))
        }
    }

    @Test
    fun dataprovider_wires_m14_operations_repo() {
        val text = File(
            repoRoot(),
            "app/src/main/java/com/comunidapp/app/data/provider/DataProvider.kt"
        ).readText()
        assertTrue(text.contains("m14OperationsRepository"))
        assertTrue(text.contains("MockM14OperationsRepository"))
        assertTrue(text.contains("SupabaseM14OperationsRepository"))
    }

    @Test
    fun block4_hardening_suite_exists() {
        val t = File(
            repoRoot(),
            "app/src/test/java/com/comunidapp/app/viewmodel/M14Block4HardeningTest.kt"
        )
        assertTrue(t.exists())
        val text = t.readText()
        assertTrue(text.contains("expiration_pending_request_by_policy"))
        assertTrue(text.contains("metrics_aggregate_without_pii_and_invalid_range"))
        assertTrue(text.contains("REMOTE_VALIDATION_PENDING"))
        assertTrue(text.contains("America/Argentina/Buenos_Aires") || text.contains("zoneIdName"))
    }

    @Test
    fun error_mapper_declares_block4_codes() {
        val text = File(
            repoRoot(),
            "app/src/main/java/com/comunidapp/app/data/remote/supabase/m14/M14ErrorMapper.kt"
        ).readText()
        listOf(
            "EXPIRATION_NOT_ALLOWED",
            "EXPIRATION_ALREADY_APPLIED",
            "METRICS_INVALID_RANGE",
            "PUBLIC_CODE_UNAVAILABLE",
            "HISTORY_UNAVAILABLE",
            "REMOTE_VALIDATION_PENDING",
            "CONFLICT"
        ).forEach { code ->
            assertTrue("missing $code", text.contains(code))
        }
    }

    @Test
    fun no_secrets_in_block4_kotlin() {
        val files = listOf(
            "app/src/main/java/com/comunidapp/app/data/repository/M14Block4Operations.kt",
            "app/src/test/java/com/comunidapp/app/viewmodel/M14Block4HardeningTest.kt"
        )
        files.forEach { rel ->
            val text = File(repoRoot(), rel).readText().lowercase()
            assertFalse("$rel service_role key", text.contains("eyj"))
            assertFalse("$rel supabase service", text.contains("service_role"))
            assertFalse("$rel aws key", text.contains("akia"))
        }
    }
}
