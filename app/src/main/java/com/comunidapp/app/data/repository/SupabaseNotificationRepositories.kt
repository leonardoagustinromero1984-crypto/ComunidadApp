package com.comunidapp.app.data.repository

import com.comunidapp.app.core.result.AppError
import com.comunidapp.app.core.result.AppErrorKind
import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.domain.notifications.NotificationCategory
import com.comunidapp.app.domain.notifications.NotificationChannel
import com.comunidapp.app.domain.notifications.NotificationDeepLink
import com.comunidapp.app.domain.notifications.NotificationDeepLinkRoute
import com.comunidapp.app.domain.notifications.NotificationDelivery
import com.comunidapp.app.domain.notifications.NotificationInboxItem
import com.comunidapp.app.domain.notifications.NotificationInstallation
import com.comunidapp.app.domain.notifications.NotificationInstallationPlatform
import com.comunidapp.app.domain.notifications.NotificationOutboxEvent
import com.comunidapp.app.domain.notifications.NotificationPreference
import com.comunidapp.app.domain.notifications.NotificationPreferenceRules
import com.comunidapp.app.domain.notifications.NotificationPriority
import com.comunidapp.app.domain.notifications.NotificationQuietHours
import com.comunidapp.app.domain.notifications.NotificationSensitivity
import com.comunidapp.app.domain.notifications.NotificationState
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class SupabaseNotificationInboxRepository : NotificationInboxRepository {

    override suspend fun listNotifications(userId: String): AppResult<List<NotificationInboxItem>> = runRpc {
        val element = rpc("m06_get_inbox", buildJsonObject {
            put("p_limit", 100)
            put("p_offset", 0)
        })
        M04SupabaseRpcSupport.decodeArray(element).mapNotNull { (it as? JsonObject)?.toInboxItem() }
    }

    override suspend fun getUnreadCount(userId: String): AppResult<Int> = runRpc {
        val element = rpc("m06_get_unread_count", buildJsonObject {})
        (element as? JsonPrimitive)?.intOrNull ?: 0
    }

    override suspend fun markRead(
        userId: String,
        notificationId: String,
        now: Instant
    ): AppResult<NotificationInboxItem> = mutateInbox("m06_mark_notification_read", notificationId)

    override suspend fun markAllRead(userId: String, now: Instant): AppResult<Int> = runRpc {
        val element = rpc("m06_mark_all_notifications_read", buildJsonObject {})
        (element as? JsonPrimitive)?.intOrNull ?: 0
    }

    override suspend fun archive(
        userId: String,
        notificationId: String,
        now: Instant
    ): AppResult<NotificationInboxItem> = mutateInbox("m06_archive_notification", notificationId)

    override suspend fun deleteLogical(
        userId: String,
        notificationId: String,
        now: Instant
    ): AppResult<NotificationInboxItem> = mutateInbox("m06_delete_notification_logical", notificationId)

    override suspend fun upsertFromEvent(
        userId: String,
        event: com.comunidapp.app.domain.notifications.NotificationEvent,
        deepLink: NotificationDeepLink,
        now: Instant
    ): AppResult<NotificationInboxItem> = denied("NOTIFICATION_CLIENT_MATERIALIZE_DENIED")

    private suspend fun mutateInbox(function: String, notificationId: String): AppResult<NotificationInboxItem> =
        runRpc {
            val element = rpc(function, buildJsonObject { put("p_notification_id", notificationId) })
            val obj = M04SupabaseRpcSupport.decodeObject(element)
                ?: error("NOTIFICATION_EMPTY")
            obj.toInboxItem() ?: error("NOTIFICATION_PARSE_FAILED")
        }
}

class SupabaseNotificationPreferenceRepository : NotificationPreferenceRepository {

    override suspend fun getPreferences(userId: String): AppResult<List<NotificationPreference>> = runRpc {
        val element = rpc("m06_get_preferences", buildJsonObject {})
        M04SupabaseRpcSupport.decodeArray(element).mapNotNull { (it as? JsonObject)?.toPreference() }
    }

    override suspend fun updatePreference(preference: NotificationPreference): AppResult<NotificationPreference> = runRpc {
        val qh = preference.quietHours
        val element = rpc(
            "m06_update_preference",
            buildJsonObject {
                put("p_category", preference.category.name)
                put("p_in_app_enabled", preference.inAppEnabled)
                put("p_push_enabled", preference.pushEnabled)
                put("p_email_enabled", preference.emailEnabled)
                put("p_marketing_consent", preference.marketingConsent)
                put("p_timezone", preference.timezone.id)
                if (qh != null) {
                    put("p_quiet_hours_start", qh.startLocalTime.toString())
                    put("p_quiet_hours_end", qh.endLocalTime.toString())
                }
            }
        )
        M04SupabaseRpcSupport.decodeObject(element)?.toPreference()
            ?: error("PREFERENCE_EMPTY")
    }

    override suspend fun getEffectiveChannels(
        userId: String,
        category: NotificationCategory
    ): AppResult<Set<NotificationChannel>> {
        val prefs = getPreferences(userId)
        return when (prefs) {
            is AppResult.Success -> {
                val preference = prefs.data.firstOrNull { it.category == category }
                    ?: NotificationPreferenceRules.defaultFor(userId, category)
                AppResult.Success(NotificationPreferenceRules.effectiveChannels(preference, category))
            }
            is AppResult.Failure -> prefs
        }
    }
}

class SupabaseNotificationInstallationRepository : NotificationInstallationRepository {

    override suspend fun registerInstallation(
        installationId: String,
        userId: String,
        platform: NotificationInstallationPlatform,
        tokenFingerprint: String,
        now: Instant,
        appVersion: String?,
        deviceLabel: String?
    ): AppResult<NotificationInstallation> = runRpc {
        val element = rpc(
            "m06_register_installation",
            buildJsonObject {
                put("p_installation_id", installationId)
                put("p_platform", platform.name)
                put("p_token_fingerprint", tokenFingerprint)
                appVersion?.let { put("p_app_version", it) }
                deviceLabel?.let { put("p_device_label", it) }
            }
        )
        M04SupabaseRpcSupport.decodeObject(element)?.toInstallation()
            ?: error("INSTALLATION_EMPTY")
    }

    override suspend fun rotateToken(
        installationId: String,
        newFingerprint: String,
        now: Instant
    ): AppResult<NotificationInstallation> = runRpc {
        val element = rpc(
            "m06_rotate_installation_token",
            buildJsonObject {
                put("p_installation_id", installationId)
                put("p_token_fingerprint", newFingerprint)
            }
        )
        M04SupabaseRpcSupport.decodeObject(element)?.toInstallation()
            ?: error("INSTALLATION_EMPTY")
    }

    override suspend fun revokeCurrentInstallation(
        userId: String,
        installationId: String,
        now: Instant
    ): AppResult<NotificationInstallation> = runRpc {
        val element = rpc(
            "m06_revoke_current_installation",
            buildJsonObject { put("p_installation_id", installationId) }
        )
        M04SupabaseRpcSupport.decodeObject(element)?.toInstallation()
            ?: error("INSTALLATION_EMPTY")
    }

    override suspend fun listOwnInstallations(userId: String): AppResult<List<NotificationInstallation>> =
        denied("NOTIFICATION_INSTALLATION_LIST_NOT_EXPOSED")
}

class ClientDeniedNotificationDeliveryRepository : NotificationDeliveryRepository {
    override suspend fun planDeliveries(
        event: com.comunidapp.app.domain.notifications.NotificationEvent,
        recipientUserId: String,
        channels: Set<NotificationChannel>,
        installationIds: List<String>,
        now: Instant
    ): AppResult<List<NotificationDelivery>> = denied("NOTIFICATION_DELIVERY_CLIENT_DENIED")

    override suspend fun recordAttempt(deliveryId: String, now: Instant): AppResult<NotificationDelivery> =
        denied("NOTIFICATION_DELIVERY_CLIENT_DENIED")

    override suspend fun markDelivered(deliveryId: String, now: Instant): AppResult<NotificationDelivery> =
        denied("NOTIFICATION_DELIVERY_CLIENT_DENIED")

    override suspend fun markFailure(
        deliveryId: String,
        failureCode: String,
        now: Instant
    ): AppResult<NotificationDelivery> =
        denied("NOTIFICATION_DELIVERY_CLIENT_DENIED")
}

class ClientDeniedNotificationOutboxRepository : NotificationOutboxRepository {
    override suspend fun enqueueEvent(
        event: com.comunidapp.app.domain.notifications.NotificationEvent,
        now: Instant
    ): AppResult<NotificationOutboxEvent> = denied("NOTIFICATION_OUTBOX_CLIENT_DENIED")

    override suspend fun claimNext(now: Instant): AppResult<NotificationOutboxEvent?> =
        denied("NOTIFICATION_OUTBOX_CLIENT_DENIED")

    override suspend fun markProcessed(outboxId: String, now: Instant): AppResult<NotificationOutboxEvent> =
        denied("NOTIFICATION_OUTBOX_CLIENT_DENIED")

    override suspend fun markFailed(
        outboxId: String,
        failureCode: String,
        now: Instant
    ): AppResult<NotificationOutboxEvent> = denied("NOTIFICATION_OUTBOX_CLIENT_DENIED")

    override suspend fun moveToDeadLetter(outboxId: String, now: Instant): AppResult<NotificationOutboxEvent> =
        denied("NOTIFICATION_OUTBOX_CLIENT_DENIED")
}

private suspend fun rpc(function: String, parameters: JsonObject): JsonElement =
    supabase.postgrest.rpc(function = function, parameters = parameters).decodeAs()

private suspend fun <T> runRpc(block: suspend () -> T): AppResult<T> =
    M04SupabaseRpcSupport.runRpc(block)

private fun <T> denied(code: String): AppResult<T> =
    AppResult.Failure(
        AppError(
            kind = AppErrorKind.FORBIDDEN,
            userMessage = "No tenés permiso para esta acción.",
            technicalMessage = code,
            code = code
        )
    )

private fun JsonObject.toInboxItem(): NotificationInboxItem? {
    val route = NotificationDeepLinkRoute.fromString(string("deep_link_type")) ?: NotificationDeepLinkRoute.SAFE_HOME
    val link = NotificationDeepLink(
        routeType = route,
        resourceType = string("deep_link_resource_type") ?: string("related_type"),
        resourceId = string("deep_link_resource_id") ?: string("related_id"),
        organizationId = string("organization_id"),
        requiredPermission = string("deep_link_required_permission")
    )
    return NotificationInboxItem(
        notificationId = requireString("id"),
        recipientUserId = requireString("user_id"),
        eventId = string("event_id") ?: requireString("id"),
        category = enumOrDefault(string("category"), NotificationCategory.OTHER),
        priority = enumOrDefault(string("priority"), NotificationPriority.NORMAL),
        sensitivity = enumOrDefault(string("sensitivity"), NotificationSensitivity.PRIVATE),
        state = enumOrDefault(string("state"), NotificationState.UNREAD),
        deepLink = link,
        titleKey = string("title") ?: "notification.title",
        bodyKey = string("body") ?: "notification.body",
        deduplicationKey = string("deduplication_key") ?: "legacy:${requireString("id")}",
        organizationId = string("organization_id"),
        resourceType = string("related_type"),
        resourceId = string("related_id"),
        isInternal = boolean("is_internal") ?: false,
        createdAt = instant("created_at"),
        updatedAt = instant("updated_at"),
        readAt = instantOrNull("read_at"),
        expiresAt = instantOrNull("expires_at")
    )
}

private fun JsonObject.toPreference(): NotificationPreference? {
    val category = enumOrDefault(string("category"), NotificationCategory.OTHER)
    val timezone = runCatching { ZoneId.of(string("timezone") ?: "UTC") }.getOrDefault(ZoneId.of("UTC"))
    val start = string("quiet_hours_start")?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
    val end = string("quiet_hours_end")?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
    val quietHours = if (start != null && end != null) {
        NotificationQuietHours(
            startLocalTime = start,
            endLocalTime = end,
            timezone = timezone,
            daysOfWeek = setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
                DayOfWeek.SATURDAY,
                DayOfWeek.SUNDAY
            )
        )
    } else {
        null
    }
    return NotificationPreference(
        userId = requireString("user_id"),
        category = category,
        inAppEnabled = boolean("in_app_enabled") ?: true,
        pushEnabled = boolean("push_enabled") ?: true,
        emailEnabled = boolean("email_enabled") ?: false,
        quietHours = quietHours,
        timezone = timezone,
        marketingConsent = boolean("marketing_consent") ?: false,
        updatedAt = instant("updated_at")
    )
}

private fun JsonObject.toInstallation(): NotificationInstallation? =
    NotificationInstallation(
        installationId = requireString("installation_id"),
        userId = requireString("user_id"),
        platform = NotificationInstallationPlatform.fromString(string("platform")),
        tokenFingerprint = requireString("token_fingerprint"),
        enabled = boolean("enabled") ?: false,
        appVersion = string("app_version"),
        deviceLabel = string("device_label"),
        lastSeenAt = instant("last_seen_at"),
        revokedAt = instantOrNull("revoked_at")
    )

private fun JsonObject.string(key: String): String? =
    when (val element = this[key]) {
        is JsonNull, null -> null
        is JsonPrimitive -> element.contentOrNull
        else -> element.toString()
    }

private fun JsonObject.boolean(key: String): Boolean? =
    (this[key] as? JsonPrimitive)?.booleanOrNull

private fun JsonObject.requireString(key: String): String =
    string(key)?.takeIf { it.isNotBlank() } ?: error("MISSING_$key")

private fun JsonObject.instant(key: String): Instant =
    instantOrNull(key) ?: Instant.EPOCH

private fun JsonObject.instantOrNull(key: String): Instant? =
    string(key)?.let { runCatching { Instant.parse(it) }.getOrNull() }

private inline fun <reified T : Enum<T>> enumOrDefault(raw: String?, default: T): T =
    runCatching { enumValueOf<T>(raw?.trim()?.uppercase().orEmpty()) }.getOrDefault(default)
