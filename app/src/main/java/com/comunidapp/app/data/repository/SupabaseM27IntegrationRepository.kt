package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.IssueM27ApiKeyInput
import com.comunidapp.app.data.model.M27ApiCredential
import com.comunidapp.app.data.model.M27OAuthApplication
import com.comunidapp.app.data.model.M27PublicApiKey
import com.comunidapp.app.data.model.M27PublicContract
import com.comunidapp.app.data.model.M27PublicOAuthApp
import com.comunidapp.app.data.model.M27PublicRateLimit
import com.comunidapp.app.data.model.M27PublicWebhook
import com.comunidapp.app.data.model.M27WebhookEndpoint
import com.comunidapp.app.data.model.RegisterM27OAuthAppInput
import com.comunidapp.app.data.model.RegisterM27WebhookInput
import com.comunidapp.app.data.remote.supabase.m27.M27IntegrationErrorMapper
import com.comunidapp.app.data.remote.supabase.m27.SupabaseM27RemoteDataSource
import com.comunidapp.app.data.remote.supabase.m27.toM27ApiCredential
import com.comunidapp.app.data.remote.supabase.m27.toM27OAuthApplication
import com.comunidapp.app.data.remote.supabase.m27.toM27PublicApiKey
import com.comunidapp.app.data.remote.supabase.m27.toM27PublicContract
import com.comunidapp.app.data.remote.supabase.m27.toM27PublicOAuthApp
import com.comunidapp.app.data.remote.supabase.m27.toM27PublicRateLimit
import com.comunidapp.app.data.remote.supabase.m27.toM27PublicWebhook
import com.comunidapp.app.data.remote.supabase.m27.toM27WebhookEndpoint
import com.comunidapp.app.data.remote.supabase.m27.toM27PublicIntegrationApp
import com.comunidapp.app.data.remote.supabase.m27.toM27PublicWebhookDelivery
import com.comunidapp.app.data.remote.supabase.m27.toM27PublicWebhookEvent
import com.comunidapp.app.data.remote.supabase.m27.toM27PublicAuditEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class SupabaseM27IntegrationRepository(
    private val remote: SupabaseM27RemoteDataSource = SupabaseM27RemoteDataSource(),
    private val actorUserId: () -> String? = { null }
) : M27IntegrationRepository {

    private fun requireActor() {
        if (actorUserId() == null) throw M27IntegrationException(
            "NOT_AUTHENTICATED", M27IntegrationErrors.userMessage("NOT_AUTHENTICATED")
        )
    }

    override fun observeWebhooks(): Flow<List<M27PublicWebhook>> = flow {
        if (actorUserId() == null) emit(emptyList())
        else emit(runCatching { remote.listMyWebhooks().map { it.toM27PublicWebhook() } }.getOrElse { emptyList() })
    }

    override fun observeOAuthApps(): Flow<List<M27PublicOAuthApp>> = flow {
        if (actorUserId() == null) emit(emptyList())
        else emit(runCatching { remote.listMyOAuthApps().map { it.toM27PublicOAuthApp() } }.getOrElse { emptyList() })
    }

    override fun observeApiKeys(): Flow<List<M27PublicApiKey>> = flow {
        if (actorUserId() == null) emit(emptyList())
        else emit(runCatching { remote.listMyApiKeys().map { it.toM27PublicApiKey() } }.getOrElse { emptyList() })
    }

    override fun observePublishedContracts(): Flow<List<M27PublicContract>> = flow {
        emit(runCatching { remote.listPublishedContracts().map { it.toM27PublicContract() } }.getOrElse { emptyList() })
    }

    override fun observeRateLimits(): Flow<List<M27PublicRateLimit>> = flow {
        emit(runCatching { remote.listRateLimits().map { it.toM27PublicRateLimit() } }.getOrElse { emptyList() })
    }

    override suspend fun registerWebhook(input: RegisterM27WebhookInput): Result<M27WebhookEndpoint> = try {
        requireActor()
        M27IntegrationValidators.validateWebhook(input.label, input.targetUrl)?.let { return M27IntegrationErrorMapper.fail(it) }
        Result.success(
            remote.registerWebhook(input.label.trim(), input.targetUrl.trim(), input.environment.name)
                .toM27WebhookEndpoint()
        )
    } catch (error: Throwable) {
        M27IntegrationErrorMapper.failure(error)
    }

    override suspend fun disableWebhook(webhookId: String): Result<Unit> = try {
        requireActor()
        remote.disableWebhook(webhookId)
        Result.success(Unit)
    } catch (error: Throwable) {
        M27IntegrationErrorMapper.failure(error)
    }

    override suspend fun registerOAuthApp(input: RegisterM27OAuthAppInput): Result<M27OAuthApplication> = try {
        requireActor()
        M27IntegrationValidators.validateOAuthApp(input.name, input.redirectUri, input.scopes)?.let {
            return M27IntegrationErrorMapper.fail(it)
        }
        Result.success(
            remote.registerOAuthApp(input.name.trim(), input.redirectUri.trim(), input.scopes, input.environment.name)
                .toM27OAuthApplication()
        )
    } catch (error: Throwable) {
        M27IntegrationErrorMapper.failure(error)
    }

    override suspend fun revokeOAuthApp(appId: String): Result<Unit> = try {
        requireActor()
        remote.revokeOAuthApp(appId)
        Result.success(Unit)
    } catch (error: Throwable) {
        M27IntegrationErrorMapper.failure(error)
    }

    override suspend fun issueApiKey(input: IssueM27ApiKeyInput): Result<M27ApiCredential> = try {
        requireActor()
        M27IntegrationValidators.validateApiKey(input.label, input.scopes)?.let { return M27IntegrationErrorMapper.fail(it) }
        Result.success(
            remote.issueApiKey(input.label.trim(), input.scopes, input.environment.name).toM27ApiCredential()
        )
    } catch (error: Throwable) {
        M27IntegrationErrorMapper.failure(error)
    }

    override suspend fun revokeApiKey(keyId: String): Result<Unit> = try {
        requireActor()
        remote.revokeApiKey(keyId)
        Result.success(Unit)
    } catch (error: Throwable) {
        M27IntegrationErrorMapper.failure(error)
    }

    override fun observeIntegrationApps(): Flow<List<com.comunidapp.app.data.model.M27PublicIntegrationApp>> = flow {
        if (actorUserId() == null) emit(emptyList())
        else emit(runCatching { remote.listMyIntegrationApps().map { it.toM27PublicIntegrationApp() } }.getOrElse { emptyList() })
    }

    override fun observeDeliveries(): Flow<List<com.comunidapp.app.data.model.M27PublicWebhookDelivery>> = flow {
        if (actorUserId() == null) emit(emptyList())
        else emit(runCatching { remote.listMyDeliveries().map { it.toM27PublicWebhookDelivery() } }.getOrElse { emptyList() })
    }

    override fun observeEvents(): Flow<List<com.comunidapp.app.data.model.M27PublicWebhookEvent>> = flow {
        if (actorUserId() == null) emit(emptyList())
        else emit(runCatching { remote.listMyEvents().map { it.toM27PublicWebhookEvent() } }.getOrElse { emptyList() })
    }

    override fun observeAuditLog(): Flow<List<com.comunidapp.app.data.model.M27PublicAuditEntry>> = flow {
        if (actorUserId() == null) emit(emptyList())
        else emit(runCatching { remote.listMyAuditLog().map { it.toM27PublicAuditEntry() } }.getOrElse { emptyList() })
    }

    override suspend fun createIntegrationApp(input: com.comunidapp.app.data.model.CreateM27IntegrationAppInput): Result<com.comunidapp.app.data.model.M27IntegrationApp> =
        remoteOps { remote.createIntegrationApp(input) }

    override suspend fun activateIntegrationApp(appId: String): Result<com.comunidapp.app.data.model.M27IntegrationApp> =
        remoteOps { remote.activateIntegrationApp(appId) }

    override suspend fun pauseIntegrationApp(appId: String): Result<com.comunidapp.app.data.model.M27IntegrationApp> =
        remoteOps { remote.pauseIntegrationApp(appId) }

    override suspend fun revokeIntegrationApp(appId: String): Result<com.comunidapp.app.data.model.M27IntegrationApp> =
        remoteOps { remote.revokeIntegrationApp(appId) }

    override suspend fun createApiKeyForApp(input: com.comunidapp.app.data.model.CreateM27ApiKeyInput): Result<com.comunidapp.app.data.model.M27IssuedCredential> =
        remoteOps { remote.createApiKeyForApp(input) }

    override suspend fun rotateApiKey(keyId: String): Result<com.comunidapp.app.data.model.M27IssuedCredential> =
        remoteOps { remote.rotateApiKey(keyId) }

    override suspend fun registerWebhookEndpoint(input: com.comunidapp.app.data.model.RegisterM27WebhookEndpointInput): Result<M27WebhookEndpointWithSecret> =
        remoteOps { remote.registerWebhookEndpoint(input) }

    override suspend fun verifyWebhookEndpoint(endpointId: String): Result<M27WebhookEndpoint> =
        remoteOps { remote.verifyWebhookEndpoint(endpointId) }

    override suspend fun subscribeWebhook(input: com.comunidapp.app.data.model.SubscribeM27WebhookInput): Result<com.comunidapp.app.data.model.M27WebhookSubscription> =
        remoteOps { remote.subscribeWebhook(input) }

    override suspend fun emitWebhookEvent(input: com.comunidapp.app.data.model.EmitM27WebhookEventInput): Result<com.comunidapp.app.data.model.M27WebhookEvent> =
        remoteOps { remote.emitWebhookEvent(input) }

    override suspend fun manualRetryDelivery(deliveryId: String): Result<com.comunidapp.app.data.model.M27WebhookDelivery> =
        remoteOps { remote.manualRetryDelivery(deliveryId) }

    override suspend fun checkAppRateLimit(appId: String, environment: com.comunidapp.app.data.model.M27Environment): com.comunidapp.app.data.model.M27RateLimitResult =
        runCatching { remote.checkAppRateLimit(appId, environment.name) }.getOrElse {
            com.comunidapp.app.data.model.M27RateLimitResult(false, "M27_REMOTE_NOT_READY", 60)
        }

    override suspend fun startOAuthStub(redirectUri: String, scopes: List<String>, state: String?): Result<com.comunidapp.app.data.model.M27OAuthStubSession> =
        remoteOps { remote.startOAuthStub(redirectUri, scopes, state) }

    private inline fun <T> remoteOps(block: () -> T): Result<T> = try {
        requireActor()
        Result.success(block())
    } catch (error: Throwable) {
        M27IntegrationErrorMapper.failure(error)
    }
}
