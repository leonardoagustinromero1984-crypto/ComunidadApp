package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.CreateM15FosterHomeInput
import com.comunidapp.app.data.model.FosterHomeStatus
import com.comunidapp.app.data.model.M15FosterHome
import com.comunidapp.app.data.model.M15FosterHomePublicListing
import com.comunidapp.app.data.model.M15FosterPlacement
import com.comunidapp.app.data.model.M15FosterRequest
import com.comunidapp.app.data.model.SubmitM15FosterRequestInput
import com.comunidapp.app.data.model.UpdateM15FosterHomeInput
import com.comunidapp.app.data.remote.supabase.m15.M15ErrorMapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * LeoVer M15 — adaptadores Supabase sobre persistencia M10 autoritativa (Bloque 2).
 * Sin tablas paralelas; delega en [FosterHomeRepository] / RPC m10_*.
 */

class SupabaseM15FosterHomeRepository(
    private val delegate: FosterHomeRepository
) : M15FosterHomeRepository {

    override fun observeAvailableHomes(): Flow<List<M15FosterHomePublicListing>> =
        delegate.observeAvailableFosterHomes().map { list -> list.map { it.toM15() } }

    override fun observeMyHome(ownerUserId: String): Flow<M15FosterHome?> =
        delegate.observeMyFosterHome(ownerUserId).map { it?.toM15() }

    override suspend fun getHomeById(id: String): Result<M15FosterHome> =
        delegate.getFosterHomeById(id).mapM15Failure { it.toM15() }

    override suspend fun getPublicHomeById(id: String): Result<M15FosterHomePublicListing> =
        delegate.getPublicFosterHomeById(id).mapM15Failure { it.toM15() }

    override suspend fun createHome(input: CreateM15FosterHomeInput): Result<M15FosterHome> =
        delegate.createFosterHome(input.toFoster()).mapM15Failure { it.toM15() }

    override suspend fun updateHome(input: UpdateM15FosterHomeInput): Result<M15FosterHome> =
        delegate.updateFosterHome(input.toFoster()).mapM15Failure { it.toM15() }

    override suspend fun activateHome(homeId: String): Result<M15FosterHome> =
        delegate.setHomeStatus(homeId, FosterHomeStatus.ACTIVE)
            .mapM15Failure { it.toM15() }
}

class SupabaseM15FosterRequestRepository(
    private val delegate: FosterRequestRepository
) : M15FosterRequestRepository {

    override fun observeSentRequests(userId: String): Flow<List<M15FosterRequest>> =
        delegate.observeSentRequests(userId).map { list -> list.map { it.toM15() } }

    override fun observeReceivedRequests(ownerUserId: String): Flow<List<M15FosterRequest>> =
        delegate.observeReceivedRequests(ownerUserId).map { list -> list.map { it.toM15() } }

    override suspend fun getRequestById(id: String): Result<M15FosterRequest> =
        delegate.getRequestById(id).mapM15Failure { it.toM15() }

    override suspend fun submitRequest(input: SubmitM15FosterRequestInput): Result<M15FosterRequest> =
        delegate.submitRequest(input.toFoster()).mapM15Failure { it.toM15() }

    override suspend fun cancelRequest(requestId: String): Result<M15FosterRequest> =
        delegate.cancelRequest(requestId).mapM15Failure { it.toM15() }

    override suspend fun markUnderReview(requestId: String): Result<M15FosterRequest> =
        delegate.markUnderReview(requestId).mapM15Failure { it.toM15() }

    override suspend fun acceptRequest(requestId: String): Result<M15FosterRequest> =
        delegate.acceptRequest(requestId).mapM15Failure { it.toM15() }

    override suspend fun rejectRequest(requestId: String, reason: String?): Result<M15FosterRequest> =
        delegate.rejectRequest(requestId, reason).mapM15Failure { it.toM15() }
}

class SupabaseM15FosterPlacementRepository(
    private val delegate: FosterPlacementRepository
) : M15FosterPlacementRepository {

    override fun observeActivePlacementsForHome(homeId: String): Flow<List<M15FosterPlacement>> =
        delegate.observeActivePlacementsForHome(homeId).map { list -> list.map { it.toM15() } }

    override fun observeActivePlacementsForUser(userId: String): Flow<List<M15FosterPlacement>> =
        delegate.observeActivePlacementsForUser(userId).map { list -> list.map { it.toM15() } }

    override fun observePlacementsForOrganization(organizationId: String): Flow<List<M15FosterPlacement>> =
        kotlinx.coroutines.flow.flow {
            emit(emptyList())
        }

    override suspend fun getPlacementById(id: String): Result<M15FosterPlacement> =
        delegate.getPlacementById(id).mapM15Failure { it.toM15() }

    override suspend fun startPlacement(
        requestId: String,
        initialNotes: String?
    ): Result<M15FosterPlacement> =
        delegate.startPlacement(requestId, initialNotes).mapM15Failure { it.toM15() }
}

private inline fun <T, R> Result<T>.mapM15Failure(transform: (T) -> R): Result<R> =
    fold(
        onSuccess = { Result.success(transform(it)) },
        onFailure = { M15ErrorMapper.failure(it) }
    )
