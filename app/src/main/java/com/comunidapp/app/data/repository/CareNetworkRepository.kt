package com.comunidapp.app.data.repository

import com.comunidapp.app.data.remote.supabase.SupabaseRowDecoding
import com.comunidapp.app.data.remote.supabase.m08.M08RpcDecoding
import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.domain.canonical.CanonicalBackend
import com.comunidapp.app.domain.pets.CareNetworkInvite
import com.comunidapp.app.domain.pets.CareNetworkPet
import com.comunidapp.app.domain.pets.CareNetworkRole
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

interface CareNetworkRepository {
    suspend fun invite(petId: String, personId: String, role: CareNetworkRole): Result<String>
    suspend fun accept(linkId: String): Result<Unit>
    suspend fun reject(linkId: String): Result<Unit>
    suspend fun leave(linkId: String): Result<Unit>
    suspend fun listMyInvites(): Result<List<CareNetworkInvite>>
    suspend fun listMyCarePets(): Result<List<CareNetworkPet>>
}

@Serializable
private data class CareNetworkRowDto(
    @SerialName("link_id") val linkId: String,
    @SerialName("pet_id") val petId: String,
    @SerialName("owner_name") val ownerName: String? = null,
    @SerialName("pet_name") val petName: String? = null,
    @SerialName("care_role") val careRole: String? = null,
    val status: String? = null
)

class SupabaseCareNetworkRepository : CareNetworkRepository {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun invite(
        petId: String,
        personId: String,
        role: CareNetworkRole
    ): Result<String> = runCatching {
        com.comunidapp.app.domain.auth.AuthSessionAccess.requireUser(supabase.auth)
        val result = supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_INVITE_PET_RESPONSIBLE,
            parameters = buildJsonObject {
                put("p_pet_id", petId)
                put("p_person_id", personId)
            }
        )
        val element = runCatching { result.decodeAs<JsonElement>() }
            .getOrElse { json.parseToJsonElement(result.data) }
        SupabaseRowDecoding.decodeUuid(element)
            ?: SupabaseRowDecoding.decodeUuidFromRaw(result.data)
            ?: error("CARE_INVITE_EMPTY")
    }

    override suspend fun accept(linkId: String): Result<Unit> = rpcUnit(
        CanonicalBackend.RPC_ACCEPT_CARE_INVITE,
        linkId
    )

    override suspend fun reject(linkId: String): Result<Unit> = rpcUnit(
        CanonicalBackend.RPC_REJECT_CARE_INVITE,
        linkId
    )

    override suspend fun leave(linkId: String): Result<Unit> = rpcUnit(
        CanonicalBackend.RPC_LEAVE_CARE_NETWORK,
        linkId
    )

    override suspend fun listMyInvites(): Result<List<CareNetworkInvite>> = runCatching {
        decodeRows(CanonicalBackend.RPC_LIST_MY_CARE_INVITES).map { row ->
            CareNetworkInvite(
                linkId = row.linkId,
                petId = row.petId,
                petName = row.petName.orEmpty(),
                ownerName = row.ownerName.orEmpty(),
                role = CareNetworkRole.fromRaw(row.careRole) ?: CareNetworkRole.OTHER,
                status = row.status.orEmpty()
            )
        }
    }

    override suspend fun listMyCarePets(): Result<List<CareNetworkPet>> = runCatching {
        decodeRows(CanonicalBackend.RPC_LIST_MY_CARE_PETS).map { row ->
            CareNetworkPet(
                linkId = row.linkId,
                petId = row.petId,
                petName = row.petName.orEmpty(),
                role = CareNetworkRole.fromRaw(row.careRole) ?: CareNetworkRole.OTHER,
                ownerName = row.ownerName.orEmpty()
            )
        }
    }

    private suspend fun rpcUnit(fn: String, linkId: String): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            function = fn,
            parameters = buildJsonObject { put("p_link_id", linkId) }
        )
        Unit
    }

    private suspend fun decodeRows(fn: String): List<CareNetworkRowDto> {
        val result = supabase.postgrest.rpc(function = fn)
        val element = runCatching { result.decodeAs<JsonElement>() }
            .getOrElse { json.parseToJsonElement(result.data) }
        return M08RpcDecoding.decodeRows(element)
    }
}
