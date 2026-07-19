package com.comunidapp.app.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class M06Migration026SecurityOutboxTest {

    @Test
    fun `migration 026 closes direct insert and cross-user create_notification`() {
        val sql = migration026Text()

        assertTrue(sql.contains("drop policy if exists notifications_insert"))
        assertTrue(sql.contains("notifications_no_insert_client"))
        assertTrue(sql.contains("with check (false)"))
        assertTrue(sql.contains("M06_CREATE_NOTIFICATION_CROSS_USER_DENIED"))
        assertTrue(sql.contains("target_user_id is distinct from v_actor"))
        assertFalse(sql.contains("create policy notifications_insert on public.notifications for insert to authenticated with check (true)"))
    }

    @Test
    fun `migration 026 creates required M06 tables and keeps device_tokens legacy`() {
        val sql = migration026Text()

        listOf(
            "notification_events",
            "notification_preferences",
            "notification_device_installations",
            "notification_deliveries",
            "notification_outbox",
            "notification_dead_letters",
            "notification_access_audit"
        ).forEach { table ->
            assertTrue("$table missing", sql.contains("create table if not exists public.$table"))
        }

        assertTrue(sql.contains("comment on table public.device_tokens"))
        assertFalse(sql.contains("drop table if exists public.device_tokens"))
        assertFalse(sql.contains("delete from public.device_tokens"))
    }

    @Test
    fun `migration 026 exposes safe RPC surface and denies Android outbox deliveries`() {
        val sql = migration026Text()

        listOf(
            "m06_require_active_actor",
            "m06_validate_payload",
            "m06_can_read_notification",
            "m06_enqueue_domain_event",
            "m06_materialize_in_app_notification",
            "m06_claim_outbox",
            "m06_mark_outbox_processed",
            "m06_mark_outbox_failed",
            "m06_get_inbox",
            "m06_get_unread_count",
            "m06_mark_notification_read",
            "m06_mark_all_notifications_read",
            "m06_archive_notification",
            "m06_delete_notification_logical",
            "m06_get_preferences",
            "m06_update_preference",
            "m06_register_installation",
            "m06_rotate_installation_token",
            "m06_revoke_current_installation"
        ).forEach { function ->
            assertTrue("$function missing", sql.contains("function public.$function"))
        }

        assertTrue(sql.contains("notification_events_no_client"))
        assertTrue(sql.contains("notification_deliveries_no_client"))
        assertTrue(sql.contains("notification_outbox_no_client"))
        assertTrue(sql.contains("notification_dead_letters_no_client"))
    }

    @Test
    fun `migration 026 rejects sensitive payload leaks and keeps legacy inbox readable`() {
        val sql = migration026Text()

        assertTrue(sql.contains("M06_PAYLOAD_URL_REJECTED"))
        assertTrue(sql.contains("M06_PAYLOAD_SECRET_REJECTED"))
        assertTrue(sql.contains("M06_PAYLOAD_TECHNICAL_LEAK_REJECTED"))
        assertTrue(sql.contains("M06_PAYLOAD_PII_REJECTED"))
        assertTrue(sql.contains("is_read boolean not null default false"))
        assertTrue(sql.contains("deduplication_key = coalesce(deduplication_key, 'legacy:' || id::text)"))
        assertTrue(sql.contains("expires_at is null or expires_at > timezone('utc', now())"))
    }

    @Test
    fun `migration 026 preserves auth username and previous migrations`() {
        val sql = migration026Text()

        assertFalse(sql.contains("AuthRepository"))
        assertFalse(sql.contains("UsernameValidators"))
        assertFalse(sql.contains("domain/auth"))
        assertFalse(sql.contains("drop table if exists public.users"))
        assertTrue(migrationFile("012_fase1_to_4_closure.sql").isFile)
        assertTrue(migrationFile("025_m05_profile_org_media_storage_rls_fix.sql").isFile)
    }

    private fun migration026Text(): String {
        val file = migrationFile("026_m06_notifications_security_outbox_foundation.sql")
        return file.readText()
    }

    private fun migrationFile(name: String): File {
        val candidates = listOf(
            File("supabase/migrations/$name"),
            File("../supabase/migrations/$name"),
            File("../../supabase/migrations/$name"),
            File(System.getProperty("user.dir"), "supabase/migrations/$name"),
            File(System.getProperty("user.dir"), "../supabase/migrations/$name")
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error(
                "$name migration not found. cwd=${System.getProperty("user.dir")} tried=" +
                    candidates.joinToString { it.absolutePath }
            )
        return file
    }
}
