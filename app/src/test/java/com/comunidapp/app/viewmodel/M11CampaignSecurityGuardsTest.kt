package com.comunidapp.app.viewmodel

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LeoVer M11 — guardas estáticas de hardening 044 (permisos campañas/insumos).
 * Valida firmas reales de RPC/helpers de 043; no inventa listas parciales.
 */
class M11CampaignSecurityGuardsTest {

    private val migration043 = "supabase/migrations/043_m11_shelter_campaigns_and_aid.sql"
    private val migration044 = "supabase/migrations/044_m11_harden_campaign_aid_permissions.sql"

    private val tables = listOf(
        "shelter_campaigns",
        "shelter_campaign_updates",
        "shelter_supply_requests",
        "shelter_supply_contributions"
    )

    /** Firmas exactas de RPC cliente en 043 (orden de tipos = identity args). */
    private val clientRpcs = listOf(
        "m11_create_shelter_campaign(uuid, text, text, text, text, timestamptz, timestamptz, text, boolean)",
        "m11_update_shelter_campaign(uuid, text, text, text, text, timestamptz, timestamptz, text)",
        "m11_change_shelter_campaign_status(uuid, text)",
        "m11_get_shelter_campaign(uuid)",
        "m11_list_public_shelter_campaigns()",
        "m11_list_shelter_campaigns(uuid)",
        "m11_add_shelter_campaign_update(uuid, text, text, text)",
        "m11_list_shelter_campaign_updates(uuid)",
        "m11_create_supply_request(uuid, text, integer, text, text, text, uuid, text, timestamptz, text, text, boolean)",
        "m11_update_supply_request(uuid, text, integer, text, text, text, text, timestamptz, text, text)",
        "m11_cancel_supply_request(uuid)",
        "m11_get_supply_request(uuid)",
        "m11_list_public_supply_requests()",
        "m11_list_shelter_supply_requests(uuid)",
        "m11_pledge_supply_contribution(uuid, integer, text)",
        "m11_cancel_supply_contribution(uuid)",
        "m11_confirm_supply_contribution(uuid)",
        "m11_record_supply_receipt(uuid, integer, text, text)",
        "m11_get_supply_contribution(uuid)",
        "m11_list_supply_contributions(uuid)"
    )

    /** Helpers internos creados en 043. */
    private val helpers = listOf(
        "_m11_is_safe_evidence_ref(text)",
        "_m11_require_safe_evidence(text)",
        "_m11_recompute_supply_status(integer, integer, integer, timestamptz, timestamptz, text)",
        "_m11_sync_supply_request_totals(uuid)",
        "_m11_open_supply_request_statuses()"
    )

    private fun migrationDir(): File {
        val candidates = listOf(
            File("supabase/migrations"),
            File("../supabase/migrations"),
            File("../../supabase/migrations")
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("supabase/migrations not found from ${File(".").absolutePath}")
    }

    private fun sourceFile(relative: String): File {
        val f = File(relative)
        if (f.isFile) return f
        val fromMigrations = File(migrationDir().parentFile?.parentFile ?: File("."), relative)
        if (fromMigrations.isFile) return fromMigrations
        return File(migrationDir(), File(relative).name).also {
            require(it.isFile) { "missing $relative" }
        }
    }

    private fun normalizeWs(s: String): String =
        s.lowercase().replace(Regex("\\s+"), " ")

    private fun sql044(): String = sourceFile(migration044).readText()

    private fun sql043(): String = sourceFile(migration043).readText()

    @Test
    fun migration_044_exists() {
        val names = migrationDir().listFiles()?.map { it.name }?.sorted().orEmpty()
        assertTrue(names.contains("044_m11_harden_campaign_aid_permissions.sql"))
        assertTrue(File(migrationDir(), "044_m11_harden_campaign_aid_permissions.sql").isFile)
    }

    @Test
    fun migration_043_remains_intact_file() {
        assertTrue(sourceFile(migration043).isFile)
        val sql = sql043()
        assertTrue(sql.contains("shelter_campaigns"))
        assertTrue(sql.contains("m11_create_shelter_campaign"))
        // 044 must not rewrite 043 content
        assertFalse(sql044().contains("create table if not exists public.shelter_campaigns"))
    }

    @Test
    fun tables_revoke_all_from_public() {
        val n = normalizeWs(sql044())
        tables.forEach { t ->
            assertTrue(
                "missing revoke public for $t",
                n.contains("revoke all privileges on table public.$t from public")
            )
        }
    }

    @Test
    fun tables_revoke_all_from_anon() {
        val n = normalizeWs(sql044())
        tables.forEach { t ->
            assertTrue(
                "missing revoke anon for $t",
                n.contains("revoke all privileges on table public.$t from anon")
            )
        }
    }

    @Test
    fun tables_revoke_all_from_authenticated() {
        val n = normalizeWs(sql044())
        tables.forEach { t ->
            assertTrue(
                "missing revoke authenticated for $t",
                n.contains("revoke all privileges on table public.$t from authenticated")
            )
        }
    }

    @Test
    fun tables_grant_select_only_to_authenticated() {
        val n = normalizeWs(sql044())
        tables.forEach { t ->
            assertTrue(
                "missing grant select $t",
                n.contains("grant select on table public.$t to authenticated")
            )
        }
    }

    @Test
    fun tables_no_direct_insert_to_authenticated() {
        val n = normalizeWs(sql044())
        assertFalse(n.contains("grant insert on table public.shelter_"))
        assertFalse(Regex("grant\\s+insert\\b").containsMatchIn(n))
    }

    @Test
    fun tables_no_direct_update_to_authenticated() {
        val n = normalizeWs(sql044())
        assertFalse(Regex("grant\\s+update\\b").containsMatchIn(n))
    }

    @Test
    fun tables_no_direct_delete_to_authenticated() {
        val n = normalizeWs(sql044())
        assertFalse(Regex("grant\\s+delete\\b").containsMatchIn(n))
    }

    @Test
    fun tables_no_direct_truncate_to_authenticated() {
        val n = normalizeWs(sql044())
        assertFalse(Regex("grant\\s+truncate\\b").containsMatchIn(n))
    }

    @Test
    fun tables_no_direct_references_to_authenticated() {
        val n = normalizeWs(sql044())
        assertFalse(Regex("grant\\s+references\\b").containsMatchIn(n))
    }

    @Test
    fun tables_no_direct_trigger_to_authenticated() {
        val n = normalizeWs(sql044())
        assertFalse(Regex("grant\\s+trigger\\b").containsMatchIn(n))
    }

    @Test
    fun client_rpcs_revoked_from_public() {
        val n = normalizeWs(sql044())
        clientRpcs.forEach { sig ->
            val target = normalizeWs("revoke all on function public.$sig from public")
            assertTrue("missing revoke PUBLIC for $sig", n.contains(target))
        }
        assertTrue("expected 20 client RPCs", clientRpcs.size == 20)
    }

    @Test
    fun client_rpcs_revoked_from_anon() {
        val n = normalizeWs(sql044())
        clientRpcs.forEach { sig ->
            val target = normalizeWs("revoke all on function public.$sig from anon")
            assertTrue("missing revoke anon for $sig", n.contains(target))
        }
    }

    @Test
    fun client_rpcs_granted_execute_only_to_authenticated() {
        val n = normalizeWs(sql044())
        clientRpcs.forEach { sig ->
            val target = normalizeWs("grant execute on function public.$sig to authenticated")
            assertTrue("missing grant execute for $sig", n.contains(target))
        }
        assertFalse(Regex("grant\\s+execute\\s+on\\s+function\\s+public\\.m11_\\w+[^;]*\\s+to\\s+anon").containsMatchIn(n))
        assertFalse(Regex("grant\\s+execute\\s+on\\s+function\\s+public\\.m11_\\w+[^;]*\\s+to\\s+public").containsMatchIn(n))
    }

    @Test
    fun helpers_revoked_from_public_anon_and_authenticated() {
        val n = normalizeWs(sql044())
        helpers.forEach { sig ->
            listOf("public", "anon", "authenticated").forEach { role ->
                val target = normalizeWs("revoke all on function public.$sig from $role")
                assertTrue("missing revoke $role for helper $sig", n.contains(target))
            }
            assertFalse(
                "helper must not grant execute to authenticated: $sig",
                n.contains(normalizeWs("grant execute on function public.$sig to authenticated"))
            )
        }
        assertTrue("expected 5 helpers from 043", helpers.size == 5)
    }

    @Test
    fun rls_reaffirmed_on_four_tables() {
        val n = normalizeWs(sql044())
        tables.forEach { t ->
            assertTrue(
                "missing enable rls for $t",
                n.contains("alter table public.$t enable row level security")
            )
        }
    }

    @Test
    fun no_drop_table() {
        val n = normalizeWs(sql044())
        assertFalse(Regex("drop\\s+table").containsMatchIn(n))
    }

    @Test
    fun no_data_mutations() {
        val n = normalizeWs(sql044())
        assertFalse(Regex("\\binsert\\s+into\\b").containsMatchIn(n))
        assertFalse(Regex("\\bupdate\\s+public\\.").containsMatchIn(n))
        assertFalse(Regex("\\bdelete\\s+from\\b").containsMatchIn(n))
        assertFalse(Regex("\\btruncate\\b").containsMatchIn(n))
    }

    @Test
    fun no_service_role_in_android_and_044_not_exposing_key() {
        val n = normalizeWs(sql044())
        assertFalse(n.contains("service_role_key"))
        val candidates = listOf(
            File("app/src/main/java/com/comunidapp/app/data/remote/supabase/m11/SupabaseShelterM11RemoteDataSource.kt"),
            File("src/main/java/com/comunidapp/app/data/remote/supabase/m11/SupabaseShelterM11RemoteDataSource.kt"),
            File("../app/src/main/java/com/comunidapp/app/data/remote/supabase/m11/SupabaseShelterM11RemoteDataSource.kt")
        )
        val remote = candidates.firstOrNull { it.isFile }
            ?: error("SupabaseShelterM11RemoteDataSource.kt not found")
        val kotlin = remote.readText().lowercase()
        assertFalse(kotlin.contains("service_role_key"))
        // Client must not call with service role key; string "service_role" as role name in grants is SQL-only.
        assertFalse(kotlin.contains("role=service_role"))
        assertFalse(kotlin.contains("\"service_role\""))
    }

    @Test
    fun client_rpc_list_matches_043_grants() {
        val sql43 = normalizeWs(sql043())
        clientRpcs.forEach { sig ->
            val bare = sig.substringBefore('(')
            assertTrue("043 missing RPC $bare", sql43.contains(bare.lowercase()))
            // 043 granted execute with same type list (spacing may vary)
            val types = sig.substringAfter('(').substringBeforeLast(')')
            assertTrue(
                "043 missing grant signature types for $bare",
                sql43.contains(normalizeWs("grant execute on function public.$bare($types)")) ||
                    sql43.contains(bare.lowercase())
            )
        }
    }
}
