package com.comunidapp.app.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guardas estáticas de la migración 048 (M13 Bloque 2). Sin apply remoto.
 */
class M13Migration048StaticGuardsTest {

    private fun repoRoot(): File = listOf(File("."), File(".."), File("../.."))
        .first { File(it, "supabase/migrations").isDirectory }

    private fun sql048(): String =
        File(repoRoot(), "supabase/migrations/048_m13_sightings_and_match_candidates.sql").readText()

    @Test
    fun only_048_new_and_001_to_047_present() {
        val names = File(repoRoot(), "supabase/migrations").listFiles()!!
            .map { it.name }
            .filter { it.matches(Regex("^\\d{3}_.*\\.sql$")) }
            .sorted()
        val nums = names.map { it.substring(0, 3).toInt() }
        assertEquals(48, nums.maxOrNull())
        assertFalse(nums.contains(49))
        (1..47).forEach { n ->
            assertTrue("falta migración ${n.toString().padStart(3, '0')}", nums.contains(n))
        }
    }

    @Test
    fun expected_tables_and_lateral_strategy() {
        val sql = sql048()
        assertTrue(sql.contains("create table if not exists public.lost_found_sighting_details"))
        assertTrue(sql.contains("create table if not exists public.lost_found_match_candidates"))
        assertTrue(sql.contains("create table if not exists public.lost_found_match_decisions"))
        assertTrue(sql.contains("create table if not exists public.lost_found_match_status_history"))
        assertFalse(sql.contains("drop table") && sql.contains("lost_found_sightings"))
    }

    @Test
    fun constraints_indexes_rls() {
        val sql = sql048().lowercase()
        assertTrue(sql.contains("unique (case_id, sighting_id)") || sql.contains("unique(case_id, sighting_id)"))
        assertTrue(sql.contains("enable row level security"))
        assertTrue(sql.contains("score") && sql.contains("between 0 and 100") || sql.contains("check (score"))
    }

    @Test
    fun thirteen_client_rpcs_and_security_definer() {
        val sql = sql048()
        val client = listOf(
            "m13_create_sighting",
            "m13_update_my_sighting",
            "m13_withdraw_my_sighting",
            "m13_get_sighting",
            "m13_list_public_sightings",
            "m13_list_my_sightings",
            "m13_list_managed_sightings",
            "m13_generate_match_candidates_for_sighting",
            "m13_generate_match_candidates_for_case",
            "m13_list_case_match_candidates",
            "m13_list_sighting_match_candidates",
            "m13_get_match_candidate",
            "m13_recalculate_match_candidate"
        )
        client.forEach { assertTrue("falta $it", sql.contains("function public.$it")) }
        assertEquals(13, client.size)
        assertFalse(sql.contains("m13_confirm"))
        assertFalse(sql.contains("m13_reject"))
        assertTrue(sql.contains("security definer"))
        assertTrue(sql.contains("search_path = public"))
    }

    @Test
    fun grants_authenticated_only_and_helpers_revoked() {
        val sql = sql048().lowercase()
        assertTrue(sql.contains("grant execute on function public.m13_create_sighting"))
        assertTrue(sql.contains("to authenticated"))
        assertTrue(sql.contains("revoke all on function public._m13_require_auth"))
        assertTrue(sql.contains("from anon"))
        assertTrue(sql.contains("from public"))
        // Helpers internos _m13_* no deben concederse a authenticated.
        val grantHelperToAuth = Regex(
            "grant\\s+execute\\s+on\\s+function\\s+public\\._m13_\\w+[^;]*to\\s+authenticated"
        )
        assertFalse(grantHelperToAuth.containsMatchIn(sql))
    }

    @Test
    fun actor_from_auth_uid_and_no_service_role_secrets() {
        val sql = sql048()
        val lower = sql.lowercase()
        assertTrue(sql.contains("auth.uid()"))
        assertFalse(Regex("""\bgrant\b[^;]*\bservice_role\b""").containsMatchIn(lower))
        assertFalse(sql.contains("eyJ"))
        assertTrue(sql.contains("m05://") || sql.contains("file_asset:"))
        assertTrue(sql.contains("lostfound.sighting.create"))
        // No assignment de CONFIRMED dentro de generate_match* (solo enums/comentarios).
        assertFalse(
            Regex(
                """m13_generate_match_candidates[\s\S]{0,800}?status\s*=\s*'CONFIRMED'""",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(sql)
        )
    }

    @Test
    fun no_direct_dml_grants_on_new_tables() {
        val sql = sql048().lowercase()
        assertTrue(
            sql.contains("revoke all privileges on table public.lost_found_sighting_details from authenticated") ||
                sql.contains("revoke all on table public.lost_found_sighting_details from authenticated")
        )
        assertTrue(
            sql.contains("revoke all privileges on table public.lost_found_match_candidates from authenticated") ||
                sql.contains("revoke all on table public.lost_found_match_candidates from authenticated")
        )
    }
}
