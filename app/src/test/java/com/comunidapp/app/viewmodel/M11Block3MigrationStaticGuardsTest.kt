package com.comunidapp.app.viewmodel

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LeoVer M11 — guardas estáticas sobre migración 045 (urgencias, eventos y reportes).
 * Contratos estructurales sin depender de indentación exacta.
 */
class M11Block3MigrationStaticGuardsTest {

    private val migration040 = "supabase/migrations/040_m10_foster_homes_core.sql"
    private val migration041 = "supabase/migrations/041_m10_foster_care_management.sql"
    private val migration042 = "supabase/migrations/042_m11_shelter_operations_core.sql"
    private val migration043 = "supabase/migrations/043_m11_shelter_campaigns_and_aid.sql"
    private val migration044 = "supabase/migrations/044_m11_harden_campaign_aid_permissions.sql"
    private val migration045 = "supabase/migrations/045_m11_shelter_emergencies_events_reports.sql"

    private val tables = listOf(
        "shelter_emergencies",
        "shelter_events",
        "shelter_event_registrations"
    )

    private val clientRpcs = listOf(
        "m11_create_shelter_emergency(uuid,text,text,text,text,text,uuid,timestamptz,timestamptz,text,boolean)",
        "m11_update_shelter_emergency(uuid,text,text,text,text,text,uuid,timestamptz,timestamptz,text)",
        "m11_change_shelter_emergency_status(uuid,text)",
        "m11_resolve_shelter_emergency(uuid,text,text)",
        "m11_get_shelter_emergency(uuid)",
        "m11_list_public_shelter_emergencies()",
        "m11_list_shelter_emergencies(uuid)",
        "m11_create_shelter_event(uuid,text,text,text,text,timestamptz,timestamptz,integer,text,text,text,boolean)",
        "m11_update_shelter_event(uuid,text,text,text,text,timestamptz,timestamptz,integer,text,text,text)",
        "m11_change_shelter_event_status(uuid,text)",
        "m11_get_shelter_event(uuid)",
        "m11_list_public_shelter_events()",
        "m11_list_shelter_events(uuid)",
        "m11_register_shelter_event(uuid,text)",
        "m11_cancel_shelter_event_registration(uuid)",
        "m11_mark_shelter_event_attendance(uuid,boolean)",
        "m11_list_shelter_event_registrations(uuid)",
        "m11_get_shelter_operational_summary(uuid,timestamptz,timestamptz,boolean)",
        "m11_get_shelter_capacity_metrics(uuid)",
        "m11_get_shelter_pet_metrics(uuid,timestamptz,timestamptz)",
        "m11_get_shelter_volunteer_metrics(uuid,timestamptz,timestamptz)",
        "m11_get_shelter_campaign_metrics(uuid,timestamptz,timestamptz)",
        "m11_get_shelter_supply_metrics(uuid,timestamptz,timestamptz)",
        "m11_get_shelter_emergency_metrics(uuid,timestamptz,timestamptz)",
        "m11_get_shelter_event_metrics(uuid,timestamptz,timestamptz)"
    )

    private val helpers = listOf(
        "_m11_sync_event_registration_counts(uuid)",
        "_m11_expire_shelter_emergencies(uuid)",
        "_m11_require_shelter_report_range(timestamptz,timestamptz)",
        "_m11_require_shelter_report_read(uuid)",
        "_m11_validate_emergency_pet(uuid, uuid)"
    )

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

    private fun normalizeWs(s: String): String =
        s.lowercase().replace(Regex("\\s+"), " ")

    private fun sql045(): String = sourceFile(migration045).readText()

    @Test
    fun migration_045_exists_after_044() {
        val names = migrationDir().listFiles()?.map { it.name }?.sorted().orEmpty()
        assertTrue(names.contains("044_m11_harden_campaign_aid_permissions.sql"))
        assertTrue(names.contains("045_m11_shelter_emergencies_events_reports.sql"))
        val i44 = names.indexOf("044_m11_harden_campaign_aid_permissions.sql")
        val i45 = names.indexOf("045_m11_shelter_emergencies_events_reports.sql")
        assertTrue(i44 < i45)
    }

    @Test
    fun migration_045_has_block3_tables() {
        val sql = sql045()
        tables.forEach { assertTrue("missing table $it", sql.contains(it)) }
    }

    @Test
    fun migration_045_enables_rls_drop_policy_before_create() {
        val sql = sql045()
        assertTrue(sql.contains("enable row level security") || sql.contains("ROW LEVEL SECURITY"))
        assertTrue(sql.contains("drop policy if exists"))
        listOf(
            "shelter_emergencies_select_m11",
            "shelter_emergencies_ins_m11",
            "shelter_events_select_m11",
            "shelter_events_ins_m11",
            "shelter_event_reg_select_m11",
            "shelter_event_reg_ins_m11"
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
    fun migration_045_has_m11_rpc_functions_and_search_path() {
        val sql = sql045()
        clientRpcs.forEach { rpc ->
            val bare = rpc.substringBefore('(')
            assertTrue("missing rpc $bare", sql.contains(bare))
        }
        assertTrue(sql.contains("security definer") || sql.contains("SECURITY DEFINER"))
        assertTrue(sql.contains("search_path"))
    }

    @Test
    fun migration_045_no_drop_table() {
        val sql = sql045().lowercase()
        assertFalse(Regex("(?i)drop\\s+table\\b").containsMatchIn(sql))
    }

    @Test
    fun migration_045_no_payment_or_monetary_columns() {
        val sql = sql045().lowercase()
        listOf(
            "cbu",
            "alias bancario",
            "tarjeta",
            "mercado pago",
            "cuenta bancaria",
            "ticket",
            "checkout",
            "payment"
        ).forEach { forbidden ->
            assertFalse("forbidden token: $forbidden", sql.contains("$forbidden text"))
            assertFalse("forbidden token: $forbidden", sql.contains("$forbidden,"))
        }
    }

    @Test
    fun migration_045_allows_m05_and_file_asset_refs() {
        val sql = sql045()
        assertTrue(sql.contains("m05://") || sql.contains("file_asset:"))
        assertTrue(sql.contains("_m11_require_safe_evidence") || sql.contains("m05://|file_asset:"))
    }

    @Test
    fun migration_045_no_service_role_key_literal() {
        val sql = sql045().lowercase()
        assertFalse(sql.contains("service_role_key"))
    }

    @Test
    fun migration_045_does_not_rewrite_040_to_044_files() {
        val sql = sql045()
        assertFalse(sql.contains("040_m10_foster_homes_core.sql"))
        assertFalse(sql.contains("041_m10_foster_care_management.sql"))
        assertFalse(sql.contains("042_m11_shelter_operations_core.sql"))
        assertFalse(sql.contains("043_m11_shelter_campaigns_and_aid.sql"))
        assertFalse(sql.contains("044_m11_harden_campaign_aid_permissions.sql"))
    }

    @Test
    fun migration_045_does_not_drop_tables_from_040_to_044() {
        val sql = sql045()
        listOf(
            "foster_home_profiles",
            "foster_care_requests",
            "foster_placements",
            "foster_expenses",
            "shelter_profiles",
            "shelter_pet_placements",
            "shelter_volunteer_assignments",
            "shelter_campaigns",
            "shelter_campaign_updates",
            "shelter_supply_requests",
            "shelter_supply_contributions"
        ).forEach { table ->
            assertFalse(
                "045 must not DROP $table",
                Regex("(?i)drop\\s+table\\s+(if\\s+exists\\s+)?public\\.$table\\b")
                    .containsMatchIn(sql)
            )
        }
    }

    @Test
    fun migrations_040_to_044_still_exist() {
        listOf(migration040, migration041, migration042, migration043, migration044).forEach { path ->
            assertTrue("$path must exist", sourceFile(path).isFile)
        }
    }

    @Test
    fun migration_045_has_shelter_block3_permissions() {
        val sql = sql045()
        listOf(
            "shelter.emergency.read",
            "shelter.emergency.manage",
            "shelter.event.read",
            "shelter.event.manage",
            "shelter.report.read",
            "shelter.report.export"
        ).forEach { assertTrue("missing permission $it", sql.contains(it)) }
    }

    @Test
    fun client_rpcs_revoked_from_public_and_anon() {
        val n = normalizeWs(sql045())
        clientRpcs.forEach { sig ->
            assertTrue(
                "missing revoke PUBLIC for $sig",
                n.contains(normalizeWs("revoke all on function public.$sig from public"))
            )
            assertTrue(
                "missing revoke anon for $sig",
                n.contains(normalizeWs("revoke all on function public.$sig from anon"))
            )
        }
        assertTrue("expected 25 client RPCs", clientRpcs.size == 25)
    }

    @Test
    fun client_rpcs_granted_execute_only_to_authenticated() {
        val n = normalizeWs(sql045())
        clientRpcs.forEach { sig ->
            assertTrue(
                "missing grant execute for $sig",
                n.contains(normalizeWs("grant execute on function public.$sig to authenticated"))
            )
        }
        assertFalse(Regex("grant\\s+execute\\s+on\\s+function\\s+public\\.m11_\\w+[^;]*\\s+to\\s+anon").containsMatchIn(n))
        assertFalse(Regex("grant\\s+execute\\s+on\\s+function\\s+public\\.m11_\\w+[^;]*\\s+to\\s+public").containsMatchIn(n))
    }

    @Test
    fun helpers_revoked_from_public_anon_and_authenticated() {
        val sql = sql045()
        helpers.forEach { sig ->
            val bare = sig.substringBefore('(')
            val revokeLine = sql.lines().firstOrNull { line ->
                line.contains("revoke all on function public.$bare", ignoreCase = true)
            }
            assertTrue("missing revoke line for helper $sig", revokeLine != null)
            assertTrue(
                "helper $sig must revoke public, anon and authenticated",
                revokeLine!!.contains("from public, anon, authenticated", ignoreCase = true)
            )
            assertFalse(
                "helper must not grant execute to authenticated: $sig",
                sql.contains("grant execute on function public.$bare", ignoreCase = true)
            )
        }
        assertTrue("expected 5 helpers", helpers.size == 5)
    }

    @Test
    fun tables_revoke_all_from_public_anon_authenticated() {
        val n = normalizeWs(sql045())
        tables.forEach { t ->
            listOf("public", "anon", "authenticated").forEach { role ->
                assertTrue(
                    "missing revoke $role for $t",
                    n.contains("revoke all privileges on table public.$t from $role")
                )
            }
            assertTrue(
                "missing grant select $t",
                n.contains("grant select on table public.$t to authenticated")
            )
        }
    }

    @Test
    fun tables_no_direct_insert_update_delete_to_authenticated() {
        val n = normalizeWs(sql045())
        tables.forEach { t ->
            assertFalse(n.contains("grant insert on table public.$t to authenticated"))
            assertFalse(n.contains("grant update on table public.$t to authenticated"))
            assertFalse(n.contains("grant delete on table public.$t to authenticated"))
        }
    }

    @Test
    fun android_m11_block3_sources_do_not_use_service_role_key() {
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
                            it.name.contains("M11", ignoreCase = true) ||
                            it.name.contains("Emergency", ignoreCase = true) ||
                            it.name.contains("Event", ignoreCase = true))
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
}
