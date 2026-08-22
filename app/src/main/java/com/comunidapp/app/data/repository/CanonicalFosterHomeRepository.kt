package com.comunidapp.app.data.repository

import com.comunidapp.app.data.remote.supabase.m08.M08RpcDecoding
import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.domain.canonical.CanonicalBackend
import com.comunidapp.app.domain.context.OperationalContextProvider
import com.comunidapp.app.domain.ux.CanonicalUiErrorMapper
import com.comunidapp.app.data.model.FosterAvailabilityStatus
import com.comunidapp.app.data.model.FosterHomeProfile
import com.comunidapp.app.data.model.FosterHomePublicListing
import com.comunidapp.app.data.model.FosterHomeStatus
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
private data class CanonicalFosterProfileRow(
    @SerialName("user_id") val userId: String,
    val capacity: Int = 1,
    val active: Boolean = true,
    @SerialName("locality_id") val localityId: String? = null
)

/**
 * Canonical foster_profiles. Personal function — never creates an organization.
 */
class CanonicalFosterHomeRepository : FosterHomeRepository {
    private val mine = MutableStateFlow<FosterHomeProfile?>(null)

    override fun observeAvailableFosterHomes(): Flow<List<FosterHomePublicListing>> = flow {
        runCatching { refresh() }
        emitAll(mine.map { profile ->
            profile?.takeIf { it.status == FosterHomeStatus.ACTIVE }?.let { listOf(it.toPublicListing()) }.orEmpty()
        })
    }

    override fun observeMyFosterHome(ownerUserId: String): Flow<FosterHomeProfile?> = flow {
        runCatching { refresh() }
        emitAll(mine)
    }

    override suspend fun getFosterHomeById(id: String): Result<FosterHomeProfile> {
        refresh()
        return mine.value?.takeIf { it.id == id }?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException("FOSTER_HOME_NOT_FOUND"))
    }

    override suspend fun getPublicFosterHomeById(id: String): Result<FosterHomePublicListing> =
        getFosterHomeById(id).map { it.toPublicListing() }

    override suspend fun createFosterHome(input: CreateFosterHomeInput): Result<FosterHomeProfile> =
        upsert(input.totalCapacity, input.localityId)

    override suspend fun updateFosterHome(input: UpdateFosterHomeInput): Result<FosterHomeProfile> =
        upsert(input.totalCapacity, input.localityId)

    override suspend fun changeAvailability(
        homeId: String,
        availability: FosterAvailabilityStatus
    ): Result<FosterHomeProfile> = mine.value?.let { Result.success(it) }
        ?: Result.failure(IllegalStateException("FOSTER_HOME_NOT_FOUND"))

    override suspend fun setHomeStatus(homeId: String, status: FosterHomeStatus): Result<FosterHomeProfile> =
        mine.value?.let { Result.success(it.copy(status = status)) }
            ?: Result.failure(IllegalStateException("FOSTER_HOME_NOT_FOUND"))

    private suspend fun upsert(capacity: Int, localityId: String?): Result<FosterHomeProfile> =
        runCatching {
            supabase.postgrest.rpc(
                function = CanonicalBackend.RPC_UPSERT_FOSTER_PROFILE,
                parameters = buildJsonObject {
                    put("p_capacity", capacity.coerceAtLeast(1))
                    localityId?.takeIf { it.isNotBlank() }?.let { put("p_locality_id", it) }
                }
            )
            runCatching { refresh() }
            val uid = AuthProvider.repository.getCurrentUser()?.id.orEmpty()
            val saved = mine.value ?: CanonicalFosterProfileRow(
                userId = uid,
                capacity = capacity.coerceAtLeast(1),
                active = true,
                localityId = localityId
            ).toProfile().also { mine.value = it }
            runCatching { OperationalContextProvider.refresh(saved.ownerUserId) }
            saved
        }.recoverCatching { error ->
            throw IllegalStateException(CanonicalUiErrorMapper.userMessage(error))
        }

    private suspend fun refresh() {
        val element: JsonElement = supabase.postgrest.rpc(CanonicalBackend.RPC_GET_MY_FOSTER_PROFILE).decodeAs()
        val row = runCatching { M08RpcDecoding.decodeRow<CanonicalFosterProfileRow>(element) }.getOrNull()
            ?: runCatching { M08RpcDecoding.decodeRows<CanonicalFosterProfileRow>(element).firstOrNull() }.getOrNull()
        if (row != null) mine.value = row.toProfile()
    }

    private fun CanonicalFosterProfileRow.toProfile(): FosterHomeProfile {
        val now = System.currentTimeMillis()
        return FosterHomeProfile(
            id = userId,
            ownerUserId = userId,
            displayName = "Hogar de tránsito",
            status = if (active) FosterHomeStatus.ACTIVE else FosterHomeStatus.PAUSED,
            availabilityStatus = if (active) {
                FosterAvailabilityStatus.AVAILABLE
            } else {
                FosterAvailabilityStatus.UNAVAILABLE
            },
            totalCapacity = capacity,
            zoneText = com.comunidapp.app.domain.ux.HumanLocationLabel.visible(localityId),
            publicLocationText = com.comunidapp.app.domain.ux.HumanLocationLabel.visible(localityId)
                .ifBlank { null },
            createdAt = now,
            updatedAt = now
        )
    }
}
