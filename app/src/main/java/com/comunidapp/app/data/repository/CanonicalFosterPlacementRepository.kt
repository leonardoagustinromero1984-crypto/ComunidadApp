package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.FosterPlacement
import com.comunidapp.app.data.model.FosterPlacementStatus
import com.comunidapp.app.data.remote.supabase.m08.M08RpcDecoding
import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.domain.canonical.CanonicalBackend
import com.comunidapp.app.domain.ux.CanonicalUiErrorMapper
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

@Serializable
private data class CanonicalFosterPlacementRow(
    val id: String,
    @SerialName("pet_id") val petId: String,
    @SerialName("pet_name") val petName: String? = null,
    @SerialName("foster_user_id") val fosterUserId: String,
    @SerialName("custody_id") val custodyId: String? = null,
    val status: String? = null,
    @SerialName("starts_at") val startsAt: String? = null,
    @SerialName("ends_at") val endsAt: String? = null,
    @SerialName("vitacora_access_granted") val vitacoraAccessGranted: Boolean? = null
)

class CanonicalFosterPlacementRepository : FosterPlacementRepository {
    private val cache = MutableStateFlow<List<FosterPlacement>>(emptyList())

    override fun observeActivePlacementsForHome(homeId: String): Flow<List<FosterPlacement>> =
        observeActivePlacementsForUser(homeId)

    override fun observeActivePlacementsForUser(userId: String): Flow<List<FosterPlacement>> = flow {
        runCatching { refresh() }
        cache.collect { list ->
            emit(
                list.filter {
                    it.fosterUserId == userId &&
                        (it.status == FosterPlacementStatus.ACTIVE || it.status == FosterPlacementStatus.RESERVED)
                }
            )
        }
    }

    override fun observePlacementsForOrganization(organizationId: String): Flow<List<FosterPlacement>> =
        cache.map { emptyList() }

    override fun observePlacementHistory(userId: String): Flow<List<FosterPlacement>> = flow {
        runCatching { refresh() }
        cache.collect { list ->
            emit(
                list.filter {
                    it.fosterUserId == userId &&
                        (it.status == FosterPlacementStatus.COMPLETED || it.status == FosterPlacementStatus.CANCELLED)
                }
            )
        }
    }

    override suspend fun getPlacementById(id: String): Result<FosterPlacement> {
        refresh()
        return cache.value.firstOrNull { it.id == id }?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException("FOSTER_PLACEMENT_NOT_FOUND"))
    }

    override suspend fun startPlacement(requestId: String, initialNotes: String?): Result<FosterPlacement> =
        Result.failure(IllegalStateException("FOSTER_REQUEST_ACCEPTANCE_USES_EXISTING_FLOW"))

    override suspend fun createDirectPlacement(
        petId: String,
        startsAtMillis: Long,
        endsAtMillis: Long?
    ): Result<FosterPlacement> = runCatching {
        val id: String = supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_CREATE_FOSTER_PLACEMENT,
            parameters = buildJsonObject {
                put("p_pet", petId)
                put("p_starts", Instant.ofEpochMilli(startsAtMillis).toString())
                if (endsAtMillis != null) put("p_ends", Instant.ofEpochMilli(endsAtMillis).toString())
                else put("p_ends", JsonNull)
            }
        ).decodeAs()
        refresh()
        cache.value.firstOrNull { it.id == id } ?: error("FOSTER_PLACEMENT_NOT_FOUND")
    }.recoverCatching { error ->
        throw IllegalStateException(
            CanonicalUiErrorMapper.userMessage(error, "No se pudo registrar el tránsito.")
        )
    }

    override suspend fun completePlacement(
        placementId: String,
        reason: com.comunidapp.app.data.model.FosterPlacementEndReason,
        notes: String?
    ): Result<FosterPlacement> = Result.failure(IllegalStateException("FOSTER_COMPLETE_NOT_FROM_DIRECTORY"))

    override suspend fun cancelReservedPlacement(placementId: String, reason: String?): Result<FosterPlacement> =
        Result.failure(IllegalStateException("FOSTER_CANCEL_NOT_FROM_DIRECTORY"))

    private suspend fun refresh() {
        val element: JsonElement = supabase.postgrest.rpc(CanonicalBackend.RPC_LIST_FOSTER_PLACEMENTS).decodeAs()
        cache.value = M08RpcDecoding.decodeRows<CanonicalFosterPlacementRow>(element).map { row ->
            val open = row.status.equals("OPEN", ignoreCase = true)
            FosterPlacement(
                id = row.id,
                fosterRequestId = "",
                fosterHomeId = row.fosterUserId,
                petId = row.petId,
                petName = row.petName,
                fosterUserId = row.fosterUserId,
                status = when {
                    open -> FosterPlacementStatus.ACTIVE
                    row.status.equals("CANCELLED", ignoreCase = true) -> FosterPlacementStatus.CANCELLED
                    else -> FosterPlacementStatus.COMPLETED
                },
                startedAt = parseMillis(row.startsAt),
                estimatedEndAt = parseMillis(row.endsAt)?.takeIf { it > 0 },
                temporaryResponsibilityId = row.custodyId,
                vitacoraAccessGranted = row.vitacoraAccessGranted
            )
        }
    }

    private fun parseMillis(value: String?): Long =
        value?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L
}
