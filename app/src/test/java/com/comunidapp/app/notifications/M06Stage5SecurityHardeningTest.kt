package com.comunidapp.app.notifications

import com.comunidapp.app.domain.authorization.PermissionCode
import com.comunidapp.app.domain.notifications.NotificationDeepLink
import com.comunidapp.app.domain.notifications.NotificationDeepLinkRoute
import com.comunidapp.app.domain.notifications.authorization.NotificationAccessDecision
import com.comunidapp.app.domain.notifications.authorization.NotificationDeepLinkAuthorization
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class M06Stage5SecurityHardeningTest {

    @Test
    fun `migration 028 revokes public execute on enqueue and materialize`() {
        val sql = migration028()
        assertTrue(sql.contains("028_m06_stage5_security_hardening") || sql.contains("M06 Etapa 5"))
        assertTrue(sql.contains("revoke all on function public.m06_enqueue_domain_event"))
        assertTrue(sql.contains("revoke all on function public.m06_materialize_in_app_notification"))
        assertTrue(sql.contains("from public, anon, authenticated"))
        assertTrue(sql.contains("grant execute on function public.m06_enqueue_domain_event"))
        assertTrue(sql.contains("to service_role"))
    }

    @Test
    fun `migration 028 fixes enqueue idempotency without updated_at`() {
        val sql = migration028()
        val enqueue = sql.substringAfter("create or replace function public.m06_enqueue_domain_event")
            .substringBefore("-- B3:")
        assertTrue(enqueue.contains("on conflict (idempotency_key) do nothing"))
        assertFalse(enqueue.contains("set updated_at = public.notification_events.created_at"))
        assertTrue(enqueue.contains("where idempotency_key = v_idem"))
    }

    @Test
    fun `migration 028 records emit failures in dead letters`() {
        val sql = migration028()
        assertTrue(sql.contains("notification_dead_letters"))
        assertTrue(sql.contains("M06_EMIT_FAILED") || sql.contains("m06_sanitize_error_code"))
    }

    @Test
    fun `migration 028 does not reopen create_notification cross-user`() {
        val sql = migration028()
        assertFalse(sql.contains("create or replace function public.create_notification"))
        assertFalse(sql.contains("notifications_insert"))
        assertTrue(sql.contains("Do not reopen cross-user") || sql.contains("self-only SYSTEM"))
    }

    @Test
    fun `deep link session resolver never trusts notification organization id`() {
        assertFalse(NotificationDeepLinkSessionResolver.trustsNotificationOrganizationId())

        val link = NotificationDeepLink(
            routeType = NotificationDeepLinkRoute.ORGANIZATION,
            organizationId = "org-from-payload"
        )
        val context = NotificationDeepLinkSessionResolver.buildOpenContext(
            authenticatedUserId = "user-1",
            platformPermissions = emptySet(),
            provenOrganizationId = null,
            organizationPermissionCodes = emptySet(),
            link = link
        )
        assertEquals(null, context.organizationId)
        val decision = NotificationDeepLinkAuthorization.authorizeOpen(context, link)
        assertEquals(NotificationAccessDecision.DENIED_ORGANIZATION, decision)
    }

    @Test
    fun `org deep link allowed only when membership is proven`() {
        val link = NotificationDeepLink(
            routeType = NotificationDeepLinkRoute.ORGANIZATION,
            organizationId = "org-1"
        )
        val denied = NotificationDeepLinkSessionResolver.buildOpenContext(
            authenticatedUserId = "user-1",
            platformPermissions = emptySet(),
            provenOrganizationId = null,
            organizationPermissionCodes = emptySet(),
            link = link
        )
        val allowed = NotificationDeepLinkSessionResolver.buildOpenContext(
            authenticatedUserId = "user-1",
            platformPermissions = emptySet(),
            provenOrganizationId = "org-1",
            organizationPermissionCodes = setOf("organization.view"),
            link = link
        )
        assertEquals(
            NotificationAccessDecision.DENIED_ORGANIZATION,
            NotificationDeepLinkAuthorization.authorizeOpen(denied, link)
        )
        assertEquals(
            NotificationAccessDecision.ALLOWED,
            NotificationDeepLinkAuthorization.authorizeOpen(allowed, link)
        )
    }

    @Test
    fun `staff deep link denied without platform permission`() {
        val link = NotificationDeepLink(
            routeType = NotificationDeepLinkRoute.MODERATION_QUEUE,
            requiredPermission = PermissionCode.MODERATION_VIEW.code
        )
        val context = NotificationDeepLinkSessionResolver.buildOpenContext(
            authenticatedUserId = "staff-1",
            platformPermissions = emptySet(),
            provenOrganizationId = null,
            organizationPermissionCodes = emptySet(),
            link = link
        )
        assertEquals(
            NotificationAccessDecision.DENIED_PERMISSION,
            NotificationDeepLinkAuthorization.authorizeOpen(context, link)
        )
    }

    @Test
    fun `resource deep link without proof fails closed to deny`() {
        val link = NotificationDeepLink(
            routeType = NotificationDeepLinkRoute.PET,
            resourceId = "pet-1"
        )
        val context = NotificationDeepLinkSessionResolver.buildOpenContext(
            authenticatedUserId = "user-1",
            platformPermissions = emptySet(),
            provenOrganizationId = null,
            organizationPermissionCodes = emptySet(),
            link = link
        )
        assertFalse(context.resourceExists)
        assertEquals(
            NotificationAccessDecision.DENIED_DEEP_LINK,
            NotificationDeepLinkAuthorization.authorizeOpen(context, link)
        )
        val resolved = NotificationDeepLinkRouter.resolve(link, context)
        assertTrue(resolved.usedFallback)
    }

    @Test
    fun `migrations 001 to 025 remain untouched by stage 5`() {
        val stage5 = migration028()
        assertFalse(stage5.contains("001_"))
        assertFalse(stage5.contains("edit migraciones 001"))
        // 028 must not recreate 026/027 wholesale
        assertFalse(stage5.contains("create table if not exists public.notification_events"))
    }

    private fun migration028(): String {
        val name = "028_m06_stage5_security_hardening.sql"
        val candidates = listOf(
            File("supabase/migrations/$name"),
            File("../supabase/migrations/$name"),
            File("../../supabase/migrations/$name"),
            File(System.getProperty("user.dir"), "supabase/migrations/$name")
        )
        return candidates.first { it.isFile }.readText()
    }
}
