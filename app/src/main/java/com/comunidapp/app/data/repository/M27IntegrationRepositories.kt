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
import com.comunidapp.app.data.model.CreateM27ApiKeyInput
import com.comunidapp.app.data.model.CreateM27IntegrationAppInput
import com.comunidapp.app.data.model.EmitM27WebhookEventInput
import com.comunidapp.app.data.model.M27AuditEntry
import com.comunidapp.app.data.model.M27IntegrationApp
import com.comunidapp.app.data.model.M27IntegrationAppStatus
import com.comunidapp.app.data.model.M27IssuedCredential
import com.comunidapp.app.data.model.M27OAuthStubSession
import com.comunidapp.app.data.model.M27PublicAuditEntry
import com.comunidapp.app.data.model.M27PublicIntegrationApp
import com.comunidapp.app.data.model.M27PublicWebhookDelivery
import com.comunidapp.app.data.model.M27PublicWebhookEvent
import com.comunidapp.app.data.model.M27RateLimitResult
import com.comunidapp.app.data.model.M27WebhookDelivery
import com.comunidapp.app.data.model.M27WebhookEvent
import com.comunidapp.app.data.model.M27WebhookSubscription
import com.comunidapp.app.data.model.RegisterM27WebhookEndpointInput
import com.comunidapp.app.data.model.SubscribeM27WebhookInput
import com.comunidapp.app.data.model.M27MockOrg
import com.comunidapp.app.domain.m27.M27ContractEligibilityService
import com.comunidapp.app.domain.m27.M27CredentialHasher
import com.comunidapp.app.domain.m27.M27RateLimitPolicy
import com.comunidapp.app.domain.m27.M27SsrfValidator
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
    fun observeIntegrationApps(): Flow<List<M27PublicIntegrationApp>>
    fun observeDeliveries(): Flow<List<M27PublicWebhookDelivery>>
    fun observeEvents(): Flow<List<M27PublicWebhookEvent>>
    fun observeAuditLog(): Flow<List<M27PublicAuditEntry>>
    suspend fun registerWebhook(input: RegisterM27WebhookInput): Result<M27WebhookEndpoint>
    suspend fun disableWebhook(webhookId: String): Result<Unit>
    suspend fun registerOAuthApp(input: RegisterM27OAuthAppInput): Result<M27OAuthApplication>
    suspend fun revokeOAuthApp(appId: String): Result<Unit>
    suspend fun issueApiKey(input: IssueM27ApiKeyInput): Result<M27ApiCredential>
    suspend fun revokeApiKey(keyId: String): Result<Unit>
    suspend fun createIntegrationApp(input: CreateM27IntegrationAppInput): Result<M27IntegrationApp>
    suspend fun activateIntegrationApp(appId: String): Result<M27IntegrationApp>
    suspend fun pauseIntegrationApp(appId: String): Result<M27IntegrationApp>
    suspend fun revokeIntegrationApp(appId: String): Result<M27IntegrationApp>
    suspend fun createApiKeyForApp(input: CreateM27ApiKeyInput): Result<M27IssuedCredential>
    suspend fun rotateApiKey(keyId: String): Result<M27IssuedCredential>
    suspend fun registerWebhookEndpoint(input: RegisterM27WebhookEndpointInput): Result<M27WebhookEndpointWithSecret>
    suspend fun verifyWebhookEndpoint(endpointId: String): Result<M27WebhookEndpoint>
    suspend fun subscribeWebhook(input: SubscribeM27WebhookInput): Result<M27WebhookSubscription>
    suspend fun emitWebhookEvent(input: EmitM27WebhookEventInput): Result<M27WebhookEvent>
    suspend fun manualRetryDelivery(deliveryId: String): Result<M27WebhookDelivery>
    suspend fun checkAppRateLimit(appId: String, environment: M27Environment): M27RateLimitResult
    suspend fun startOAuthStub(redirectUri: String, scopes: List<String>, state: String?): Result<M27OAuthStubSession>
}

class M27IntegrationMemoryStore {
    private val mutex = Mutex()
    private var sequence = 0
    val webhooks = MutableStateFlow<List<M27WebhookEndpoint>>(emptyList())
    val oauthApps = MutableStateFlow<List<M27OAuthApplication>>(emptyList())
    val apiKeys = MutableStateFlow<List<M27ApiCredential>>(emptyList())
    val contracts = MutableStateFlow<List<M27ApiContract>>(emptyList())
    val quotas = MutableStateFlow(M27RateLimitPolicy.defaultQuotas())
    val apps = MutableStateFlow<List<M27IntegrationApp>>(emptyList())
    val subscriptions = MutableStateFlow<List<M27WebhookSubscription>>(emptyList())
    val events = MutableStateFlow<List<M27WebhookEvent>>(emptyList())
    val deliveries = MutableStateFlow<List<M27WebhookDelivery>>(emptyList())
    val attempts = MutableStateFlow<List<com.comunidapp.app.data.model.M27WebhookAttempt>>(emptyList())
    val auditLog = MutableStateFlow<List<M27AuditEntry>>(emptyList())
    val clientRequests = mutableMapOf<String, String>()
    val eventIdempotency = mutableMapOf<String, String>()
    val deliveryKeys = mutableSetOf<String>()
    val rateCounters = mutableMapOf<String, Int>()
    val oauthSessions = mutableMapOf<String, M27OAuthStubSession>()

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
    fun nextId(prefix: String): String = "${prefix}_${++sequence}"

    fun seedDefaults() {
        if (webhooks.value.isNotEmpty()) return
        val stamp = 1_700_100_000_000L
        val secretHash = M27CredentialHasher.hashSecret("seed_secret_whsec_ab12")
        apps.value = listOf(
            M27IntegrationApp(
                M27MockIds.APP_ACTIVE, M27MockUsers.DEVELOPER, M27MockOrg.DEV_ORG,
                "Portal municipal activo", M27ContractVersion.V1,
                listOf("adoptions.read.public", "events.read.public", "webhooks.manage"),
                M27IntegrationAppStatus.ACTIVE, M27Environment.SANDBOX, stamp, stamp
            ),
            M27IntegrationApp(
                M27MockIds.APP_DRAFT, M27MockUsers.DEVELOPER, M27MockOrg.DEV_ORG,
                "App borrador QA", M27ContractVersion.V1,
                listOf("sandbox.execute"), M27IntegrationAppStatus.DRAFT, M27Environment.SANDBOX, stamp, stamp
            )
        )
        webhooks.value = listOf(
            webhook(M27MockIds.WEBHOOK_ACTIVE, M27MockUsers.DEVELOPER, M27MockIds.APP_ACTIVE, "Eventos municipales", "https://hooks.example.com/leover", "whsec_ab12", secretHash, M27WebhookStatus.ACTIVE, M27Environment.SANDBOX, stamp),
            webhook("m27_webhook_sandbox", M27MockUsers.DEVELOPER, M27MockIds.APP_ACTIVE, "Sandbox QA", "https://sandbox.example.com/hook", "whsec_cd34", M27CredentialHasher.hashSecret("whsec_cd34"), M27WebhookStatus.ACTIVE, M27Environment.SANDBOX, stamp),
            webhook("m27_webhook_disabled", M27MockUsers.OTHER, null, "Legacy hook", "https://legacy.example.com/hook", "whsec_ef56", M27CredentialHasher.hashSecret("whsec_ef56"), M27WebhookStatus.DISABLED, M27Environment.SANDBOX, stamp)
        )
        subscriptions.value = listOf(
            M27WebhookSubscription(M27MockIds.SUBSCRIPTION_ACTIVE, M27MockIds.WEBHOOK_ACTIVE, com.comunidapp.app.data.model.M27WebhookEventType.ADOPTION_PUBLISHED, true, stamp)
        )
        oauthApps.value = listOf(
            oauth(M27MockIds.OAUTH_APP, M27MockUsers.DEVELOPER, "Portal municipal", "https://portal.example.com/oauth/callback", "lv_cli_9f", listOf("adoptions.read.public", "events.read.public"), M27OAuthAppStatus.ACTIVE, M27Environment.SANDBOX, stamp),
            oauth("m27_oauth_revoked", M27MockUsers.OTHER, "App revocada", "https://old.example.com/cb", "lv_cli_xx", listOf("pets.read.public"), M27OAuthAppStatus.REVOKED, M27Environment.SANDBOX, stamp)
        )
        apiKeys.value = listOf(
            apiKey(M27MockIds.API_KEY_PROD, M27MockUsers.DEVELOPER, M27MockIds.APP_ACTIVE, "Integración sandbox", "lvk_sbx_7a", M27CredentialHasher.hashSecret("seed_key_prod"), listOf("webhooks.manage", "adoptions.read.public"), M27ApiKeyStatus.ACTIVE, M27Environment.SANDBOX, stamp),
            apiKey("m27_key_sandbox", M27MockUsers.DEVELOPER, M27MockIds.APP_ACTIVE, "Clave sandbox", "lvk_sbx_2b", M27CredentialHasher.hashSecret("seed_key_sbx"), listOf("sandbox.execute"), M27ApiKeyStatus.ACTIVE, M27Environment.SANDBOX, stamp)
        )
        contracts.value = listOf(
            contract(M27MockIds.CONTRACT_V1, "LeoVer Public API v1", M27ContractVersion.V1, M27ContractStatus.PUBLISHED, "Contrato estable para adopciones, eventos y webhooks municipales.", stamp),
            contract("m27_contract_draft", "LeoVer Public API v2 (borrador)", M27ContractVersion.V2, M27ContractStatus.DRAFT, "Versión futura con scopes ampliados.", stamp, publishedAt = null)
        )
    }

    private fun webhook(
        id: String, owner: String, appId: String?, label: String, url: String, prefix: String, secretHash: String,
        status: M27WebhookStatus, env: M27Environment, stamp: Long
    ) = M27WebhookEndpoint(id, owner, appId, label, url, prefix, secretHash, status, env, stamp, stamp)

    private fun oauth(
        id: String, owner: String, name: String, redirect: String, prefix: String,
        scopes: List<String>, status: M27OAuthAppStatus, env: M27Environment, stamp: Long
    ) = M27OAuthApplication(id, owner, name, redirect, prefix, scopes, status, env, stamp, stamp)

    private fun apiKey(
        id: String, owner: String, appId: String?, label: String, prefix: String, hash: String, scopes: List<String>,
        status: M27ApiKeyStatus, env: M27Environment, stamp: Long
    ) = M27ApiCredential(id, owner, appId, label, prefix, hash, scopes, status, env, stamp, null)

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
        M27SsrfValidator.validateTargetUrl(input.targetUrl)?.let {
            return@withLock M27IntegrationErrors.failure(M27IntegrationException(it, it))
        }
        val stamp = System.currentTimeMillis()
        val rawSecret = "whsec_${(1000..9999).random()}"
        val webhook = M27WebhookEndpoint(
            id = store.nextId("m27_webhook"),
            ownerUserId = actor,
            appId = null,
            label = input.label.trim(),
            targetUrl = input.targetUrl.trim(),
            secretPrefix = rawSecret.take(10),
            secretHash = M27CredentialHasher.hashSecret(rawSecret),
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
        val raw = "lvk_${(100..999).random()}"
        val prefix = if (input.environment == M27Environment.SANDBOX) "lvk_sbx_" else "lvk_stg_"
        val key = M27ApiCredential(
            id = store.nextId("m27_key"),
            ownerUserId = actor,
            appId = null,
            label = input.label.trim(),
            keyPrefix = "$prefix${(100..999).random()}",
            keyHash = M27CredentialHasher.hashSecret(raw),
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

    override fun observeIntegrationApps(): Flow<List<M27PublicIntegrationApp>> = store.apps.map { items ->
        val actor = actorUserId() ?: return@map emptyList()
        val org = M27IntegrationOperationsLogic.orgFor(actor)
        items.filter { M27IntegrationOperationsLogic.canManageOrg(actor, it.organizationId) || it.ownerUserId == actor }
            .map { it.toPublic() }
    }

    override fun observeDeliveries(): Flow<List<M27PublicWebhookDelivery>> = store.deliveries.map { it.map { d -> d.toPublic() } }

    override fun observeEvents(): Flow<List<M27PublicWebhookEvent>> = store.events.map { it.map { e -> e.toPublic() } }

    override fun observeAuditLog(): Flow<List<M27PublicAuditEntry>> = store.auditLog.map { items ->
        val actor = actorUserId() ?: return@map emptyList()
        items.filter { it.actorUserId == actor || actor == M27MockUsers.ADMIN }.map { it.toPublic() }
    }

    override suspend fun createIntegrationApp(input: CreateM27IntegrationAppInput): Result<M27IntegrationApp> =
        store.withLock {
            val actor = actorUserId() ?: return@withLock M27IntegrationErrors.failure(M27IntegrationException("NOT_AUTHENTICATED", "auth"))
            M27IntegrationOperationsLogic.createApp(store, actor, input)
        }

    override suspend fun activateIntegrationApp(appId: String): Result<M27IntegrationApp> = store.withLock {
        val actor = actorUserId() ?: return@withLock M27IntegrationErrors.failure(M27IntegrationException("NOT_AUTHENTICATED", "auth"))
        M27IntegrationOperationsLogic.activateApp(store, actor, appId)
    }

    override suspend fun pauseIntegrationApp(appId: String): Result<M27IntegrationApp> = store.withLock {
        val actor = actorUserId() ?: return@withLock M27IntegrationErrors.failure(M27IntegrationException("NOT_AUTHENTICATED", "auth"))
        M27IntegrationOperationsLogic.pauseApp(store, actor, appId)
    }

    override suspend fun revokeIntegrationApp(appId: String): Result<M27IntegrationApp> = store.withLock {
        val actor = actorUserId() ?: return@withLock M27IntegrationErrors.failure(M27IntegrationException("NOT_AUTHENTICATED", "auth"))
        M27IntegrationOperationsLogic.revokeApp(store, actor, appId)
    }

    override suspend fun createApiKeyForApp(input: CreateM27ApiKeyInput): Result<M27IssuedCredential> = store.withLock {
        val actor = actorUserId() ?: return@withLock M27IntegrationErrors.failure(M27IntegrationException("NOT_AUTHENTICATED", "auth"))
        M27IntegrationOperationsLogic.createApiKey(store, actor, input)
    }

    override suspend fun rotateApiKey(keyId: String): Result<M27IssuedCredential> = store.withLock {
        val actor = actorUserId() ?: return@withLock M27IntegrationErrors.failure(M27IntegrationException("NOT_AUTHENTICATED", "auth"))
        M27IntegrationOperationsLogic.rotateApiKey(store, actor, keyId)
    }

    override suspend fun registerWebhookEndpoint(input: RegisterM27WebhookEndpointInput): Result<M27WebhookEndpointWithSecret> =
        store.withLock {
            val actor = actorUserId() ?: return@withLock M27IntegrationErrors.failure(M27IntegrationException("NOT_AUTHENTICATED", "auth"))
            M27IntegrationOperationsLogic.registerEndpoint(store, actor, input)
        }

    override suspend fun verifyWebhookEndpoint(endpointId: String): Result<M27WebhookEndpoint> = store.withLock {
        val actor = actorUserId() ?: return@withLock M27IntegrationErrors.failure(M27IntegrationException("NOT_AUTHENTICATED", "auth"))
        M27IntegrationOperationsLogic.verifyEndpoint(store, actor, endpointId)
    }

    override suspend fun subscribeWebhook(input: SubscribeM27WebhookInput): Result<M27WebhookSubscription> = store.withLock {
        val actor = actorUserId() ?: return@withLock M27IntegrationErrors.failure(M27IntegrationException("NOT_AUTHENTICATED", "auth"))
        M27IntegrationOperationsLogic.subscribe(store, actor, input)
    }

    override suspend fun emitWebhookEvent(input: EmitM27WebhookEventInput): Result<M27WebhookEvent> = store.withLock {
        val actor = actorUserId() ?: return@withLock M27IntegrationErrors.failure(M27IntegrationException("NOT_AUTHENTICATED", "auth"))
        M27IntegrationOperationsLogic.emitEvent(store, actor, input)
    }

    override suspend fun manualRetryDelivery(deliveryId: String): Result<M27WebhookDelivery> = store.withLock {
        val actor = actorUserId() ?: return@withLock M27IntegrationErrors.failure(M27IntegrationException("NOT_AUTHENTICATED", "auth"))
        M27IntegrationOperationsLogic.manualRetry(store, actor, deliveryId)
    }

    override suspend fun checkAppRateLimit(appId: String, environment: M27Environment): M27RateLimitResult =
        store.withLock { M27IntegrationOperationsLogic.checkRateLimit(store, appId, environment) }

    override suspend fun startOAuthStub(redirectUri: String, scopes: List<String>, state: String?): Result<M27OAuthStubSession> =
        store.withLock { M27IntegrationOperationsLogic.startOAuthStub(store, redirectUri, scopes, state) }
}
