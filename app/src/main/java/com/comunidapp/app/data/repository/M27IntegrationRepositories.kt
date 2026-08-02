package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.IssueM27ApiKeyInput
import com.comunidapp.app.data.model.M27ApiCredential
import com.comunidapp.app.data.model.M27ApiKeyStatus
import com.comunidapp.app.data.model.M27ApiContract
import com.comunidapp.app.data.model.M27ContractStatus
import com.comunidapp.app.data.model.M27ContractVersion
import com.comunidapp.app.data.model.M27Environment
import com.comunidapp.app.data.model.M27MockIds
import com.comunidapp.app.data.model.M27MockUsers
import com.comunidapp.app.data.model.M27OAuthAppStatus
import com.comunidapp.app.data.model.M27OAuthApplication
import com.comunidapp.app.data.model.M27PublicApiKey
import com.comunidapp.app.data.model.M27PublicContract
import com.comunidapp.app.data.model.M27PublicOAuthApp
import com.comunidapp.app.data.model.M27PublicRateLimit
import com.comunidapp.app.data.model.M27PublicWebhook
import com.comunidapp.app.data.model.M27RateLimitQuota
import com.comunidapp.app.data.model.M27WebhookEndpoint
import com.comunidapp.app.data.model.M27WebhookStatus
import com.comunidapp.app.data.model.RegisterM27OAuthAppInput
import com.comunidapp.app.data.model.RegisterM27WebhookInput
import com.comunidapp.app.domain.m27.M27ContractEligibilityService
import com.comunidapp.app.domain.m27.M27RateLimitPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface M27IntegrationRepository {
    fun observeWebhooks(): Flow<List<M27PublicWebhook>>
    fun observeOAuthApps(): Flow<List<M27PublicOAuthApp>>
    fun observeApiKeys(): Flow<List<M27PublicApiKey>>
    fun observePublishedContracts(): Flow<List<M27PublicContract>>
    fun observeRateLimits(): Flow<List<M27PublicRateLimit>>
    suspend fun registerWebhook(input: RegisterM27WebhookInput): Result<M27WebhookEndpoint>
    suspend fun disableWebhook(webhookId: String): Result<Unit>
    suspend fun registerOAuthApp(input: RegisterM27OAuthAppInput): Result<M27OAuthApplication>
    suspend fun revokeOAuthApp(appId: String): Result<Unit>
    suspend fun issueApiKey(input: IssueM27ApiKeyInput): Result<M27ApiCredential>
    suspend fun revokeApiKey(keyId: String): Result<Unit>
}

class M27IntegrationMemoryStore {
    private val mutex = Mutex()
    private var sequence = 0
    val webhooks = MutableStateFlow<List<M27WebhookEndpoint>>(emptyList())
    val oauthApps = MutableStateFlow<List<M27OAuthApplication>>(emptyList())
    val apiKeys = MutableStateFlow<List<M27ApiCredential>>(emptyList())
    val contracts = MutableStateFlow<List<M27ApiContract>>(emptyList())
    val quotas = MutableStateFlow(M27RateLimitPolicy.defaultQuotas())

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
    fun nextId(prefix: String): String = "${prefix}_${++sequence}"

    fun seedDefaults() {
        if (webhooks.value.isNotEmpty()) return
        val stamp = 1_700_100_000_000L
        webhooks.value = listOf(
            webhook(M27MockIds.WEBHOOK_ACTIVE, M27MockUsers.DEVELOPER, "Eventos municipales", "https://hooks.example.com/leover", "whsec_ab12", M27WebhookStatus.ACTIVE, M27Environment.PRODUCTION, stamp),
            webhook("m27_webhook_sandbox", M27MockUsers.DEVELOPER, "Sandbox QA", "https://sandbox.example.com/hook", "whsec_cd34", M27WebhookStatus.ACTIVE, M27Environment.SANDBOX, stamp),
            webhook("m27_webhook_disabled", M27MockUsers.OTHER, "Legacy hook", "https://legacy.example.com/hook", "whsec_ef56", M27WebhookStatus.DISABLED, M27Environment.PRODUCTION, stamp)
        )
        oauthApps.value = listOf(
            oauth(M27MockIds.OAUTH_APP, M27MockUsers.DEVELOPER, "Portal municipal", "https://portal.example.com/oauth/callback", "lv_cli_9f", listOf("adoptions_read", "events_read"), M27OAuthAppStatus.ACTIVE, M27Environment.PRODUCTION, stamp),
            oauth("m27_oauth_revoked", M27MockUsers.OTHER, "App revocada", "https://old.example.com/cb", "lv_cli_xx", listOf("lost_found_read"), M27OAuthAppStatus.REVOKED, M27Environment.SANDBOX, stamp)
        )
        apiKeys.value = listOf(
            apiKey(M27MockIds.API_KEY_PROD, M27MockUsers.DEVELOPER, "Integración producción", "lvk_prod_7a", listOf("webhooks_manage", "adoptions_read"), M27ApiKeyStatus.ACTIVE, M27Environment.PRODUCTION, stamp),
            apiKey("m27_key_sandbox", M27MockUsers.DEVELOPER, "Clave sandbox", "lvk_sbx_2b", listOf("sandbox_all"), M27ApiKeyStatus.ACTIVE, M27Environment.SANDBOX, stamp)
        )
        contracts.value = listOf(
            contract(M27MockIds.CONTRACT_V1, "LeoVer Public API v1", M27ContractVersion.V1, M27ContractStatus.PUBLISHED, "Contrato estable para adopciones, eventos y webhooks municipales.", stamp),
            contract("m27_contract_draft", "LeoVer Public API v2 (borrador)", M27ContractVersion.V2, M27ContractStatus.DRAFT, "Versión futura con scopes ampliados.", stamp, publishedAt = null)
        )
    }

    private fun webhook(
        id: String, owner: String, label: String, url: String, prefix: String,
        status: M27WebhookStatus, env: M27Environment, stamp: Long
    ) = M27WebhookEndpoint(id, owner, label, url, prefix, status, env, stamp, stamp)

    private fun oauth(
        id: String, owner: String, name: String, redirect: String, prefix: String,
        scopes: List<String>, status: M27OAuthAppStatus, env: M27Environment, stamp: Long
    ) = M27OAuthApplication(id, owner, name, redirect, prefix, scopes, status, env, stamp, stamp)

    private fun apiKey(
        id: String, owner: String, label: String, prefix: String, scopes: List<String>,
        status: M27ApiKeyStatus, env: M27Environment, stamp: Long
    ) = M27ApiCredential(id, owner, label, prefix, scopes, status, env, stamp, null)

    private fun contract(
        id: String, title: String, version: M27ContractVersion, status: M27ContractStatus,
        summary: String, stamp: Long, publishedAt: Long? = stamp
    ) = M27ApiContract(id, title, version, status, summary, publishedAt)
}

class MockM27IntegrationRepository(
    private val actorUserId: () -> String?,
    private val store: M27IntegrationMemoryStore = M27IntegrationMemoryStore()
) : M27IntegrationRepository {
    init { store.seedDefaults() }

    override fun observeWebhooks(): Flow<List<M27PublicWebhook>> = store.webhooks.map { items ->
        val actor = actorUserId() ?: return@map emptyList()
        items.filter { it.ownerUserId == actor }.map { it.toPublic() }
    }

    override fun observeOAuthApps(): Flow<List<M27PublicOAuthApp>> = store.oauthApps.map { items ->
        val actor = actorUserId() ?: return@map emptyList()
        items.filter { it.ownerUserId == actor }.map { it.toPublic() }
    }

    override fun observeApiKeys(): Flow<List<M27PublicApiKey>> = store.apiKeys.map { items ->
        val actor = actorUserId() ?: return@map emptyList()
        items.filter { it.ownerUserId == actor }.map { it.toPublic() }
    }

    override fun observePublishedContracts(): Flow<List<M27PublicContract>> = store.contracts.map { items ->
        M27ContractEligibilityService.filterEligiblePublic(items)
    }

    override fun observeRateLimits(): Flow<List<M27PublicRateLimit>> = store.quotas.map { quotas ->
        quotas.map { M27RateLimitPolicy.toPublic(it) }
    }

    override suspend fun registerWebhook(input: RegisterM27WebhookInput): Result<M27WebhookEndpoint> = store.withLock {
        val actor = actorUserId() ?: return@withLock M27IntegrationErrors.failure(M27IntegrationException("NOT_AUTHENTICATED", "auth"))
        if (!canManage(actor)) return@withLock M27IntegrationErrors.failure(M27IntegrationException("M27_PERMISSION_DENIED", "perm"))
        M27IntegrationValidators.validateWebhook(input.label, input.targetUrl)?.let {
            return@withLock M27IntegrationErrors.failure(M27IntegrationException(it, it))
        }
        val stamp = System.currentTimeMillis()
        val webhook = M27WebhookEndpoint(
            id = store.nextId("m27_webhook"),
            ownerUserId = actor,
            label = input.label.trim(),
            targetUrl = input.targetUrl.trim(),
            secretPrefix = "whsec_${(1000..9999).random()}",
            status = M27WebhookStatus.ACTIVE,
            environment = input.environment,
            createdAt = stamp,
            updatedAt = stamp
        )
        store.webhooks.value = store.webhooks.value + webhook
        Result.success(webhook)
    }

    override suspend fun disableWebhook(webhookId: String): Result<Unit> = store.withLock {
        val actor = actorUserId() ?: return@withLock M27IntegrationErrors.failure(M27IntegrationException("NOT_AUTHENTICATED", "auth"))
        val idx = store.webhooks.value.indexOfFirst { it.id == webhookId && it.ownerUserId == actor }
        if (idx < 0) return@withLock M27IntegrationErrors.failure(M27IntegrationException("M27_WEBHOOK_NOT_FOUND", "nf"))
        val current = store.webhooks.value[idx]
        store.webhooks.value = store.webhooks.value.toMutableList().also {
            it[idx] = current.copy(status = M27WebhookStatus.DISABLED, updatedAt = System.currentTimeMillis())
        }
        Result.success(Unit)
    }

    override suspend fun registerOAuthApp(input: RegisterM27OAuthAppInput): Result<M27OAuthApplication> = store.withLock {
        val actor = actorUserId() ?: return@withLock M27IntegrationErrors.failure(M27IntegrationException("NOT_AUTHENTICATED", "auth"))
        if (!canManage(actor)) return@withLock M27IntegrationErrors.failure(M27IntegrationException("M27_PERMISSION_DENIED", "perm"))
        M27IntegrationValidators.validateOAuthApp(input.name, input.redirectUri, input.scopes)?.let {
            return@withLock M27IntegrationErrors.failure(M27IntegrationException(it, it))
        }
        val stamp = System.currentTimeMillis()
        val app = M27OAuthApplication(
            id = store.nextId("m27_oauth"),
            ownerUserId = actor,
            name = input.name.trim(),
            redirectUri = input.redirectUri.trim(),
            clientIdPrefix = "lv_cli_${(100..999).random()}",
            scopes = input.scopes,
            status = M27OAuthAppStatus.ACTIVE,
            environment = input.environment,
            createdAt = stamp,
            updatedAt = stamp
        )
        store.oauthApps.value = store.oauthApps.value + app
        Result.success(app)
    }

    override suspend fun revokeOAuthApp(appId: String): Result<Unit> = store.withLock {
        val actor = actorUserId() ?: return@withLock M27IntegrationErrors.failure(M27IntegrationException("NOT_AUTHENTICATED", "auth"))
        val idx = store.oauthApps.value.indexOfFirst { it.id == appId && it.ownerUserId == actor }
        if (idx < 0) return@withLock M27IntegrationErrors.failure(M27IntegrationException("M27_OAUTH_NOT_FOUND", "nf"))
        val current = store.oauthApps.value[idx]
        store.oauthApps.value = store.oauthApps.value.toMutableList().also {
            it[idx] = current.copy(status = M27OAuthAppStatus.REVOKED, updatedAt = System.currentTimeMillis())
        }
        Result.success(Unit)
    }

    override suspend fun issueApiKey(input: IssueM27ApiKeyInput): Result<M27ApiCredential> = store.withLock {
        val actor = actorUserId() ?: return@withLock M27IntegrationErrors.failure(M27IntegrationException("NOT_AUTHENTICATED", "auth"))
        if (!canManage(actor)) return@withLock M27IntegrationErrors.failure(M27IntegrationException("M27_PERMISSION_DENIED", "perm"))
        M27IntegrationValidators.validateApiKey(input.label, input.scopes)?.let {
            return@withLock M27IntegrationErrors.failure(M27IntegrationException(it, it))
        }
        val stamp = System.currentTimeMillis()
        val prefix = if (input.environment == M27Environment.SANDBOX) "lvk_sbx_" else "lvk_prod_"
        val key = M27ApiCredential(
            id = store.nextId("m27_key"),
            ownerUserId = actor,
            label = input.label.trim(),
            keyPrefix = "$prefix${(100..999).random()}",
            scopes = input.scopes,
            status = M27ApiKeyStatus.ACTIVE,
            environment = input.environment,
            createdAt = stamp,
            expiresAt = null
        )
        store.apiKeys.value = store.apiKeys.value + key
        Result.success(key)
    }

    override suspend fun revokeApiKey(keyId: String): Result<Unit> = store.withLock {
        val actor = actorUserId() ?: return@withLock M27IntegrationErrors.failure(M27IntegrationException("NOT_AUTHENTICATED", "auth"))
        val idx = store.apiKeys.value.indexOfFirst { it.id == keyId && it.ownerUserId == actor }
        if (idx < 0) return@withLock M27IntegrationErrors.failure(M27IntegrationException("M27_KEY_NOT_FOUND", "nf"))
        val current = store.apiKeys.value[idx]
        store.apiKeys.value = store.apiKeys.value.toMutableList().also {
            it[idx] = current.copy(status = M27ApiKeyStatus.REVOKED)
        }
        Result.success(Unit)
    }

    private fun canManage(actor: String): Boolean =
        actor == M27MockUsers.DEVELOPER || actor == M27MockUsers.ADMIN
}
