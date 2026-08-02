package com.comunidapp.app.data.model

import com.comunidapp.app.domain.m27.M27ContractEligibilityService
import com.comunidapp.app.domain.m27.M27PrivacySanitizer

/** LeoVer M27 — Integraciones y API pública (Bloque 1 local; sin pagos ni M24). */
enum class M27Environment { SANDBOX, STAGING, PRODUCTION }
enum class M27WebhookStatus { PENDING_VERIFICATION, ACTIVE, PAUSED, DISABLED, REVOKED, PENDING }
enum class M27OAuthAppStatus { ACTIVE, REVOKED, PENDING }
enum class M27ApiKeyStatus { ACTIVE, REVOKED, EXPIRED }
enum class M27ContractStatus { DRAFT, PUBLISHED, DEPRECATED }
enum class M27ContractVersion { V1, V2 }

data class M27WebhookEndpoint(
    val id: String,
    val ownerUserId: String,
    val appId: String?,
    val label: String,
    val targetUrl: String,
    val secretPrefix: String,
    val secretHash: String,
    val status: M27WebhookStatus,
    val environment: M27Environment,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toPublic(): M27PublicWebhook = M27PrivacySanitizer.toPublicWebhook(this)
}

data class M27OAuthApplication(
    val id: String,
    val ownerUserId: String,
    val name: String,
    val redirectUri: String,
    val clientIdPrefix: String,
    val scopes: List<String>,
    val status: M27OAuthAppStatus,
    val environment: M27Environment,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toPublic(): M27PublicOAuthApp = M27PrivacySanitizer.toPublicOAuthApp(this)
}

data class M27ApiCredential(
    val id: String,
    val ownerUserId: String,
    val appId: String?,
    val label: String,
    val keyPrefix: String,
    val keyHash: String,
    val scopes: List<String>,
    val status: M27ApiKeyStatus,
    val environment: M27Environment,
    val createdAt: Long,
    val expiresAt: Long?,
    val lastUsedAt: Long? = null
) {
    fun toPublic(): M27PublicApiKey = M27PrivacySanitizer.toPublicApiKey(this)
}

data class M27RateLimitQuota(
    val environment: M27Environment,
    val requestsPerMinute: Int,
    val requestsPerDay: Int,
    val burstAllowance: Int
)

data class M27ApiContract(
    val id: String,
    val title: String,
    val version: M27ContractVersion,
    val status: M27ContractStatus,
    val summary: String,
    val publishedAt: Long?
) {
    fun toPublic(): M27PublicContract = M27PrivacySanitizer.toPublicContract(this)
}

data class M27PublicWebhook(
    val label: String,
    val targetUrl: String,
    val secretPrefix: String,
    val status: M27WebhookStatus,
    val environment: M27Environment
)

data class M27PublicOAuthApp(
    val name: String,
    val redirectUri: String,
    val clientIdPrefix: String,
    val scopes: List<String>,
    val status: M27OAuthAppStatus,
    val environment: M27Environment
)

data class M27PublicApiKey(
    val label: String,
    val keyPrefix: String,
    val scopes: List<String>,
    val status: M27ApiKeyStatus,
    val environment: M27Environment
)

data class M27PublicContract(
    val title: String,
    val version: M27ContractVersion,
    val summary: String,
    val publishedForDisplay: Boolean
)

data class M27PublicRateLimit(
    val environment: M27Environment,
    val requestsPerMinute: Int,
    val requestsPerDay: Int
)

data class RegisterM27WebhookInput(val label: String, val targetUrl: String, val environment: M27Environment)
data class RegisterM27OAuthAppInput(val name: String, val redirectUri: String, val scopes: List<String>, val environment: M27Environment)
data class IssueM27ApiKeyInput(val label: String, val scopes: List<String>, val environment: M27Environment)

object M27MockUsers {
    const val DEVELOPER = "mock_user_developer"
    const val ADMIN = "mock_user_admin"
    const val OTHER = "mock_user_other"
    const val UNAUTHORIZED = "mock_user_unauthorized"
}

object M27MockIds {
    const val WEBHOOK_ACTIVE = "m27_webhook_active"
    const val OAUTH_APP = "m27_oauth_app"
    const val API_KEY_PROD = "m27_key_prod"
    const val CONTRACT_V1 = "m27_contract_v1"
    const val APP_ACTIVE = "m27_app_active"
    const val APP_DRAFT = "m27_app_draft"
    const val ENDPOINT_ACTIVE = "m27_endpoint_active"
    const val SUBSCRIPTION_ACTIVE = "m27_sub_active"
}
