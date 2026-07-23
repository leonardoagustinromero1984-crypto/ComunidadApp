package com.comunidapp.app.viewmodel

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LeoVer M09 — guardas estáticas sobre migraciones 037–039 (listas para apply manual).
 * Comprueba contratos estructurales sin depender del formato exacto del SQL.
 */
class M09MigrationStaticGuardsTest {

    private val migration037 = "supabase/migrations/037_m09_adoption_publications.sql"
    private val migration038 = "supabase/migrations/038_m09_adoption_applications.sql"
    private val migration039 = "supabase/migrations/039_m09_adoption_completion_followup.sql"

    @Test
    fun migration_files_exist_in_required_order_and_names() {
        val dir = migrationDir()
        val names = dir.listFiles()
            ?.map { it.name }
            ?.sorted()
            .orEmpty()
        assertTrue(names.contains("037_m09_adoption_publications.sql"))
        assertTrue(names.contains("038_m09_adoption_applications.sql"))
        assertTrue(names.contains("039_m09_adoption_completion_followup.sql"))
        val i37 = names.indexOf("037_m09_adoption_publications.sql")
        val i38 = names.indexOf("038_m09_adoption_applications.sql")
        val i39 = names.indexOf("039_m09_adoption_completion_followup.sql")
        assertTrue(i37 < i38 && i38 < i39)
    }

    @Test
    fun migration_037_has_adoptions_table_rls_and_core_rpcs() {
        val sql = sourceFile(migration037).readText()
        assertTrue(sql.contains("adoptions"))
        assertTrue(sql.contains("enable row level security") || sql.contains("ROW LEVEL SECURITY"))
        assertTrue(sql.contains("with check (false)") || sql.contains("using (false)"))
        listOf(
            "m09_create_adoption_publication",
            "m09_update_adoption_publication",
            "m09_set_adoption_status",
            "m09_list_published_adoptions",
            "m09_get_adoption"
        ).forEach { assertTrue("missing $it", sql.contains(it)) }
        assertTrue(sql.contains("security definer") || sql.contains("SECURITY DEFINER"))
        assertTrue(sql.contains("search_path"))
        assertFalse(sql.lowercase().contains("service_role_key"))
    }

    @Test
    fun migration_038_depends_on_adoptions_and_blocks_direct_writes() {
        val sql = sourceFile(migration038).readText()
        assertTrue(sql.contains("adoption_applications"))
        assertTrue(sql.contains("references public.adoptions") || sql.contains("references adoptions"))
        assertTrue(sql.contains("enable row level security") || sql.contains("ROW LEVEL SECURITY"))
        assertTrue(sql.contains("with check (false)") || sql.contains("using (false)"))
        listOf(
            "m09_submit_application",
            "m09_accept_application",
            "m09_reject_application",
            "m09_withdraw_application",
            "m09_list_my_applications",
            "m09_list_received_applications"
        ).forEach { assertTrue("missing $it", sql.contains(it)) }
        assertTrue(sql.contains("PAUSED"))
        assertTrue(sql.contains("ACCEPTED"))
        assertFalse(sql.lowercase().contains("service_role_key"))
    }

    @Test
    fun migration_039_has_completion_tables_rls_finalize_and_followup() {
        val sql = sourceFile(migration039).readText()
        listOf(
            "adoption_interviews",
            "adoption_document_requirements",
            "adoption_agreements",
            "adoption_finalizations",
            "adoption_followup_plans",
            "adoption_followup_checks"
        ).forEach { assertTrue("missing table $it", sql.contains(it)) }
        assertTrue(sql.contains("enable row level security") || sql.contains("ROW LEVEL SECURITY"))
        assertTrue(sql.contains("with check (false)") || sql.contains("using (false)"))
        listOf(
            "m09_schedule_interview",
            "m09_confirm_interview",
            "m09_complete_interview",
            "m09_cancel_interview",
            "m09_request_document",
            "m09_submit_document_reference",
            "m09_review_document",
            "m09_create_adoption_agreement",
            "m09_accept_adoption_agreement",
            "m09_finalize_adoption",
            "m09_list_followup_plans",
            "m09_get_followup_plan",
            "m09_complete_followup_check"
        ).forEach { assertTrue("missing rpc $it", sql.contains(it)) }
        assertTrue(sql.contains("m09_finalize_adoption"))
        assertTrue(sql.contains("PRINCIPAL"))
        assertTrue(sql.contains("pet_status_history") || sql.contains("pet_status_history"))
        assertTrue(sql.contains("interval '7 days'"))
        assertTrue(sql.contains("interval '30 days'"))
        assertTrue(sql.contains("interval '90 days'"))
        assertTrue(sql.contains("object/public/leover") || sql.contains("leover"))
        assertTrue(sql.contains("DOCUMENT_UNSAFE_REFERENCE") || sql.contains("adoption_docs_no_public_leover"))
        assertTrue(sql.contains("ADOPTION_USE_FINALIZE"))
        assertFalse(sql.lowercase().contains("service_role_key"))
    }

    @Test
    fun migration_039_does_not_rewrite_037_or_038_files() {
        val sql = sourceFile(migration039).readText()
        assertFalse(sql.contains("037_m09_adoption_publications.sql"))
        assertFalse(sql.contains("038_m09_adoption_applications.sql"))
        // May create/replace helpers from earlier M09, but must not drop core 037/038 tables.
        assertFalse(Regex("(?i)drop\\s+table\\s+(if\\s+exists\\s+)?public\\.adoptions\\b")
            .containsMatchIn(sql))
        assertFalse(Regex("(?i)drop\\s+table\\s+(if\\s+exists\\s+)?public\\.adoption_applications\\b")
            .containsMatchIn(sql))
    }

    @Test
    fun android_m09_sources_do_not_use_service_role() {
        val roots = listOf(
            "app/src/main/java/com/comunidapp/app/data/remote/supabase/m09",
            "app/src/main/java/com/comunidapp/app/data/repository",
            "app/src/main/java/com/comunidapp/app/viewmodel"
        )
        roots.forEach { root ->
            val dir = File(root).takeIf { it.isDirectory }
                ?: File(System.getProperty("user.dir"), root)
            if (!dir.isDirectory) return@forEach
            dir.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".kt") && it.name.contains("Adoption", ignoreCase = true) }
                .forEach { file ->
                    val text = file.readText()
                    assertFalse(
                        "${file.path} must not embed service_role",
                        Regex("(?i)service_role").containsMatchIn(text)
                    )
                }
        }
    }

    private fun migrationDir(): File {
        val candidates = listOf(
            File("supabase/migrations"),
            File("../supabase/migrations"),
            File("../../supabase/migrations"),
            File(System.getProperty("user.dir"), "supabase/migrations"),
            File(System.getProperty("user.dir"), "../supabase/migrations")
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("supabase/migrations not found. cwd=${System.getProperty("user.dir")}")
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
