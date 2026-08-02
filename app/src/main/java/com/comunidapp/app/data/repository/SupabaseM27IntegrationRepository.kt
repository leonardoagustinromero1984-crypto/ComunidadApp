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
}
