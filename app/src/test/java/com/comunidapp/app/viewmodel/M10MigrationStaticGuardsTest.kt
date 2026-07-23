package com.comunidapp.app.viewmodel

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LeoVer M10 — guardas estáticas sobre migraciones 040–041 (listas para apply manual).
 * Contratos estructurales sin depender de indentación exacta.
 */
class M10MigrationStaticGuardsTest {

    private val migration040 = "supabase/migrations/040_m10_foster_homes_core.sql"
    private val migration041 = "supabase/migrations/041_m10_foster_care_management.sql"

    @Test
    fun migration_files_exist_in_order_040_then_041() {
        val names = migrationDir().listFiles()?.map { it.name }?.sorted().orEmpty()
        assertTrue(names.contains("040_m10_foster_homes_core.sql"))
        assertTrue(names.contains("041_m10_foster_care_management.sql"))
        val i40 = names.indexOf("040_m10_foster_homes_core.sql")
        val i41 = names.indexOf("041_m10_foster_care_management.sql")
        assertTrue(i40 < i41)
    }

    @Test
    fun migration_040_has_core_tables_foster_care_requests_not_new_foster_requests_table() {
        val sql = sourceFile(migration040).readText()
        listOf(
            "foster_home_profiles",
            "foster_care_requests",
            "foster_placements"
        ).forEach { assertTrue("missing $it", sql.contains(it)) }
        assertTrue(sql.contains("foster_care_requests"))
        // Must not CREATE a new M10 table named foster_requests (legacy 011 stays separate).
        assertFalse(
            Regex("(?i)create\\s+table\\s+(if\\s+not\\s+exists\\s+)?public\\.foster_requests\\b")
                .containsMatchIn(sql)
        )
    }

    @Test
    fun migration_040_enables_rls_drop_policy_before_create_and_denies_direct_writes() {
        val sql = sourceFile(migration040).readText()
        assertTrue(sql.contains("enable row level security") || sql.contains("ROW LEVEL SECURITY"))
        assertTrue(sql.contains("drop policy if exists"))
        assertTrue(sql.contains("with check (false)") || sql.contains("using (false)"))
        listOf(
            "foster_home_profiles_select_m10",
            "foster_care_requests_select_m10",
            "foster_placements_select_m10"
        ).forEach { name ->
            assertTrue("missing policy $name", sql.contains(name))
            assertTrue(
                "missing drop before $name",
                Regex("(?is)drop\\s+policy\\s+if\\s+exists\\s+$name").containsMatchIn(sql)
            )
        }
    }

    @Test
    fun migration_040_has_start_placement_temporary_custodian_capacity_and_no_principal_delete() {
        val sql = sourceFile(migration040).readText()
        listOf(
            "m10_create_foster_home",
            "m10_submit_foster_request",
            "m10_accept_foster_request",
            "m10_start_foster_placement",
            "m10_list_active_foster_placements"
        ).forEach { assertTrue("missing $it", sql.contains(it)) }
        assertTrue(sql.contains("m10_start_foster_placement"))
        assertTrue(sql.contains("TEMPORARY_CUSTODIAN"))
        assertTrue(sql.contains("total_capacity") || sql.contains("FOSTER_PLACEMENT_CAPACITY"))
        assertTrue(
            sql.contains("FOSTER_HOME_FULL") ||
                sql.contains("FOSTER_PLACEMENT_CAPACITY_EXCEEDED") ||
                sql.contains("current_occupancy")
        )
        assertFalse(
            Regex("(?is)delete\\s+from\\s+public\\.pet_responsibilities[^;]*PRINCIPAL")
                .containsMatchIn(sql)
        )
        assertFalse(
            Regex("(?is)update\\s+public\\.pet_responsibilities[^;]*role_code\\s*=\\s*'PRINCIPAL'[^;]*status\\s*=\\s*'REVOKED'")
                .containsMatchIn(sql)
        )
        assertTrue(sql.contains("security definer") || sql.contains("SECURITY DEFINER"))
        assertTrue(sql.contains("search_path"))
        assertFalse(sql.lowercase().contains("service_role_key"))
    }

    @Test
    fun migration_041_has_care_tables_rls_complete_cancel_and_private_receipts() {
        val sql = sourceFile(migration041).readText()
        listOf(
            "foster_expenses",
            "foster_evolution_entries",
            "foster_help_requests",
            "foster_help_contributions"
        ).forEach { assertTrue("missing $it", sql.contains(it)) }
        assertTrue(sql.contains("enable row level security") || sql.contains("ROW LEVEL SECURITY"))
        assertTrue(sql.contains("drop policy if exists"))
        assertTrue(sql.contains("with check (false)") || sql.contains("using (false)"))
        listOf(
            "m10_add_foster_expense",
            "m10_list_foster_expenses",
            "m10_add_foster_evolution",
            "m10_list_foster_evolution",
            "m10_create_help_request",
            "m10_update_help_request_status",
            "m10_list_help_requests",
            "m10_record_help_contribution",
            "m10_cancel_foster_placement",
            "m10_complete_foster_placement",
            "m10_list_foster_history"
        ).forEach { assertTrue("missing $it", sql.contains(it)) }
        assertTrue(sql.contains("TEMPORARY_CUSTODIAN"))
        assertTrue(sql.contains("REVOKED") || sql.contains("revoked"))
        assertTrue(sql.contains("m05://") || sql.contains("file_asset:"))
        assertTrue(sql.contains("object/public/leover") || sql.contains("leover"))
        assertFalse(
            Regex("(?is)delete\\s+from\\s+public\\.pet_responsibilities[^;]*PRINCIPAL")
                .containsMatchIn(sql)
        )
        assertFalse(sql.lowercase().contains("service_role_key"))
        assertFalse(
            Regex("(?i)create\\s+table\\s+(if\\s+not\\s+exists\\s+)?public\\.foster_requests\\b")
                .containsMatchIn(sql)
        )
    }

    @Test
    fun migration_041_does_not_rewrite_040_file_content() {
        val sql041 = sourceFile(migration041).readText()
        assertFalse(sql041.contains("040_m10_foster_homes_core.sql"))
        assertFalse(
            Regex("(?i)drop\\s+table\\s+(if\\s+exists\\s+)?public\\.foster_home_profiles\\b")
                .containsMatchIn(sql041)
        )
        assertFalse(
            Regex("(?i)drop\\s+table\\s+(if\\s+exists\\s+)?public\\.foster_care_requests\\b")
                .containsMatchIn(sql041)
        )
        assertFalse(
            Regex("(?i)drop\\s+table\\s+(if\\s+exists\\s+)?public\\.foster_placements\\b")
                .containsMatchIn(sql041)
        )
        // 041 may recreate helper function; must still reference foster_care_requests / placements from 040.
        assertTrue(sql041.contains("foster_placements"))
    }

    @Test
    fun availability_rule_uses_used_slots_not_free_le_1() {
        val sql040 = sourceFile(migration040).readText()
        val sql041 = sourceFile(migration041).readText()
        // New rule: used (occupancy+reserved) thresholds — not the old "free <= 1" LIMITED quirk.
        assertFalse(
            Regex("(?is)_m10_recompute_availability[\\s\\S]{0,400}<=\\s*1\\s+then\\s+'LIMITED'")
                .containsMatchIn(sql040)
        )
        assertTrue(sql040.contains("'AVAILABLE'"))
        assertTrue(sql041.contains("_m10_recompute_availability"))
    }

    @Test
    fun android_m10_sources_do_not_use_service_role() {
        val roots = listOf(
            "app/src/main/java/com/comunidapp/app/data/remote/supabase/m10",
            "app/src/main/java/com/comunidapp/app/data/repository",
            "app/src/main/java/com/comunidapp/app/viewmodel"
        )
        roots.forEach { root ->
            val dir = File(root).takeIf { it.isDirectory }
                ?: File(System.getProperty("user.dir"), root)
            if (!dir.isDirectory) return@forEach
            dir.walkTopDown()
                .filter {
                    it.isFile && it.name.endsWith(".kt") &&
                        (it.name.contains("Foster", ignoreCase = true) ||
                            it.name.contains("M10", ignoreCase = true))
                }
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
