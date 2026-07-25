package com.comunidapp.app.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guardas estáticas de la migración 049 (revisión humana remota). Sin apply remoto.
 */
class M13Migration049StaticGuardsTest {

    private fun repoRoot(): File = listOf(File("."), File(".."), File("../.."))
        .first { File(it, "supabase/migrations").isDirectory }

    private fun sql049(): String =
        File(repoRoot(), "supabase/migrations/049_m13_match_review_workflow.sql").readText()

    @Test
    fun only_049_new_and_001_to_048_present() {
        val names = File(repoRoot(), "supabase/migrations").listFiles()!!
            .map { it.name }
            .filter { it.matches(Regex("^\\d{3}_.*\\.sql$")) }
            .sorted()
        val nums = names.map { it.substring(0, 3).toInt() }
        assertEquals(49, nums.maxOrNull())
        assertFalse(nums.contains(50))
        (1..48).forEach { n ->
            assertTrue("falta migración ${n.toString().padStart(3, '0')}", nums.contains(n))
        }
        assertTrue(names.any { it == "049_m13_match_review_workflow.sql" })
    }

    @Test
    fun eight_client_rpcs_security_definer_search_path() {
        val sql = sql049()
        val client = listOf(
            "m13_open_match_review",
            "m13_confirm_match_candidate",
            "m13_reject_match_candidate",
            "m13_mark_match_inconclusive",
            "m13_withdraw_match_candidate",
            "m13_expire_match_candidate",
            "m13_list_match_decisions",
            "m13_list_match_status_history"
        )
        assertEquals(8, client.size)
        client.forEach { assertTrue("falta $it", sql.contains("function public.$it")) }
        assertTrue(sql.contains("security definer"))
        assertTrue(sql.contains("search_path = public"))
        assertTrue(sql.contains("for update"))
        assertTrue(sql.contains("auth.uid()"))
    }

    @Test
    fun grants_authenticated_helpers_revoked_no_dml() {
        val sql = sql049().lowercase()
        assertTrue(sql.contains("grant execute on function public.m13_open_match_review"))
        assertTrue(sql.contains("to authenticated"))
        assertTrue(sql.contains("from anon"))
        assertTrue(sql.contains("from public"))
        assertTrue(
            Regex("grant\\s+execute\\s+on\\s+function\\s+public\\._m13_\\w+[^;]*to\\s+authenticated")
                .findAll(sql).none()
        )
        assertTrue(sql.contains("revoke insert, update, delete on table public.lost_found_match_decisions"))
    }

    @Test
    fun transitions_authority_no_autoconfirm_no_case_close() {
        val sql = sql049()
        assertTrue(sql.contains("UNDER_REVIEW"))
        assertTrue(sql.contains("CONFIRMED"))
        assertTrue(sql.contains("WITHDRAWN"))
        assertTrue(sql.contains("EXPIRED"))
        assertTrue(sql.contains("lostfound.match.review"))
        assertTrue(sql.contains("lostfound.match.confirm"))
        assertTrue(sql.contains("_m13_append_candidate_history"))
        assertTrue(sql.contains("lost_found_match_decisions_candidate_uniq"))
        assertFalse(sql.lowercase().contains("service_role"))
        assertFalse(sql.contains("eyJ"))
        // No cierre automático del caso Lost/Found.
        assertFalse(
            Regex(
                """update\s+public\.lost_found_posts[\s\S]{0,120}status\s*=""",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(sql)
        )
        // Confirm no asigna CONFIRMED desde generate.
        assertFalse(sql.contains("m13_generate_match_candidates") && sql.contains("status = 'CONFIRMED'"))
    }

    @Test
    fun android_remote_wires_eight_rpcs() {
        val src = File(
            repoRoot(),
            "app/src/main/java/com/comunidapp/app/data/remote/supabase/m13/SupabaseM13RemoteDataSource.kt"
        ).readText()
        listOf(
            "m13_open_match_review",
            "m13_confirm_match_candidate",
            "m13_reject_match_candidate",
            "m13_mark_match_inconclusive",
            "m13_withdraw_match_candidate",
            "m13_expire_match_candidate",
            "m13_list_match_decisions",
            "m13_list_match_status_history"
        ).forEach { assertTrue(src.contains(it)) }
        assertFalse(src.contains(".insert("))
    }
}
