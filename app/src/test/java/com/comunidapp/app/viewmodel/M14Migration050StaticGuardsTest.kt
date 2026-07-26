package com.comunidapp.app.viewmodel

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Static safety guards for the forward-only M14 migration 050. */
class M14Migration050StaticGuardsTest {
    private fun repoRoot(): File = listOf(File("."), File(".."), File("../.."))
        .first { File(it, "supabase/migrations").isDirectory }

    private fun sql050(): String =
        File(repoRoot(), "supabase/migrations/050_m14_pet_passports_and_credentials.sql").readText()

    @Test
    fun migration_050_exists_and_remains_intact() {
        val names = File(repoRoot(), "supabase/migrations").listFiles()!!.map { it.name }
        assertTrue("050 migration missing", names.contains("050_m14_pet_passports_and_credentials.sql"))
        assertTrue("051 hotfix missing", names.contains("051_m14_revoke_residual_table_privileges.sql"))
        assertTrue("052 B3 missing", names.contains("052_m14_credential_verification_and_public_access.sql"))
        assertFalse("053 must not exist", names.any { it.startsWith("053_") })
    }

    @Test
    fun plpgsql_dollar_delimiters_are_valid_for_archive_and_verification() {
        val sql = sql050()
        assertTrue(
            sql.contains("m14_archive_my_pet_passport") &&
                Regex(
                    """m14_archive_my_pet_passport[\s\S]*?as \$\$[\s\S]*?\$\$;""",
                    RegexOption.MULTILINE
                ).containsMatchIn(sql)
        )
        assertTrue(
            sql.contains("m14_create_verification_request") &&
                Regex(
                    """m14_create_verification_request[\s\S]*?as \$\$[\s\S]*?\$\$;""",
                    RegexOption.MULTILINE
                ).containsMatchIn(sql)
        )
        // Reject the exact broken single-dollar openers that blocked the first apply.
        assertFalse(Regex("""as \$\s*\n\s*declare""").containsMatchIn(sql))
        assertFalse(Regex("""(?m)^\$;""").containsMatchIn(sql))
    }

    @Test
    fun eighteen_client_rpcs_are_security_definer_with_actor_derivation() {
        val sql = sql050()
        val rpcs = listOf(
            "m14_create_pet_passport", "m14_get_pet_passport", "m14_get_pet_passport_by_pet",
            "m14_list_my_pet_passports", "m14_update_my_pet_passport", "m14_activate_my_pet_passport",
            "m14_archive_my_pet_passport", "m14_get_public_pet_passport",
            "m14_create_passport_credential", "m14_update_my_passport_credential",
            "m14_withdraw_my_passport_credential", "m14_get_passport_credential",
            "m14_list_passport_credentials", "m14_create_verification_request",
            "m14_cancel_my_verification_request", "m14_get_verification_request",
            "m14_list_my_verification_requests", "m14_list_managed_verification_requests"
        )
        assertEquals(18, rpcs.size)
        rpcs.forEach { assertTrue("missing $it", sql.contains("function public.$it")) }
        assertTrue(sql.contains("security definer"))
        assertTrue(sql.contains("search_path = public"))
        assertTrue(sql.contains("auth.uid()"))
        assertTrue(sql.contains("gen_random_bytes"))
        assertTrue(sql.contains("m08_actor_has_active_responsibility"))
    }

    @Test
    fun access_controls_expose_only_public_rpc_to_anon() {
        val sql = sql050().lowercase()
        assertTrue(sql.contains("grant execute on function public.m14_get_public_pet_passport(text) to anon, authenticated"))
        assertFalse(sql.contains("service_role"))
        assertFalse(sql.contains("m14_resolve"))
        assertTrue(sql.contains("'_m14_require_auth()'"))
        assertTrue(sql.contains("revoke all on function public.%s from public, anon, authenticated"))
    }

    @Test
    fun tables_indexes_permissions_and_helper_revocations_are_present() {
        val sql = sql050()
        listOf(
            "pet_passports",
            "pet_passport_credentials",
            "pet_passport_verification_requests",
            "pet_passport_verification_decisions",
            "pet_passport_status_history"
        ).forEach { assertTrue("missing table $it", sql.contains("table if not exists public.$it")) }
        assertTrue(sql.contains("pet_passports_one_non_final_per_pet"))
        assertTrue(sql.contains("where status not in ('REVOKED','ARCHIVED')"))
        val permissions = listOf(
            "passport.read", "passport.create", "passport.manage_own",
            "passport.manage_organization", "passport.verify", "passport.moderate",
            "passport.credential.issue", "passport.credential.verify", "passport.public.read"
        )
        assertEquals(9, permissions.size)
        permissions.forEach { assertTrue("missing permission $it", sql.contains("'$it'")) }
        assertTrue(sql.contains("revoke all on function public.%s from public, anon, authenticated"))
    }
}
