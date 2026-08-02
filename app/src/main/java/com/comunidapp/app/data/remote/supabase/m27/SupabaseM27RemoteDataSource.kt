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
    label = string("label").orEmpty(),
    targetUrl = string("target_url").orEmpty(),
    secretPrefix = string("secret_prefix").orEmpty(),
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
    label = string("label").orEmpty(),
    keyPrefix = string("key_prefix").orEmpty(),
    scopes = scopesList(),
    status = enumOr(string("status"), M27ApiKeyStatus.ACTIVE),
    environment = enumOr(string("environment"), M27Environment.SANDBOX),
    createdAt = parseTimestamp(string("created_at")),
    expiresAt = string("expires_at")?.let(::parseTimestamp)
)

class SupabaseM27RemoteDataSource {
    private suspend inline fun <reified T : Any> one(function: String, parameters: JsonObject): T =
        supabase.postgrest.rpc(function, parameters).decodeSingle()

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
}
