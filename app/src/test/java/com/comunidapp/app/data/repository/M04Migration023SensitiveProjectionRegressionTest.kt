package com.comunidapp.app.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Regresión local de la migración correctiva 023 (Etapa 5).
 * No ejecuta SQL remoto; valida presencia y alcance mínimo del fix.
 */
class M04Migration023SensitiveProjectionRegressionTest {

    private fun migration023Text(): String {
        val candidates = listOf(
            File("supabase/migrations/023_m04_sensitive_projection_rls_fix.sql"),
            File("../supabase/migrations/023_m04_sensitive_projection_rls_fix.sql"),
            File("../../supabase/migrations/023_m04_sensitive_projection_rls_fix.sql"),
            File(System.getProperty("user.dir"), "supabase/migrations/023_m04_sensitive_projection_rls_fix.sql"),
            File(System.getProperty("user.dir"), "../supabase/migrations/023_m04_sensitive_projection_rls_fix.sql")
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error(
                "023 migration not found. cwd=${System.getProperty("user.dir")} tried=" +
                    candidates.joinToString { it.absolutePath }
            )
        return file.readText()
    }

    @Test
    fun content_reports_select_is_reporter_only() {
        val sql = migration023Text()
        assertTrue(sql.contains("drop policy if exists content_reports_select"))
        assertTrue(sql.contains("reporter_id = auth.uid()"))
        assertFalse(sql.contains("or public.has_permission('moderation.view')"))
        assertFalse(sql.contains("or public.has_permission('moderation.manage_reports')"))
    }

    @Test
    fun support_messages_policy_checks_parent_category() {
        val sql = migration023Text()
        assertTrue(sql.contains("support_ticket_messages_select"))
        assertTrue(sql.contains("support.view_sensitive"))
        assertTrue(sql.contains("'PRIVACY', 'SAFETY'"))
    }

    @Test
    fun verification_review_rpc_uses_allowlist_without_nested_row_leak() {
        val sql = migration023Text()
        assertTrue(sql.contains("get_organization_verification_review"))
        assertTrue(sql.contains("organization_verification_reviews_select"))
        assertTrue(sql.contains("using (false)"))
        val fnStart = sql.indexOf("create or replace function public.get_organization_verification_review")
        assertTrue(fnStart >= 0)
        val fnBody = sql.substring(fnStart)
        assertFalse(fnBody.contains("row_to_json(r)"))
        assertTrue(fnBody.contains("jsonb_build_object"))
    }
}
