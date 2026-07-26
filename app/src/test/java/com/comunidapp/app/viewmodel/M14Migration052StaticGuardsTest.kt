package com.comunidapp.app.viewmodel

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Static guards for M14 Bloque 3 migration 052 (verification + public access). */
class M14Migration052StaticGuardsTest {
    private fun repoRoot(): File = listOf(File("."), File(".."), File("../.."))
        .first { File(it, "supabase/migrations").isDirectory }

    private fun sql052(): String =
        File(repoRoot(), "supabase/migrations/052_m14_credential_verification_and_public_access.sql").readText()

    @Test
    fun migration_052_exists_without_053_and_001_to_051_present() {
        val names = File(repoRoot(), "supabase/migrations").listFiles()!!
            .map { it.name }
            .filter { it.matches(Regex("^\\d{3}_.*\\.sql$")) }
        val nums = names.map { it.substring(0, 3).toInt() }
        assertEquals(52, nums.maxOrNull())
        assertFalse(nums.contains(53))
        (1..51).forEach { n ->
            assertTrue("falta migración ${n.toString().padStart(3, '0')}", nums.contains(n))
        }
        assertTrue(names.contains("052_m14_credential_verification_and_public_access.sql"))
    }

    @Test
    fun under_review_and_ten_client_rpcs_security_definer() {
        val sql = sql052()
        assertTrue(sql.contains("'UNDER_REVIEW'"))
        val rpcs = listOf(
            "m14_open_verification_review",
            "m14_approve_verification_request",
            "m14_reject_verification_request",
            "m14_expire_verification_request",
            "m14_get_verification_decision",
            "m14_list_verification_decisions",
            "m14_issue_verified_credential",
            "m14_revoke_verified_credential",
            "m14_rotate_public_code",
            "m14_list_passport_status_history"
        )
        assertEquals(10, rpcs.size)
        rpcs.forEach { assertTrue("missing $it", sql.contains("function public.$it")) }
        assertTrue(sql.contains("security definer"))
        assertTrue(sql.contains("search_path = public"))
        assertTrue(
            sql.contains("auth.uid()") || sql.contains("_m14_require_auth()"),
        )
        assertTrue(sql.contains("for update"))
    }

    @Test
    fun anti_self_verify_concurrency_and_no_clinical_history() {
        val sql = sql052().lowercase()
        assertTrue(sql.contains("_m14_can_decide_request"))
        assertTrue(sql.contains("self_verification_not_allowed") || sql.contains("SELF_VERIFICATION_NOT_ALLOWED".lowercase()))
        assertTrue(sql.contains("_m14_write_final_decision"))
        assertTrue(sql.contains("decision_already_exists") || sql.contains("DECISION_ALREADY_EXISTS".lowercase()))
        assertTrue(sql.contains("conflict") || sql.contains("CONFLICT".lowercase()))
        assertTrue(sql.contains("_m14_generate_public_code"))
        assertFalse(sql.contains("clinical") || sql.contains("historia_clinica") || sql.contains("medical_record"))
        assertFalse(sql.contains("service_role"))
    }

    @Test
    fun grants_authenticated_only_and_helpers_revoked_no_direct_dml() {
        val sql = sql052().lowercase()
        assertTrue(sql.contains("grant execute on function public.%s to authenticated"))
        assertTrue(sql.contains("revoke all on function public.%s from public, anon"))
        assertTrue(sql.contains("revoke all privileges on table public.pet_passports"))
        assertTrue(sql.contains("grant select on table public.pet_passports to authenticated"))
        listOf("insert", "update", "delete", "truncate").forEach { priv ->
            assertFalse(
                "must not grant $priv",
                Regex("grant\\s+$priv\\b").containsMatchIn(sql)
            )
        }
        // No approve from PENDING: explicit check in approve/reject
        assertTrue(sql.contains("if r.status = 'pending'"))
        assertTrue(sql.contains("invalid_transition") || sql.contains("INVALID_TRANSITION".lowercase()))
    }

    @Test
    fun no_anon_execute_on_new_rpcs() {
        val sql = sql052().lowercase()
        assertFalse(sql.contains("to anon, authenticated"))
        assertFalse(Regex("grant execute on function public\\.m14_open_verification_review[^\\n]*to anon").containsMatchIn(sql))
        assertFalse(Regex("grant execute on function public\\.m14_approve_verification_request[^\\n]*to anon").containsMatchIn(sql))
    }
}
