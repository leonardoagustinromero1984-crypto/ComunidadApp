package com.comunidapp.app.data.model

import com.comunidapp.app.domain.m27.M27PrivacySanitizer

/** LeoVer M27 Bloque 3 — operaciones de integración (sin pagos M24). */
enum class M27IntegrationAppStatus { DRAFT, ACTIVE, PAUSED, REVOKED, SUSPENDED, ARCHIVED }

enum class M27WebhookDeliveryStatus {
    PENDING, DELIVERING, DELIVERED, RETRY_SCHEDULED, FAILED, DEAD_LETTER, CANCELLED
}

enum class M27WebhookEventType {
    PET_UPDATED,
    ADOPTION_PUBLISHED,
    ADOPTION_STATUS_CHANGED,
    EVENT_PUBLISHED,
    BOOKING_STATUS_CHANGED,
    ORDER_STATUS_CHANGED,
    REVIEW_PUBLISHED
}

object M27ApiScopes {
    val ALLOWLIST = setOf(
        "pets.read.public",
        "adoptions.read.public",
        "events.read.public",
        "providers.read.public",
        "marketplace.read.public",
        "bookings.read.own",
        "webhooks.manage",
        "sandbox.execute"
    )

    fun isAllowed(scope: String): Boolean = scope in ALLOWLIST
}

data class M27IntegrationApp(
    val id: String,
    val ownerUserId: String,
    val organizationId: String,
    val name: String,
    val contractVersion: M27ContractVersion,
    val grantedScopes: List<String>,
    val status: M27IntegrationAppStatus,
    val environment: M27Environment,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toPublic(): M27PublicIntegrationApp = M27PrivacySanitizer.toPublicApp(this)
}

data class M27PublicIntegrationApp(
    val name: String,
    val contractVersion: M27ContractVersion,
    val grantedScopes: List<String>,
    val status: M27IntegrationAppStatus,
    val environment: M27Environment
)

data class M27IssuedCredential(
    val credential: M27ApiCredential,
    /** Solo disponible inmediatamente tras creación/rotación; nunca en listados. */
    val plaintextKeyOnce: String?
)

data class M27WebhookSubscription(
    val id: String,
    val endpointId: String,
    val eventType: M27WebhookEventType,
    val active: Boolean,
    val createdAt: Long
)

data class M27WebhookEvent(
    val id: String,
    val appId: String,
    val eventType: M27WebhookEventType,
    val version: String,
    val resourceRef: String,
    val sanitizedPayload: String,
    val occurredAt: Long
) {
    fun toPublic(): M27PublicWebhookEvent = M27PublicWebhookEvent(
        eventType = eventType,
        version = version,
        resourceRef = resourceRef,
        sanitizedPayload = sanitizedPayload,
        occurredAt = occurredAt
    )
}

data class M27PublicWebhookEvent(
    val eventType: M27WebhookEventType,
    val version: String,
    val resourceRef: String,
    val sanitizedPayload: String,
    val occurredAt: Long
)

data class M27WebhookDelivery(
    val id: String,
    val eventId: String,
    val subscriptionId: String,
    val endpointId: String,
    val status: M27WebhookDeliveryStatus,
    val attemptCount: Int,
    val maxAttempts: Int,
    val signatureVersion: String,
    val signatureDigest: String,
    val lastAttemptAt: Long?,
    val createdAt: Long
) {
    fun toPublic(): M27PublicWebhookDelivery = M27PublicWebhookDelivery(
        status = status,
        attemptCount = attemptCount,
        maxAttempts = maxAttempts,
        signatureVersion = signatureVersion
    )
}

data class M27PublicWebhookDelivery(
    val status: M27WebhookDeliveryStatus,
    val attemptCount: Int,
    val maxAttempts: Int,
    val signatureVersion: String
)

data class M27WebhookAttempt(
    val id: String,
    val deliveryId: String,
    val attemptNumber: Int,
    val outcome: String,
    val sanitizedError: String?,
    val createdAt: Long
)

data class M27RateLimitResult(
    val allowed: Boolean,
    val reason: String?,
    val retryAfterSeconds: Long?
)

data class M27AuditEntry(
    val id: String,
    val actorUserId: String,
    val appId: String?,
    val operation: String,
    val outcome: String,
    val environment: M27Environment,
    val sanitizedReason: String?,
    val createdAt: Long
) {
    fun toPublic(): M27PublicAuditEntry = M27PublicAuditEntry(
        operation = operation,
        outcome = outcome,
        environment = environment,
        sanitizedReason = sanitizedReason,
        createdAt = createdAt
    )
}

data class M27PublicAuditEntry(
    val operation: String,
    val outcome: String,
    val environment: M27Environment,
    val sanitizedReason: String?,
    val createdAt: Long
)

data class CreateM27IntegrationAppInput(
    val name: String,
    val organizationId: String,
    val contractVersion: M27ContractVersion,
    val requestedScopes: List<String>,
    val environment: M27Environment,
    val clientRequestId: String? = null
)

data class CreateM27ApiKeyInput(
    val appId: String,
    val label: String,
    val scopes: List<String>,
    val environment: M27Environment,
    val clientRequestId: String? = null
)

data class RegisterM27WebhookEndpointInput(
    val appId: String,
    val label: String,
    val targetUrl: String,
    val environment: M27Environment,
    val clientRequestId: String? = null
)

data class SubscribeM27WebhookInput(val endpointId: String, val eventType: M27WebhookEventType)
data class EmitM27WebhookEventInput(
    val appId: String,
    val eventType: M27WebhookEventType,
    val resourceRef: String,
    val sanitizedPayload: String,
    val clientRequestId: String? = null
)

data class M27OAuthStubSession(
    val state: String,
    val redirectUri: String,
    val scopes: List<String>,
    val stubTokenPrefix: String,
    val expiresAt: Long
)

object M27MockOrg {
    const val DEV_ORG = "mock_org_developer"
    const val OTHER_ORG = "mock_org_other"
}
