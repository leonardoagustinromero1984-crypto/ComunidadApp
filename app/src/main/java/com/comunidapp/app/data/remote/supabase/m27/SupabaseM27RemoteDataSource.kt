package com.comunidapp.app.data.remote.supabase.m27

import com.comunidapp.app.data.model.M27ApiCredential
import com.comunidapp.app.data.model.M27ApiKeyStatus
import com.comunidapp.app.data.model.M27Environment
import com.comunidapp.app.data.model.M27OAuthAppStatus
import com.comunidapp.app.data.model.M27OAuthApplication
import com.comunidapp.app.data.model.M27PublicApiKey
import com.comunidapp.app.data.model.M27PublicContract
import com.comunidapp.app.data.model.M27PublicOAuthApp
import com.comunidapp.app.data.model.M27PublicRateLimit
import com.comunidapp.app.data.model.M27PublicWebhook
import com.comunidapp.app.data.model.M27WebhookEndpoint
import com.comunidapp.app.data.model.M27WebhookStatus
import com.comunidapp.app.data.model.M27ContractVersion
import com.comunidapp.app.data.remote.supabase.supabase
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

private fun JsonElement?.stringOrNull(): String? =
    (this as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

private fun JsonElement?.intOrNull(): Int? =
    (this as? JsonPrimitive)?.contentOrNull?.toIntOrNull()

private fun JsonObject.string(key: String): String? = this[key].stringOrNull()
private fun JsonObject.int(key: String): Int? = this[key].intOrNull()
private fun JsonObject.boolean(key: String, default: Boolean = false): Boolean =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() ?: default

private fun parseTimestamp(value: String?): Long =
    value?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
        ?: System.currentTimeMillis()

private inline fun <reified T : Enum<T>> enumOr(raw: String?, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == raw } ?: fallback

private fun JsonObject.scopesList(): List<String> = when (val raw = this["scopes"]) {
    is JsonArray -> raw.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    else -> emptyList()
}

fun JsonObject.toM27PublicWebhook(): M27PublicWebhook = M27PublicWebhook(
    label = string("label").orEmpty(),
    targetUrl = string("target_url").orEmpty(),
    secretPrefix = string("secret_prefix").orEmpty(),
    status = enumOr(string("status"), M27WebhookStatus.ACTIVE),
    environment = enumOr(string("environment"), M27Environment.SANDBOX)
)

fun JsonObject.toM27PublicOAuthApp(): M27PublicOAuthApp = M27PublicOAuthApp(
    name = string("name").orEmpty(),
    redirectUri = string("redirect_uri").orEmpty(),
    clientIdPrefix = string("client_id_prefix").orEmpty(),
    scopes = scopesList(),
    status = enumOr(string("status"), M27OAuthAppStatus.ACTIVE),
    environment = enumOr(string("environment"), M27Environment.SANDBOX)
)

fun JsonObject.toM27PublicApiKey(): M27PublicApiKey = M27PublicApiKey(
    label = string("label").orEmpty(),
    keyPrefix = string("key_prefix").orEmpty(),
    scopes = scopesList(),
    status = enumOr(string("status"), M27ApiKeyStatus.ACTIVE),
    environment = enumOr(string("environment"), M27Environment.SANDBOX)
)

fun JsonObject.toM27PublicContract(): M27PublicContract = M27PublicContract(
    title = string("title").orEmpty(),
    version = enumOr(string("version"), M27ContractVersion.V1),
    summary = string("summary").orEmpty(),
    publishedForDisplay = boolean("published_for_display")
)

fun JsonObject.toM27PublicRateLimit(): M27PublicRateLimit = M27PublicRateLimit(
    environment = enumOr(string("environment"), M27Environment.SANDBOX),
    requestsPerMinute = int("requests_per_minute") ?: 0,
    requestsPerDay = int("requests_per_day") ?: 0
)

fun JsonObject.toM27WebhookEndpoint(): M27WebhookEndpoint = M27WebhookEndpoint(
    id = string("id").orEmpty(),
    ownerUserId = string("owner_user_id").orEmpty(),
    appId = string("app_id"),
    label = string("label").orEmpty(),
    targetUrl = string("target_url").orEmpty(),
    secretPrefix = string("secret_prefix").orEmpty(),
    secretHash = string("secret_hash").orEmpty(),
    status = enumOr(string("status"), M27WebhookStatus.ACTIVE),
    environment = enumOr(string("environment"), M27Environment.SANDBOX),
    createdAt = parseTimestamp(string("created_at")),
    updatedAt = parseTimestamp(string("updated_at"))
)

fun JsonObject.toM27OAuthApplication(): M27OAuthApplication = M27OAuthApplication(
    id = string("id").orEmpty(),
    ownerUserId = string("owner_user_id").orEmpty(),
    name = string("name").orEmpty(),
    redirectUri = string("redirect_uri").orEmpty(),
    clientIdPrefix = string("client_id_prefix").orEmpty(),
    scopes = scopesList(),
    status = enumOr(string("status"), M27OAuthAppStatus.ACTIVE),
    environment = enumOr(string("environment"), M27Environment.SANDBOX),
    createdAt = parseTimestamp(string("created_at")),
    updatedAt = parseTimestamp(string("updated_at"))
)

fun JsonObject.toM27ApiCredential(): M27ApiCredential = M27ApiCredential(
    id = string("id").orEmpty(),
    ownerUserId = string("owner_user_id").orEmpty(),
    appId = string("app_id"),
    label = string("label").orEmpty(),
    keyPrefix = string("key_prefix").orEmpty(),
    keyHash = string("key_hash").orEmpty(),
    scopes = scopesList(),
    status = enumOr(string("status"), M27ApiKeyStatus.ACTIVE),
    environment = enumOr(string("environment"), M27Environment.SANDBOX),
    createdAt = parseTimestamp(string("created_at")),
    expiresAt = string("expires_at")?.let(::parseTimestamp)
)

fun JsonObject.toM27PublicIntegrationApp(): com.comunidapp.app.data.model.M27PublicIntegrationApp =
    com.comunidapp.app.data.model.M27PublicIntegrationApp(
        name = string("name").orEmpty(),
        contractVersion = enumOr(string("contract_version"), com.comunidapp.app.data.model.M27ContractVersion.V1),
        grantedScopes = scopesList(),
        status = enumOr(string("status"), com.comunidapp.app.data.model.M27IntegrationAppStatus.DRAFT),
        environment = enumOr(string("environment"), M27Environment.SANDBOX)
    )

fun JsonObject.toM27PublicWebhookDelivery(): com.comunidapp.app.data.model.M27PublicWebhookDelivery =
    com.comunidapp.app.data.model.M27PublicWebhookDelivery(
        status = enumOr(string("status"), com.comunidapp.app.data.model.M27WebhookDeliveryStatus.PENDING),
        attemptCount = int("attempt_count") ?: 0,
        maxAttempts = int("max_attempts") ?: 3,
        signatureVersion = string("signature_version").orEmpty()
    )

fun JsonObject.toM27PublicWebhookEvent(): com.comunidapp.app.data.model.M27PublicWebhookEvent =
    com.comunidapp.app.data.model.M27PublicWebhookEvent(
        eventType = enumOr(string("event_type"), com.comunidapp.app.data.model.M27WebhookEventType.ADOPTION_PUBLISHED),
        version = string("version").orEmpty(),
        resourceRef = string("resource_ref").orEmpty(),
        sanitizedPayload = string("sanitized_payload").orEmpty(),
        occurredAt = parseTimestamp(string("occurred_at"))
    )

fun JsonObject.toM27PublicAuditEntry(): com.comunidapp.app.data.model.M27PublicAuditEntry =
    com.comunidapp.app.data.model.M27PublicAuditEntry(
        operation = string("operation").orEmpty(),
        outcome = string("outcome").orEmpty(),
        environment = enumOr(string("environment"), M27Environment.SANDBOX),
        sanitizedReason = string("sanitized_reason"),
        createdAt = parseTimestamp(string("created_at"))
    )

fun JsonObject.toM27IntegrationApp(): com.comunidapp.app.data.model.M27IntegrationApp =
    com.comunidapp.app.data.model.M27IntegrationApp(
        id = string("id").orEmpty(),
        ownerUserId = string("owner_user_id").orEmpty(),
        organizationId = string("organization_id").orEmpty(),
        name = string("name").orEmpty(),
        contractVersion = enumOr(string("contract_version"), com.comunidapp.app.data.model.M27ContractVersion.V1),
        grantedScopes = scopesList(),
        status = enumOr(string("status"), com.comunidapp.app.data.model.M27IntegrationAppStatus.DRAFT),
        environment = enumOr(string("environment"), M27Environment.SANDBOX),
        createdAt = parseTimestamp(string("created_at")),
        updatedAt = parseTimestamp(string("updated_at"))
    )

fun JsonObject.toM27IssuedCredential(): com.comunidapp.app.data.model.M27IssuedCredential =
    com.comunidapp.app.data.model.M27IssuedCredential(
        credential = toM27ApiCredential(),
        plaintextKeyOnce = string("plaintext_key_once")
    )

fun JsonObject.toM27WebhookEndpointWithSecret(): com.comunidapp.app.data.repository.M27WebhookEndpointWithSecret =
    com.comunidapp.app.data.repository.M27WebhookEndpointWithSecret(
        endpoint = toM27WebhookEndpoint(),
        secretOnce = string("secret_once")
    )

fun JsonObject.toM27RateLimitResult(): com.comunidapp.app.data.model.M27RateLimitResult =
    com.comunidapp.app.data.model.M27RateLimitResult(
        allowed = boolean("allowed"),
        reason = string("reason"),
        retryAfterSeconds = string("retry_after_seconds")?.toLongOrNull()
    )

class SupabaseM27RemoteDataSource {
    private suspend inline fun <reified T : Any> one(function: String, parameters: JsonObject): T =
        supabase.postgrest.rpc(function, parameters).decodeSingle()

    private suspend fun oneJson(function: String, parameters: JsonObject): JsonObject =
        one(function, parameters)

    private suspend inline fun <reified T : Any> list(function: String, parameters: JsonObject): List<T> =
        supabase.postgrest.rpc(function, parameters).decodeList()

    suspend fun listMyWebhooks(): List<JsonObject> = list("m27_list_my_webhooks", buildJsonObject {})
    suspend fun listMyOAuthApps(): List<JsonObject> = list("m27_list_my_oauth_apps", buildJsonObject {})
    suspend fun listMyApiKeys(): List<JsonObject> = list("m27_list_my_api_keys", buildJsonObject {})
    suspend fun listPublishedContracts(): List<JsonObject> = list("m27_list_published_contracts", buildJsonObject {})
    suspend fun listRateLimits(): List<JsonObject> = list("m27_list_rate_limits", buildJsonObject {})

    suspend fun registerWebhook(label: String, targetUrl: String, environment: String): JsonObject =
        one("m27_register_webhook", buildJsonObject {
            put("p_label", label)
            put("p_target_url", targetUrl)
            put("p_environment", environment)
        })

    suspend fun disableWebhook(webhookId: String): JsonObject =
        one("m27_disable_webhook", buildJsonObject { put("p_webhook_id", webhookId) })

    suspend fun registerOAuthApp(name: String, redirectUri: String, scopes: List<String>, environment: String): JsonObject =
        one("m27_register_oauth_app", buildJsonObject {
            put("p_name", name)
            put("p_redirect_uri", redirectUri)
            put("p_scopes", JsonArray(scopes.map(::JsonPrimitive)))
            put("p_environment", environment)
        })

    suspend fun revokeOAuthApp(appId: String): JsonObject =
        one("m27_revoke_oauth_app", buildJsonObject { put("p_app_id", appId) })

    suspend fun issueApiKey(label: String, scopes: List<String>, environment: String): JsonObject =
        one("m27_issue_api_key", buildJsonObject {
            put("p_label", label)
            put("p_scopes", JsonArray(scopes.map(::JsonPrimitive)))
            put("p_environment", environment)
        })

    suspend fun revokeApiKey(keyId: String): JsonObject =
        one("m27_revoke_api_key", buildJsonObject { put("p_key_id", keyId) })

    suspend fun listMyIntegrationApps(): List<JsonObject> = list("m27_list_my_integration_apps", buildJsonObject {})
    suspend fun listMyDeliveries(): List<JsonObject> = list("m27_list_my_deliveries", buildJsonObject {})
    suspend fun listMyEvents(): List<JsonObject> = list("m27_list_my_events", buildJsonObject {})
    suspend fun listMyAuditLog(): List<JsonObject> = list("m27_list_my_audit_log", buildJsonObject {})

    suspend fun createIntegrationApp(input: com.comunidapp.app.data.model.CreateM27IntegrationAppInput): com.comunidapp.app.data.model.M27IntegrationApp =
        oneJson("m27_create_integration_app", buildJsonObject {
            put("p_name", input.name)
            put("p_organization_id", input.organizationId)
            put("p_contract_version", input.contractVersion.name)
            put("p_scopes", JsonArray(input.requestedScopes.map(::JsonPrimitive)))
            put("p_environment", input.environment.name)
            put("p_client_request_id", input.clientRequestId)
        }).toM27IntegrationApp()

    suspend fun activateIntegrationApp(appId: String): com.comunidapp.app.data.model.M27IntegrationApp =
        oneJson("m27_activate_integration_app", buildJsonObject { put("p_app_id", appId) }).toM27IntegrationApp()

    suspend fun pauseIntegrationApp(appId: String): com.comunidapp.app.data.model.M27IntegrationApp =
        oneJson("m27_pause_integration_app", buildJsonObject { put("p_app_id", appId) }).toM27IntegrationApp()

    suspend fun revokeIntegrationApp(appId: String): com.comunidapp.app.data.model.M27IntegrationApp =
        oneJson("m27_revoke_integration_app", buildJsonObject { put("p_app_id", appId) }).toM27IntegrationApp()

    suspend fun createApiKeyForApp(input: com.comunidapp.app.data.model.CreateM27ApiKeyInput): com.comunidapp.app.data.model.M27IssuedCredential =
        oneJson("m27_create_api_key_for_app", buildJsonObject {
            put("p_app_id", input.appId)
            put("p_label", input.label)
            put("p_scopes", JsonArray(input.scopes.map(::JsonPrimitive)))
            put("p_environment", input.environment.name)
            put("p_client_request_id", input.clientRequestId)
        }).toM27IssuedCredential()

    suspend fun rotateApiKey(keyId: String): com.comunidapp.app.data.model.M27IssuedCredential =
        oneJson("m27_rotate_api_key", buildJsonObject { put("p_key_id", keyId) }).toM27IssuedCredential()

    suspend fun registerWebhookEndpoint(input: com.comunidapp.app.data.model.RegisterM27WebhookEndpointInput): com.comunidapp.app.data.repository.M27WebhookEndpointWithSecret =
        oneJson("m27_register_webhook_endpoint", buildJsonObject {
            put("p_app_id", input.appId)
            put("p_label", input.label)
            put("p_target_url", input.targetUrl)
            put("p_environment", input.environment.name)
            put("p_client_request_id", input.clientRequestId)
        }).toM27WebhookEndpointWithSecret()

    suspend fun verifyWebhookEndpoint(endpointId: String): M27WebhookEndpoint =
        oneJson("m27_verify_webhook_endpoint", buildJsonObject { put("p_endpoint_id", endpointId) }).toM27WebhookEndpoint()

    suspend fun subscribeWebhook(input: com.comunidapp.app.data.model.SubscribeM27WebhookInput): com.comunidapp.app.data.model.M27WebhookSubscription =
        oneJson("m27_subscribe_webhook", buildJsonObject {
            put("p_endpoint_id", input.endpointId)
            put("p_event_type", input.eventType.name)
        }).let { json ->
            com.comunidapp.app.data.model.M27WebhookSubscription(
                id = json.string("id").orEmpty(),
                endpointId = json.string("endpoint_id").orEmpty(),
                eventType = enumOr(json.string("event_type"), com.comunidapp.app.data.model.M27WebhookEventType.ADOPTION_PUBLISHED),
                active = json.boolean("active", true),
                createdAt = parseTimestamp(json.string("created_at"))
            )
        }

    suspend fun emitWebhookEvent(input: com.comunidapp.app.data.model.EmitM27WebhookEventInput): com.comunidapp.app.data.model.M27WebhookEvent =
        oneJson("m27_emit_webhook_event", buildJsonObject {
            put("p_app_id", input.appId)
            put("p_event_type", input.eventType.name)
            put("p_resource_ref", input.resourceRef)
            put("p_sanitized_payload", input.sanitizedPayload)
            put("p_client_request_id", input.clientRequestId)
        }).let { json ->
            com.comunidapp.app.data.model.M27WebhookEvent(
                id = json.string("id").orEmpty(),
                appId = json.string("app_id").orEmpty(),
                eventType = enumOr(json.string("event_type"), input.eventType),
                version = json.string("version").orEmpty(),
                resourceRef = json.string("resource_ref").orEmpty(),
                sanitizedPayload = json.string("sanitized_payload").orEmpty(),
                occurredAt = parseTimestamp(json.string("occurred_at"))
            )
        }

    suspend fun manualRetryDelivery(deliveryId: String): com.comunidapp.app.data.model.M27WebhookDelivery =
        oneJson("m27_manual_retry_delivery", buildJsonObject { put("p_delivery_id", deliveryId) }).let { json ->
            com.comunidapp.app.data.model.M27WebhookDelivery(
                id = json.string("id").orEmpty(),
                eventId = json.string("event_id").orEmpty(),
                subscriptionId = json.string("subscription_id").orEmpty(),
                endpointId = json.string("endpoint_id").orEmpty(),
                status = enumOr(json.string("status"), com.comunidapp.app.data.model.M27WebhookDeliveryStatus.PENDING),
                attemptCount = json.int("attempt_count") ?: 0,
                maxAttempts = json.int("max_attempts") ?: 3,
                signatureVersion = json.string("signature_version").orEmpty(),
                signatureDigest = json.string("signature_digest").orEmpty(),
                lastAttemptAt = json.string("last_attempt_at")?.let(::parseTimestamp),
                createdAt = parseTimestamp(json.string("created_at"))
            )
        }

    suspend fun checkAppRateLimit(appId: String, environment: String): com.comunidapp.app.data.model.M27RateLimitResult =
        oneJson("m27_check_app_rate_limit", buildJsonObject {
            put("p_app_id", appId)
            put("p_environment", environment)
        }).toM27RateLimitResult()

    suspend fun startOAuthStub(redirectUri: String, scopes: List<String>, state: String?): com.comunidapp.app.data.model.M27OAuthStubSession =
        oneJson("m27_start_oauth_stub", buildJsonObject {
            put("p_redirect_uri", redirectUri)
            put("p_scopes", JsonArray(scopes.map(::JsonPrimitive)))
            put("p_state", state)
        }).let { json ->
            com.comunidapp.app.data.model.M27OAuthStubSession(
                state = json.string("state").orEmpty(),
                redirectUri = json.string("redirect_uri").orEmpty(),
                scopes = json.scopesList(),
                stubTokenPrefix = json.string("stub_token_prefix").orEmpty(),
                expiresAt = parseTimestamp(json.string("expires_at"))
            )
        }
}
