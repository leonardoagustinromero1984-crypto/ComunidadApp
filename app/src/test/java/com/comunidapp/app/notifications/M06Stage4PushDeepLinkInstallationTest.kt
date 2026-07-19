package com.comunidapp.app.notifications

import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.repository.MockNotificationRepositories
import com.comunidapp.app.domain.authorization.PermissionCode
import com.comunidapp.app.domain.notifications.NotificationCategory
import com.comunidapp.app.domain.notifications.NotificationChannel
import com.comunidapp.app.domain.notifications.NotificationDeepLink
import com.comunidapp.app.domain.notifications.NotificationDeepLinkRoute
import com.comunidapp.app.domain.notifications.NotificationDeliveryState
import com.comunidapp.app.domain.notifications.NotificationDeliveryStateTransitions
import com.comunidapp.app.domain.notifications.NotificationEvent
import com.comunidapp.app.domain.notifications.NotificationInboxItem
import com.comunidapp.app.domain.notifications.NotificationInstallationPlatform
import com.comunidapp.app.domain.notifications.NotificationInstallationRules
import com.comunidapp.app.domain.notifications.NotificationOriginModule
import com.comunidapp.app.domain.notifications.NotificationPreference
import com.comunidapp.app.domain.notifications.NotificationPreferenceRules
import com.comunidapp.app.domain.notifications.NotificationPriority
import com.comunidapp.app.domain.notifications.NotificationQuietHours
import com.comunidapp.app.domain.notifications.NotificationQuietHoursRules
import com.comunidapp.app.domain.notifications.NotificationRetryPolicy
import com.comunidapp.app.domain.notifications.NotificationSensitivity
import com.comunidapp.app.domain.notifications.NotificationSensitivityRules
import com.comunidapp.app.domain.notifications.NotificationState
import com.comunidapp.app.domain.notifications.QuietHoursDecision
import com.comunidapp.app.domain.notifications.authorization.NotificationAccessDecision
import com.comunidapp.app.domain.notifications.authorization.NotificationAuthContext
import com.comunidapp.app.domain.notifications.authorization.NotificationAuthorization
import com.comunidapp.app.domain.notifications.authorization.NotificationDeepLinkAuthorization
import com.comunidapp.app.domain.user.UsernameValidators
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class M06Stage4PushDeepLinkInstallationTest {

    private val now = Instant.parse("2026-07-16T12:00:00Z")

    @After
    fun clearPendingNavigation() {
        NotificationPendingNavigationStore.clear()
    }

    @Test
    fun valid_push_payload_parses_allowlisted_fields() {
        val parsed = NotificationPushPayloadParser.parse(
            mapOf(
                "notification_id" to "notification-1",
                "delivery_id" to "delivery-1",
                "category" to "PET",
                "priority" to "HIGH",
                "sensitivity" to "PRIVATE",
                "deep_link_type" to "PET",
                "resource_type" to "pet",
                "resource_id" to "pet-1",
                "title" to "Actualización",
                "body" to "Tu mascota tiene novedades."
            )
        )

        assertTrue(parsed.isSuccess)
        assertEquals("notification-1", parsed.getOrThrow().notificationId)
        assertEquals(NotificationDeepLinkRoute.PET, parsed.getOrThrow().deepLink.routeType)
    }

    @Test
    fun push_payload_with_token_signed_url_or_pii_is_rejected() {
        listOf(
            mapOf("notification_id" to "n-1", "category" to "PET", "token" to "secret"),
            mapOf("notification_id" to "n-1", "category" to "PET", "body" to "https://signed.example"),
            mapOf("notification_id" to "n-1", "category" to "PET", "email" to "person@example.com")
        ).forEach { payload ->
            assertTrue(payload.toString(), NotificationPushPayloadParser.parse(payload).isFailure)
        }
    }

    @Test
    fun sensitive_push_uses_generic_copy() {
        val parsed = NotificationPushPayloadParser.parse(
            mapOf(
                "notification_id" to "n-1",
                "category" to "MODERATION",
                "sensitivity" to "SENSITIVE",
                "deep_link_type" to "SAFE_HOME",
                "title" to "Private title",
                "body" to "Private body"
            )
        ).getOrThrow()

        assertEquals(NotificationSensitivityRules.GENERIC_PUSH_TITLE, parsed.title)
        assertEquals(NotificationSensitivityRules.GENERIC_PUSH_BODY, parsed.body)
    }

    @Test
    fun unknown_push_route_falls_back_to_safe_home() {
        val parsed = NotificationPushPayloadParser.parse(
            mapOf(
                "notification_id" to "n-1",
                "category" to "PET",
                "deep_link_type" to "not-a-route"
            )
        ).getOrThrow()

        assertEquals(NotificationDeepLinkRoute.SAFE_HOME, parsed.deepLink.routeType)
        assertEquals(
            NotificationDeepLinkRouter.unknownRouteFallback(),
            NotificationDeepLinkRouter.toNavRoute(parsed.deepLink)
        )
    }

    @Test
    fun deep_link_without_session_is_denied_not_authenticated() {
        val decision = NotificationDeepLinkAuthorization.authorizeOpen(
            NotificationDeepLinkAuthorization.DeepLinkOpenContext(authenticatedUserId = null),
            NotificationDeepLink(NotificationDeepLinkRoute.SAFE_HOME)
        )

        assertEquals(NotificationAccessDecision.DENIED_NOT_AUTHENTICATED, decision)
    }

    @Test
    fun deep_link_to_missing_resource_is_denied() {
        val decision = NotificationDeepLinkAuthorization.authorizeOpen(
            NotificationDeepLinkAuthorization.DeepLinkOpenContext(
                authenticatedUserId = "user-1",
                resourceExists = false
            ),
            NotificationDeepLink(NotificationDeepLinkRoute.PET, resourceId = "pet-1")
        )

        assertEquals(NotificationAccessDecision.DENIED_DEEP_LINK, decision)
    }

    @Test
    fun staff_deep_link_without_required_permission_is_denied() {
        val decision = NotificationDeepLinkAuthorization.authorizeOpen(
            NotificationDeepLinkAuthorization.DeepLinkOpenContext(authenticatedUserId = "staff-1"),
            NotificationDeepLink(
                routeType = NotificationDeepLinkRoute.MODERATION_QUEUE,
                requiredPermission = PermissionCode.MODERATION_VIEW.code
            )
        )

        assertEquals(NotificationAccessDecision.DENIED_PERMISSION, decision)
    }

    @Test
    fun deep_link_for_wrong_organization_is_denied() {
        val decision = NotificationDeepLinkAuthorization.authorizeOpen(
            NotificationDeepLinkAuthorization.DeepLinkOpenContext(
                authenticatedUserId = "user-1",
                organizationId = "org-current"
            ),
            NotificationDeepLink(
                routeType = NotificationDeepLinkRoute.ORGANIZATION,
                organizationId = "org-other"
            )
        )

        assertEquals(NotificationAccessDecision.DENIED_ORGANIZATION, decision)
    }

    @Test
    fun staff_flag_without_permission_does_not_grant_internal_access() {
        val decision = NotificationAuthorization.canViewInboxItem(
            NotificationAuthContext(actorUserId = "staff-1", isStaff = true),
            internalInboxItem(recipientUserId = "other-user")
        )

        assertEquals(NotificationAccessDecision.DENIED_INTERNAL, decision)
    }

    @Test
    fun internal_access_requires_staff_permission() {
        val decision = NotificationAuthorization.canViewInboxItem(
            NotificationAuthContext(
                actorUserId = "staff-1",
                isStaff = true,
                platformPermissions = setOf(PermissionCode.SUPPORT_VIEW)
            ),
            internalInboxItem(recipientUserId = "other-user")
        )

        assertEquals(NotificationAccessDecision.ALLOWED, decision)
    }

    @Test
    fun pending_navigation_rejects_redelivery_after_consume() {
        val link = NotificationDeepLink(NotificationDeepLinkRoute.SAFE_HOME)

        assertTrue(NotificationPendingNavigationStore.offer("n-1", link))
        assertEquals("n-1", NotificationPendingNavigationStore.consume()?.notificationId)
        assertFalse(NotificationPendingNavigationStore.offer("n-1", link))
    }

    @Test
    fun pending_navigation_rejects_double_tap_before_consume() {
        val link = NotificationDeepLink(NotificationDeepLinkRoute.SAFE_HOME)

        assertTrue(NotificationPendingNavigationStore.offer("n-1", link))
        assertFalse(NotificationPendingNavigationStore.offer("n-1", link))
    }

    @Test
    fun logout_clears_pending_navigation() {
        NotificationPendingNavigationStore.offer("n-1", NotificationDeepLink(NotificationDeepLinkRoute.SAFE_HOME))

        NotificationPendingNavigationStore.clear()

        assertNull(NotificationPendingNavigationStore.peek())
    }

    @Test
    fun account_change_clears_pending_navigation() {
        NotificationPendingNavigationStore.offer("n-1", NotificationDeepLink(NotificationDeepLinkRoute.SAFE_HOME))

        NotificationPendingNavigationStore.clearForAccountChange()

        assertNull(NotificationPendingNavigationStore.peek())
    }

    @Test
    fun disabled_push_preference_excludes_push_from_effective_channels() {
        val preference = preference(NotificationCategory.PET, pushEnabled = false)

        val channels = NotificationPreferenceRules.effectiveChannels(preference)

        assertTrue(NotificationChannel.IN_APP in channels)
        assertFalse(NotificationChannel.PUSH in channels)
    }

    @Test
    fun mandatory_in_app_category_cannot_be_disabled() {
        val invalid = preference(NotificationCategory.SECURITY, inAppEnabled = false)

        assertTrue(NotificationPreferenceRules.validate(invalid).isFailure)
        assertTrue(NotificationChannel.IN_APP in NotificationPreferenceRules.effectiveChannels(invalid))
    }

    @Test
    fun marketing_categories_are_off_by_default() {
        val preference = NotificationPreferenceRules.defaultFor(
            userId = "user-1",
            category = NotificationCategory.EVENT,
            now = now
        )

        assertFalse(preference.marketingConsent)
        assertEquals(
            setOf(NotificationChannel.IN_APP),
            NotificationPreferenceRules.effectiveChannels(preference)
        )
    }

    @Test
    fun quiet_hours_defers_across_dst_transition() {
        val quietHours = NotificationQuietHours(
            startLocalTime = LocalTime.of(1, 0),
            endLocalTime = LocalTime.of(4, 0),
            timezone = ZoneId.of("America/New_York")
        )
        val evaluation = NotificationQuietHoursRules.evaluate(
            quietHours = quietHours,
            at = Instant.parse("2026-03-08T07:00:00Z"),
            sensitivity = NotificationSensitivity.PRIVATE,
            categoryAllowsQuietHoursException = false
        )

        assertEquals(QuietHoursDecision.DEFER_UNTIL, evaluation.decision)
        assertEquals(Instant.parse("2026-03-08T08:00:00Z"), evaluation.deferUntil)
    }

    @Test
    fun denied_android_permission_never_blocks_in_app_usage() {
        assertFalse(NotificationPermissionCoordinator.blocksAppUsageWhenDenied())
    }

    @Test
    fun denied_permanent_and_can_ask_permission_states_remain_distinct() {
        assertNotEquals(
            NotificationPermissionCoordinator.PermissionState.DENIED_PERMANENT,
            NotificationPermissionCoordinator.PermissionState.DENIED_CAN_ASK
        )
        assertTrue(NotificationPermissionCoordinator.rationaleText().contains("bandeja in-app"))
    }

    @Test
    fun installation_register_rotate_revoke_current_and_keep_other_device() = runBlocking {
        val mocks = MockNotificationRepositories.create()
        val first = fingerprint("first-token")
        val second = fingerprint("second-token")
        val rotated = fingerprint("rotated-token")

        mocks.installation.registerInstallation("install-1", "user-1", NotificationInstallationPlatform.ANDROID, first, now)
        mocks.installation.registerInstallation("install-2", "user-1", NotificationInstallationPlatform.ANDROID, second, now)
        val afterRotate = mocks.installation.rotateToken("install-1", rotated, now) as AppResult.Success
        val revoked = mocks.installation.revokeCurrentInstallation("user-1", "install-1", now) as AppResult.Success
        val installations = (mocks.installation.listOwnInstallations("user-1") as AppResult.Success).data

        assertEquals(rotated, afterRotate.data.tokenFingerprint)
        assertFalse(revoked.data.isActive)
        assertTrue(installations.first { it.installationId == "install-2" }.isActive)
    }

    @Test
    fun installation_user_switch_rebinds_same_installation_to_new_user() = runBlocking {
        val mocks = MockNotificationRepositories.create()
        mocks.installation.registerInstallation(
            "install-1", "user-old", NotificationInstallationPlatform.ANDROID, fingerprint("old"), now
        )

        val switched = mocks.installation.registerInstallation(
            "install-1", "user-new", NotificationInstallationPlatform.ANDROID, fingerprint("new"), now
        ) as AppResult.Success

        assertEquals("user-new", switched.data.userId)
        assertTrue((mocks.installation.listOwnInstallations("user-old") as AppResult.Success).data.isEmpty())
    }

    @Test
    fun invalid_token_failure_is_permanent() = runBlocking {
        val mocks = MockNotificationRepositories.create()
        val delivery = plannedPushDelivery(mocks)

        mocks.delivery.recordAttempt(delivery, now)
        val failed = mocks.delivery.markFailure(delivery, "INVALID_TOKEN", now) as AppResult.Success

        assertEquals(NotificationDeliveryState.FAILED_PERMANENT, failed.data.state)
    }

    @Test
    fun registration_is_idempotent_for_same_fingerprint() = runBlocking {
        val mocks = MockNotificationRepositories.create()
        val token = fingerprint("same-token")

        val first = mocks.installation.registerInstallation(
            "install-1", "user-1", NotificationInstallationPlatform.ANDROID, token, now
        ) as AppResult.Success
        val second = mocks.installation.registerInstallation(
            "install-1", "user-1", NotificationInstallationPlatform.ANDROID, token, now
        ) as AppResult.Success

        assertEquals(first.data, second.data)
        assertEquals(1, mocks.store.installations.size)
    }

    @Test
    fun installation_model_never_exposes_raw_token_and_legacy_documentation_is_present() = runBlocking {
        val mocks = MockNotificationRepositories.create()
        val rawToken = "raw-fcm-token-should-never-leak"
        val installation = mocks.installation.registerInstallation(
            "install-1",
            "user-1",
            NotificationInstallationPlatform.ANDROID,
            fingerprint(rawToken),
            now,
            tokenReference = "protected-reference"
        ) as AppResult.Success
        val migration = repositoryFile("supabase/migrations/027_m06_push_delivery_and_installation_hardening.sql").readText()

        assertFalse(installation.data.toString().contains(rawToken))
        assertFalse(NotificationInstallationRules.containsRawTokenLeak(installation.data.toString(), rawToken))
        assertTrue(migration.contains("evitar doble envío"))
    }

    @Test
    fun transient_delivery_retries_then_moves_to_dead_letter() = runBlocking {
        val mocks = MockNotificationRepositories.create(
            retryPolicy = NotificationRetryPolicy(maxAttempts = 2, initialDelay = Duration.ofMillis(1))
        )
        val delivery = plannedPushDelivery(mocks)

        mocks.delivery.recordAttempt(delivery, now)
        val firstFailure = mocks.delivery.markFailure(delivery, "TRANSIENT", now) as AppResult.Success
        mocks.delivery.recordAttempt(delivery, now)
        val secondFailure = mocks.delivery.markFailure(delivery, "TRANSIENT", now) as AppResult.Success

        assertEquals(NotificationDeliveryState.FAILED_RETRYABLE, firstFailure.data.state)
        assertEquals(NotificationDeliveryState.DEAD_LETTER, secondFailure.data.state)
    }

    @Test
    fun delivered_push_never_marks_recipient_read() {
        assertFalse(NotificationDeliveryStateTransitions.deliveredPushMarksRecipientRead())
    }

    @Test
    fun channel_registry_maps_categories_and_preserves_legacy_default() {
        assertEquals(NotificationChannelRegistry.SECURITY, NotificationChannelRegistry.channelIdForCategory(NotificationCategory.SECURITY))
        assertEquals(NotificationChannelRegistry.SOCIAL_MESSAGES, NotificationChannelRegistry.channelIdForCategory(NotificationCategory.MESSAGE))
        assertEquals(NotificationChannelRegistry.SYSTEM, NotificationChannelRegistry.channelIdForCategory(null))
        assertTrue(NotificationChannelRegistry.LEGACY_DEFAULT in NotificationChannelRegistry.knownIds())
    }

    @Test
    fun channel_registry_unknown_id_falls_back_to_system() {
        assertEquals(NotificationChannelRegistry.SYSTEM, NotificationChannelRegistry.channelIdOrSystem("unknown-channel"))
    }

    @Test
    fun ui_error_mapper_sanitizes_bearer_tokens() {
        val sanitized = NotificationUiErrorMapper.sanitizeTechnical(
            "request failed Authorization: Bearer abc.def-123_secret"
        )

        assertTrue(sanitized.contains("Bearer [redacted]"))
        assertFalse(sanitized.contains("abc.def-123_secret"))
    }

    @Test
    fun deep_link_router_unknown_route_fallback_is_home() {
        assertEquals(NotificationDeepLinkRouter.unknownRouteFallback(), NotificationDeepLinkRouter.toNavRoute(
            NotificationDeepLink(NotificationDeepLinkRoute.SAFE_HOME)
        ))
    }

    @Test
    fun username_and_auth_sources_keep_stage_four_protected_markers() {
        val username = repositoryFile("app/src/main/java/com/comunidapp/app/domain/user/UsernameValidators.kt").readText()
        val authRepository = repositoryFile("app/src/main/java/com/comunidapp/app/data/repository/AuthRepository.kt").readText()

        assertTrue(username.contains("object UsernameValidators"))
        assertTrue(username.contains("\"login\""))
        assertTrue(authRepository.contains("interface AuthRepository"))
        assertTrue(authRepository.contains("suspend fun login"))
    }

    private suspend fun plannedPushDelivery(mocks: MockNotificationRepositories): String {
        val delivery = mocks.delivery.planDeliveries(
            event = notificationEvent(),
            recipientUserId = "user-1",
            channels = setOf(NotificationChannel.PUSH),
            installationIds = listOf("install-1"),
            now = now
        ) as AppResult.Success
        return delivery.data.single().deliveryId
    }

    private fun notificationEvent() = NotificationEvent(
        eventId = "event-1",
        eventKey = "m03.invitation.created",
        category = NotificationCategory.INVITATION,
        priority = NotificationPriority.NORMAL,
        sensitivity = NotificationSensitivity.PRIVATE,
        originModule = NotificationOriginModule.M03,
        originType = "INVITATION_CREATED",
        occurredAt = now,
        expiresAt = Instant.parse("2030-01-01T00:00:00Z"),
        payload = mapOf("title_key" to "title", "body_key" to "body"),
        deduplicationKey = "dedup-1",
        idempotencyKey = "idempotency-1"
    )

    private fun preference(
        category: NotificationCategory,
        inAppEnabled: Boolean = true,
        pushEnabled: Boolean = true
    ) = NotificationPreference(
        userId = "user-1",
        category = category,
        inAppEnabled = inAppEnabled,
        pushEnabled = pushEnabled,
        timezone = ZoneId.of("America/Argentina/Buenos_Aires"),
        updatedAt = now
    )

    private fun internalInboxItem(recipientUserId: String) = NotificationInboxItem(
        notificationId = "inbox-1",
        recipientUserId = recipientUserId,
        eventId = "event-1",
        category = NotificationCategory.SUPPORT,
        priority = NotificationPriority.NORMAL,
        sensitivity = NotificationSensitivity.SENSITIVE,
        state = NotificationState.UNREAD,
        deepLink = NotificationDeepLink(NotificationDeepLinkRoute.SAFE_HOME),
        titleKey = "title",
        bodyKey = "body",
        deduplicationKey = "dedup-1",
        isInternal = true,
        createdAt = now,
        updatedAt = now
    )

    private fun fingerprint(raw: String): String = NotificationInstallationRules.fingerprintOf(raw)

    private fun repositoryFile(relativePath: String): File {
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
}
