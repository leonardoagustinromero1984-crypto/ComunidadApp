package com.comunidapp.app.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class M06Migration027PushHardeningTest {

    @Test
    fun `migration 027 exists and exposes governed push RPCs`() {
        val migration = migration027Text()

        listOf(
            "m06_claim_push_deliveries",
            "m06_mark_delivery_result",
            "m06_plan_push_deliveries",
            "m06_emit_domain_notification"
        ).forEach { function ->
            assertTrue("$function missing", migration.contains("function public.$function"))
        }
    }

    @Test
    fun `migration 027 wires M03 M04 and M05 notification events`() {
        val migration = migration027Text()

        listOf(
            "m03.invitation.created",
            "m04.moderation.action_applied",
            "m05.upload.",
            "m06_org_invitation_notify",
            "m06_moderation_action_notify",
            "m06_file_upload_notify"
        ).forEach { marker ->
            assertTrue("$marker missing", migration.contains(marker))
        }
    }

    @Test
    fun `migration 027 does not reopen cross user create_notification`() {
        val migration = migration027Text()

        assertFalse(migration.contains("create or replace function public.create_notification"))
        assertFalse(migration.contains("create policy notifications_insert"))
        assertFalse(migration.contains("M06_CREATE_NOTIFICATION_CROSS_USER_DENIED"))
    }

    @Test
    fun `migration 027 stores token reference without returning it`() {
        val migration = migration027Text()

        assertTrue(migration.contains("p_token_reference text default null"))
        assertTrue(migration.contains("token_protected_or_token_reference"))
        assertTrue(migration.contains("return to_jsonb(v_row) - 'token_protected_or_token_reference'"))
    }

    @Test
    fun `migration 027 never marks notifications read for push delivery`() {
        val migration = migration027Text()
        val markDelivery = migration.substringAfter("function public.m06_mark_delivery_result")
            .substringBefore("-- Store token reference")

        assertTrue(markDelivery.contains("Never mark notification READ on push delivery"))
        assertFalse(markDelivery.contains("set state = 'READ'"))
        assertFalse(markDelivery.contains("is_read = true"))
    }

    private fun migration027Text(): String =
        migrationFile("027_m06_push_delivery_and_installation_hardening.sql").readText()

    private fun migrationFile(name: String): File {
        val candidates = listOf(
            File("supabase/migrations/$name"),
            File("../supabase/migrations/$name"),
            File("../../supabase/migrations/$name"),
            File(System.getProperty("user.dir"), "supabase/migrations/$name"),
            File(System.getProperty("user.dir"), "../supabase/migrations/$name")
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("$name migration not found. cwd=${System.getProperty("user.dir")}")
    }
}
