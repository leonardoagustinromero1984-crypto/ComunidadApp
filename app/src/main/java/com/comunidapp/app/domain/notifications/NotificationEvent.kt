package com.comunidapp.app.domain.notifications

import java.time.Instant

/**
 * Módulos de origen allowlisted para eventos M06 Etapa 2 (M01–M05).
 */
enum class NotificationOriginModule {
    M01,
    M02,
    M03,
    M04,
    M05;

    companion object {
        fun fromString(raw: String?): NotificationOriginModule? =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) }
    }
}

/**
 * Evento tipado de notificación. No autoriza envío real.
 */
data class NotificationEvent(
    val eventId: String,
    val eventKey: String,
    val category: NotificationCategory,
    val priority: NotificationPriority,
    val sensitivity: NotificationSensitivity,
    val originModule: NotificationOriginModule,
    val originType: String,
    val resourceType: String? = null,
    val resourceId: String? = null,
    val organizationId: String? = null,
    val occurredAt: Instant,
    val expiresAt: Instant? = null,
    val payload: Map<String, String> = emptyMap(),
    val deduplicationKey: String,
    val idempotencyKey: String,
    val isInternal: Boolean = false
)

object NotificationEventRules {

    private val idPattern = Regex("^[A-Za-z0-9_\\-.:]{1,128}$")
    private val keyPattern = Regex("^[A-Za-z0-9_\\-.:|]{1,256}$")

    private val allowedPayloadKeys: Set<String> = setOf(
        "title_key",
        "body_key",
        "title",
        "body",
        "summary",
        "actor_user_id",
        "resource_label",
        "status",
        "action",
        "count"
    )

    private val forbiddenKeyFragments: Set<String> = setOf(
        "token",
        "secret",
        "password",
        "signed_url",
        "signedurl",
        "authorization",
        "apikey",
        "api_key",
        "stack",
        "sql",
        "email",
        "phone",
        "dni",
        "ssn",
        "credit_card",
        "card_number"
    )

    private val forbiddenValuePatterns: List<Regex> = listOf(
        Regex("(?i)https?://"),
        Regex("(?i)signed[-_]?url"),
        Regex("(?i)bearer\\s+"),
        Regex("(?i)eyJ[A-Za-z0-9_-]+\\."), // JWT-ish
        Regex("(?i)(password|secret|token)\\s*=")
    )

    fun validate(event: NotificationEvent, now: Instant = Instant.now()): Result<NotificationEvent> {
        if (event.eventId.isBlank() || !idPattern.matches(event.eventId.trim())) {
            return Result.failure(IllegalArgumentException("EVENT_ID_INVALID"))
        }
        if (event.eventKey.isBlank() || !keyPattern.matches(event.eventKey.trim())) {
            return Result.failure(IllegalArgumentException("EVENT_KEY_INVALID"))
        }
        if (event.idempotencyKey.isBlank() || !keyPattern.matches(event.idempotencyKey.trim())) {
            return Result.failure(IllegalArgumentException("IDEMPOTENCY_KEY_INVALID"))
        }
        if (event.deduplicationKey.isBlank() || !keyPattern.matches(event.deduplicationKey.trim())) {
            return Result.failure(IllegalArgumentException("DEDUPLICATION_KEY_INVALID"))
        }
        if (event.originType.isBlank()) {
            return Result.failure(IllegalArgumentException("ORIGIN_TYPE_REQUIRED"))
        }
        if (NotificationOriginModule.fromString(event.originModule.name) == null) {
            return Result.failure(IllegalArgumentException("ORIGIN_MODULE_NOT_ALLOWLISTED"))
        }
        if (isUrlLike(event.resourceType) || isUrlLike(event.resourceId)) {
            return Result.failure(IllegalArgumentException("RESOURCE_URL_REJECTED"))
        }
        event.resourceType?.let {
            if (!idPattern.matches(it.trim())) {
                return Result.failure(IllegalArgumentException("RESOURCE_TYPE_INVALID"))
            }
        }
        event.resourceId?.let {
            if (!idPattern.matches(it.trim())) {
                return Result.failure(IllegalArgumentException("RESOURCE_ID_INVALID"))
            }
        }
        event.organizationId?.let {
            if (!idPattern.matches(it.trim())) {
                return Result.failure(IllegalArgumentException("ORGANIZATION_ID_INVALID"))
            }
        }
        validatePayload(event.payload).getOrElse { return Result.failure(it) }

        if (event.expiresAt != null && !event.expiresAt.isAfter(event.occurredAt)) {
            return Result.failure(IllegalArgumentException("EXPIRES_BEFORE_OCCURRED"))
        }
        if (isExpired(event, now)) {
            return Result.failure(IllegalArgumentException("EVENT_EXPIRED"))
        }
        if (event.isInternal && event.sensitivity == NotificationSensitivity.PUBLIC_SUMMARY) {
            return Result.failure(IllegalArgumentException("INTERNAL_PUBLIC_SUMMARY_FORBIDDEN"))
        }
        val policy = NotificationCategoryPolicies.forCategory(event.category)
        if (event.priority == NotificationPriority.URGENT &&
            event.category !in setOf(
                NotificationCategory.SECURITY,
                NotificationCategory.ACCOUNT,
                NotificationCategory.MODERATION,
                NotificationCategory.LOST_FOUND
            )
        ) {
            return Result.failure(IllegalArgumentException("URGENT_NOT_ALLOWED_FOR_CATEGORY"))
        }
        if (!policy.allowsLockScreen &&
            event.sensitivity == NotificationSensitivity.PUBLIC_SUMMARY
        ) {
            // soft consistency: prefer SENSITIVE defaults from policy — not hard fail
        }
        return Result.success(event)
    }

    fun isExpired(event: NotificationEvent, now: Instant): Boolean =
        event.expiresAt != null && !event.expiresAt.isAfter(now)

    /** Evento expirado no genera delivery. */
    fun mayGenerateDelivery(event: NotificationEvent, now: Instant): Boolean =
        !isExpired(event, now)

    fun validatePayload(payload: Map<String, String>): Result<Map<String, String>> {
        for ((rawKey, rawValue) in payload) {
            val key = rawKey.trim().lowercase()
            if (key.isEmpty()) {
                return Result.failure(IllegalArgumentException("PAYLOAD_KEY_BLANK"))
            }
            if (key !in allowedPayloadKeys) {
                return Result.failure(IllegalArgumentException("PAYLOAD_KEY_NOT_ALLOWLISTED:$key"))
            }
            if (forbiddenKeyFragments.any { key.contains(it) }) {
                return Result.failure(IllegalArgumentException("PAYLOAD_KEY_FORBIDDEN:$key"))
            }
            val value = rawValue.trim()
            if (forbiddenValuePatterns.any { it.containsMatchIn(value) }) {
                return Result.failure(IllegalArgumentException("PAYLOAD_VALUE_FORBIDDEN"))
            }
            if (value.length > 500) {
                return Result.failure(IllegalArgumentException("PAYLOAD_VALUE_TOO_LONG"))
            }
        }
        return Result.success(payload)
    }

    private fun isUrlLike(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        val v = value.trim()
        return v.contains("://") || v.startsWith("http", ignoreCase = true) ||
            v.startsWith("www.", ignoreCase = true)
    }
}
