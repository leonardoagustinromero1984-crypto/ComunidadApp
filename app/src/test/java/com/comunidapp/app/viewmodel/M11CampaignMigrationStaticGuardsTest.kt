package com.comunidapp.app.viewmodel

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LeoVer M11 — guardas estáticas sobre migración 043 (campañas e insumos).
 * Contratos estructurales sin depender de indentación exacta.
 */
class M11CampaignMigrationStaticGuardsTest {

    private val migration040 = "supabase/migrations/040_m10_foster_homes_core.sql"
    private val migration041 = "supabase/migrations/041_m10_foster_care_management.sql"
    private val migration042 = "supabase/migrations/042_m11_shelter_operations_core.sql"
    private val migration043 = "supabase/migrations/043_m11_shelter_campaigns_and_aid.sql"

    @Test
    fun migration_043_exists_after_042() {
        val names = migrationDir().listFiles()?.map { it.name }?.sorted().orEmpty()
        assertTrue(names.contains("042_m11_shelter_operations_core.sql"))
        assertTrue(names.contains("043_m11_shelter_campaigns_and_aid.sql"))
        val i42 = names.indexOf("042_m11_shelter_operations_core.sql")
        val i43 = names.indexOf("043_m11_shelter_campaigns_and_aid.sql")
        assertTrue(i42 < i43)
    }

    @Test
    fun migration_043_has_block2_tables() {
        val sql = sourceFile(migration043).readText()
        listOf(
            "shelter_campaigns",
            "shelter_campaign_updates",
            "shelter_supply_requests",
            "shelter_supply_contributions"
        ).forEach { assertTrue("missing table $it", sql.contains(it)) }
    }

    @Test
    fun migration_043_enables_rls_drop_policy_before_create() {
        val sql = sourceFile(migration043).readText()
        assertTrue(sql.contains("enable row level security") || sql.contains("ROW LEVEL SECURITY"))
        assertTrue(sql.contains("drop policy if exists"))
        listOf(
            "shelter_campaigns_select_m11",
            "shelter_campaign_updates_select_m11",
            "shelter_supply_requests_select_m11",
            "shelter_supply_contributions_select_m11"
        ).forEach { name ->
            assertTrue("missing policy $name", sql.contains(name))
            assertTrue(
                "missing drop before $name",
                Regex("(?is)drop\\s+policy\\s+if\\s+exists\\s+$name").containsMatchIn(sql)
            )
        }
        assertTrue(sql.contains("with check (false)") || sql.contains("using (false)"))
    }

    @Test
    fun migration_043_has_m11_rpc_functions_and_search_path() {
        val sql = sourceFile(migration043).readText()
        listOf(
            "m11_create_shelter_campaign",
            "m11_update_shelter_campaign",
            "m11_change_shelter_campaign_status",
            "m11_get_shelter_campaign",
            "m11_list_public_shelter_campaigns",
            "m11_list_shelter_campaigns",
            "m11_add_shelter_campaign_update",
            "m11_list_shelter_campaign_updates",
            "m11_create_supply_request",
            "m11_update_supply_request",
            "m11_cancel_supply_request",
            "m11_get_supply_request",
            "m11_list_public_supply_requests",
            "m11_list_shelter_supply_requests",
            "m11_pledge_supply_contribution",
            "m11_cancel_supply_contribution",
            "m11_confirm_supply_contribution",
            "m11_record_supply_receipt",
            "m11_get_supply_contribution",
            "m11_list_supply_contributions"
        ).forEach { assertTrue("missing rpc $it", sql.contains(it)) }
        assertTrue(sql.contains("security definer") || sql.contains("SECURITY DEFINER"))
        assertTrue(sql.contains("search_path"))
    }

    @Test
    fun migration_043_no_drop_table() {
        val sql = sourceFile(migration043).readText().lowercase()
        assertFalse(Regex("(?i)drop\\s+table\\b").containsMatchIn(sql))
    }

    @Test
    fun migration_043_no_monetary_columns() {
        val sql = sourceFile(migration043).readText().lowercase()
        listOf("cbu", "alias bancario", "tarjeta", "mercado pago", "cuenta bancaria").forEach { forbidden ->
            assertFalse("forbidden token as column: $forbidden", sql.contains("$forbidden text"))
            assertFalse("forbidden token as column: $forbidden", sql.contains("$forbidden,"))
        }
    }

    @Test
    fun migration_043_allows_m05_and_file_asset_refs() {
        val sql = sourceFile(migration043).readText()
        assertTrue(sql.contains("m05://") || sql.contains("file_asset:"))
        assertTrue(sql.contains("_m11_is_safe_evidence_ref") || sql.contains("_m11_require_safe_evidence"))
    }

    @Test
    fun migration_043_no_service_role_key_literal() {
        val sql = sourceFile(migration043).readText().lowercase()
        assertFalse(sql.contains("service_role_key"))
    }

    @Test
    fun migration_043_does_not_rewrite_040_041_042_files() {
        val sql043 = sourceFile(migration043).readText()
        assertFalse(sql043.contains("040_m10_foster_homes_core.sql"))
        assertFalse(sql043.contains("041_m10_foster_care_management.sql"))
        assertFalse(sql043.contains("042_m11_shelter_operations_core.sql"))
    }

    @Test
    fun migration_043_does_not_drop_tables_from_040_041_042() {
        val sql043 = sourceFile(migration043).readText()
        listOf(
            "foster_home_profiles",
            "foster_care_requests",
            "foster_placements",
            "foster_expenses",
            "shelter_profiles",
            "shelter_pet_placements",
            "shelter_volunteer_assignments"
        ).forEach { table ->
            assertFalse(
                "043 must not DROP $table",
                Regex("(?i)drop\\s+table\\s+(if\\s+exists\\s+)?public\\.$table\\b")
                    .containsMatchIn(sql043)
            )
        }
    }

    @Test
    fun migrations_040_041_042_still_exist() {
        listOf(migration040, migration041, migration042).forEach { path ->
            assertTrue("$path must exist", sourceFile(path).isFile)
        }
    }

    @Test
    fun migration_043_has_shelter_permissions() {
        val sql = sourceFile(migration043).readText()
        listOf(
            "shelter.campaign.read",
            "shelter.campaign.manage",
            "shelter.supply.read",
            "shelter.supply.manage",
            "shelter.contribution.read",
            "shelter.contribution.manage"
        ).forEach { assertTrue("missing permission $it", sql.contains(it)) }
    }

    @Test
    fun android_m11_campaign_sources_do_not_use_service_role_key() {
        val roots = listOf(
            "app/src/main/java/com/comunidapp/app/data/remote/supabase/m11",
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
                        (it.name.contains("Shelter", ignoreCase = true) ||
                            it.name.contains("M11", ignoreCase = true))
                }
                .forEach { file ->
                    val text = file.readText()
                    assertFalse(
                        "${file.path} must not embed service_role_key",
                        Regex("(?i)service_role_key").containsMatchIn(text)
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
