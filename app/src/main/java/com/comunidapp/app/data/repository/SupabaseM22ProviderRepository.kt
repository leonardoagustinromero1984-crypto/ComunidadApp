package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.CreateM22ProviderInput
import com.comunidapp.app.data.model.M22ProviderBranch
import com.comunidapp.app.data.model.M22ProviderProfile
import com.comunidapp.app.data.model.M22PublicProviderDetail
import com.comunidapp.app.data.model.M22PublicProviderListing
import com.comunidapp.app.data.model.M22ServiceOffering
import com.comunidapp.app.data.model.UpdateM22ProviderInput
import com.comunidapp.app.data.model.UpsertM22BranchInput
import com.comunidapp.app.data.model.UpsertM22OfferingInput
import com.comunidapp.app.data.remote.supabase.m22.M22ProviderErrorMapper
import com.comunidapp.app.data.remote.supabase.m22.SupabaseM22RemoteDataSource
import com.comunidapp.app.data.remote.supabase.m22.toM22ProviderBranch
import com.comunidapp.app.data.remote.supabase.m22.toM22ProviderProfile
import com.comunidapp.app.data.remote.supabase.m22.toM22PublicDetail
import com.comunidapp.app.data.remote.supabase.m22.toM22PublicListing
import com.comunidapp.app.data.remote.supabase.m22.toM22ServiceOffering
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class SupabaseM22ProviderRepository(
    private val remote: SupabaseM22RemoteDataSource = SupabaseM22RemoteDataSource(),
    private val actorUserId: () -> String? = { null }
) : M22ProviderRepository {

    private fun requireActor() {
        if (actorUserId() == null) throw M22ProviderException(
            "NOT_AUTHENTICATED", M22ProviderErrors.userMessage("NOT_AUTHENTICATED")
        )
    }

    override fun observeCatalog(category: com.comunidapp.app.data.model.M22ProviderCategory?): Flow<List<M22PublicProviderListing>> =
        flow {
            emit(runCatching { remote.listCatalog(category?.name, null).map { it.toM22PublicListing() } }
                .getOrElse { emptyList() })
        }

    override fun observeProviderDetail(providerId: String): Flow<M22PublicProviderDetail?> = flow {
        emit(runCatching { remote.getProviderDetail(providerId).toM22PublicDetail() }.getOrNull())
    }

    override fun observeMyProviders(): Flow<List<M22ProviderProfile>> = flow {
        if (actorUserId() == null) {
            emit(emptyList())
        } else {
            emit(runCatching { remote.listMyProviders().map { it.toM22ProviderProfile() } }.getOrElse { emptyList() })
        }
    }

    override suspend fun createProvider(input: CreateM22ProviderInput): Result<M22ProviderProfile> = try {
        requireActor()
        M22ProviderValidators.validateProvider(input.displayName, input.description, input.city)?.let {
            return M22ProviderErrorMapper.fail(it)
        }
        Result.success(remote.createProvider(
            input.displayName.trim(), input.category.name, input.description.trim(), input.city.trim(), input.organizationId
        ).toM22ProviderProfile())
    } catch (error: Throwable) {
        M22ProviderErrorMapper.failure(error)
    }

    override suspend fun updateProvider(input: UpdateM22ProviderInput): Result<M22ProviderProfile> = try {
        requireActor()
        Result.success(remote.updateProvider(
            input.providerId, input.displayName?.trim(), input.description?.trim(), input.city?.trim(), input.status?.name
        ).toM22ProviderProfile())
    } catch (error: Throwable) {
        M22ProviderErrorMapper.failure(error)
    }

    override suspend fun upsertBranch(input: UpsertM22BranchInput): Result<M22ProviderBranch> = try {
        requireActor()
        M22ProviderValidators.validateBranch(input.name, input.city, input.coverage)?.let {
            return M22ProviderErrorMapper.fail(it)
        }
        Result.success(remote.upsertBranch(
            input.providerId, input.branchId, input.name.trim(), input.city.trim(), input.neighborhood?.trim(),
            input.coverage, input.status.name
        ).toM22ProviderBranch())
    } catch (error: Throwable) {
        M22ProviderErrorMapper.failure(error)
    }

    override suspend fun upsertOffering(input: UpsertM22OfferingInput): Result<M22ServiceOffering> = try {
        requireActor()
        M22ProviderValidators.validateOffering(input.name, input.description, input.priceType, input.priceAmount)?.let {
            return M22ProviderErrorMapper.fail(it)
        }
        Result.success(remote.upsertOffering(
            input.providerId, input.offeringId, input.branchId, input.name.trim(), input.description.trim(),
            input.priceType.name, input.priceAmount, input.currency, input.active
        ).toM22ServiceOffering())
    } catch (error: Throwable) {
        M22ProviderErrorMapper.failure(error)
    }

    override suspend fun archiveProvider(providerId: String): Result<Unit> = try {
        requireActor()
        remote.archiveProvider(providerId)
        Result.success(Unit)
    } catch (error: Throwable) {
        M22ProviderErrorMapper.failure(error)
    }
}
