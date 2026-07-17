package com.comunidapp.app.notifications

import com.comunidapp.app.domain.notifications.NotificationCategory
import com.comunidapp.app.domain.notifications.NotificationDeepLink
import com.comunidapp.app.domain.notifications.NotificationDeepLinkRoute
import com.comunidapp.app.domain.notifications.NotificationDeepLinkRules
import com.comunidapp.app.domain.notifications.NotificationPriority
import com.comunidapp.app.domain.notifications.NotificationSensitivity
import com.comunidapp.app.domain.notifications.NotificationSensitivityRules

/**
 * Parser tipado del payload FCM M06. Rechaza secretos, signed URLs, tokens y PII.
 */
data class NotificationPushPayload(
    val notificationId: String,
    val deliveryId: String?,
    val category: NotificationCategory,
    val priority: NotificationPriority,
    val sensitivity: NotificationSensitivity,
    val deepLink: NotificationDeepLink,
    val title: String,
    val body: String
)

object NotificationPushPayloadParser {

    private val forbiddenKeys = setOf(
        "token", "fcm_token", "signed_url", "signedurl", "secret", "password",
        "authorization", "apikey", "api_key", "private_key", "sql", "stack",
        "stack_trace", "provider_credentials", "email", "phone", "dni", "cuit"
    )

    private val forbiddenValue = Regex(
        "(?i)(https?://|signed[_-]?url|Bearer\\s+|-----BEGIN|eyJ[A-Za-z0-9_-]{20,}|SELECT\\s+|INSERT\\s+|UPDATE\\s+|DELETE\\s+)"
    )

    fun parse(
        data: Map<String, String>,
        notificationTitle: String? = null,
        notificationBody: String? = null
    ): Result<NotificationPushPayload> {
        for ((key, value) in data) {
            if (key.lowercase() in forbiddenKeys) {
                return Result.failure(IllegalArgumentException("PUSH_FORBIDDEN_FIELD:$key"))
            }
            if (forbiddenValue.containsMatchIn(value)) {
                return Result.failure(IllegalArgumentException("PUSH_FORBIDDEN_VALUE:$key"))
            }
        }

        val notificationId = data["notification_id"]?.trim().orEmpty()
        if (notificationId.isBlank()) {
            return Result.failure(IllegalArgumentException("PUSH_NOTIFICATION_ID_REQUIRED"))
        }

        val category = NotificationCategory.fromString(data["category"])
            ?: return Result.failure(IllegalArgumentException("PUSH_CATEGORY_INVALID"))
        val priority = NotificationPriority.fromString(data["priority"])
            ?: NotificationPriority.NORMAL
        val sensitivity = NotificationSensitivity.fromString(data["sensitivity"])
            ?: NotificationSensitivity.PRIVATE

        val routeRaw = data["deep_link_type"]?.trim().orEmpty()
        val route = NotificationDeepLinkRoute.fromString(routeRaw)
            ?: NotificationDeepLinkRoute.SAFE_HOME

        val link = NotificationDeepLink(
            routeType = route,
            resourceType = data["resource_type"]?.trim()?.ifBlank { null },
            resourceId = data["resource_id"]?.trim()?.ifBlank { null },
            organizationId = data["organization_id"]?.trim()?.ifBlank { null },
            requiredPermission = data["required_permission"]?.trim()?.ifBlank { null }
        )
        NotificationDeepLinkRules.validate(link).getOrElse {
            return Result.failure(IllegalArgumentException(it.message ?: "PUSH_DEEP_LINK_INVALID"))
        }

        val rawTitle = notificationTitle?.takeIf { it.isNotBlank() }
            ?: data["title"]?.takeIf { it.isNotBlank() }
            ?: "LeoVer"
        val rawBody = notificationBody?.takeIf { it.isNotBlank() }
            ?: data["body"]?.takeIf { it.isNotBlank() }
            ?: NotificationSensitivityRules.GENERIC_PUSH_BODY

        if (forbiddenValue.containsMatchIn(rawTitle) || forbiddenValue.containsMatchIn(rawBody)) {
            return Result.failure(IllegalArgumentException("PUSH_COPY_FORBIDDEN"))
        }

        val (safeTitle, safeBody) = NotificationSensitivityRules.resolvePushCopy(
            sensitivity, rawTitle, rawBody
        )

        return Result.success(
            NotificationPushPayload(
                notificationId = notificationId,
                deliveryId = data["delivery_id"]?.trim()?.ifBlank { null },
                category = category,
                priority = priority,
                sensitivity = sensitivity,
                deepLink = link,
                title = safeTitle,
                body = safeBody
            )
        )
    }
}
