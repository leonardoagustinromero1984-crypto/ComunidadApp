package com.comunidapp.app.data.repository

import com.comunidapp.app.core.logging.AppLog
import com.comunidapp.app.data.model.FriendConnection
import com.comunidapp.app.data.model.FriendConnectionStatus
import com.comunidapp.app.data.remote.supabase.m08.M08RpcDecoding
import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.domain.canonical.CanonicalBackend
import com.comunidapp.app.domain.social.FriendshipErrorMapper
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.coroutines.coroutineContext

@Serializable
private data class CanonicalFriendshipRow(
    val id: String,
    @SerialName("requester_id") val requesterId: String,
    @SerialName("addressee_id") val addresseeId: String,
    val status: String = "PENDING",
    @SerialName("created_at") val createdAt: String? = null
)

/**
 * Canonical friendships via security-definer RPCs (migration 1042).
 * Direct table access is not required from Android.
 */
class CanonicalFriendRepository : FriendRepository {

    override fun observeConnections(userId: String): Flow<List<FriendConnection>> = flow {
        while (coroutineContext.isActive) {
            emit(
                runCatching { fetchConnections() }
                    .onFailure { e ->
                        AppLog.warning("Friendships", "LOAD failed", e)
                    }
                    .getOrDefault(emptyList())
            )
            delay(4_000)
        }
    }

    override suspend fun sendFriendRequest(requesterId: String, addresseeId: String): Result<Unit> {
        if (requesterId == addresseeId) {
            return Result.failure(IllegalArgumentException("No podés enviarte una solicitud a vos mismo"))
        }
        return try {
            supabase.postgrest.rpc(
                function = CanonicalBackend.RPC_SEND_FRIEND_REQUEST,
                parameters = buildJsonObject { put("p_addressee_id", addresseeId) }
            )
            Result.success(Unit)
        } catch (e: Exception) {
            AppLog.warning("Friendships", "SEND failed", e)
            Result.failure(
                IllegalArgumentException(
                    FriendshipErrorMapper.userMessage(e, FriendshipErrorMapper.Operation.SEND)
                )
            )
        }
    }

    override suspend fun respondToRequest(
        connectionId: String,
        accept: Boolean,
        responderId: String
    ): Result<Unit> = try {
        supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_RESPOND_FRIEND_REQUEST,
            parameters = buildJsonObject {
                put("p_connection_id", connectionId)
                put("p_accept", accept)
            }
        )
        Result.success(Unit)
    } catch (e: Exception) {
        AppLog.warning("Friendships", "RESPOND failed", e)
        Result.failure(
            IllegalArgumentException(
                FriendshipErrorMapper.userMessage(e, FriendshipErrorMapper.Operation.RESPOND)
            )
        )
    }

    override suspend fun removeAcceptedConnection(
        connectionId: String,
        actorUserId: String
    ): Result<Unit> = try {
        supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_REMOVE_FRIENDSHIP,
            parameters = buildJsonObject { put("p_connection_id", connectionId) }
        )
        Result.success(Unit)
    } catch (e: Exception) {
        AppLog.warning("Friendships", "REMOVE failed", e)
        Result.failure(
            IllegalArgumentException(
                FriendshipErrorMapper.userMessage(e, FriendshipErrorMapper.Operation.REMOVE)
            )
        )
    }

    override suspend fun cancelRequest(connectionId: String, requesterId: String): Result<Unit> = try {
        supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_CANCEL_FRIEND_REQUEST,
            parameters = buildJsonObject { put("p_connection_id", connectionId) }
        )
        Result.success(Unit)
    } catch (e: Exception) {
        AppLog.warning("Friendships", "CANCEL failed", e)
        Result.failure(
            IllegalArgumentException(
                FriendshipErrorMapper.userMessage(e, FriendshipErrorMapper.Operation.CANCEL)
            )
        )
    }

    private suspend fun fetchConnections(): List<FriendConnection> {
        val element: JsonElement = supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_LIST_MY_FRIENDSHIPS
        ).decodeAs()
        return M08RpcDecoding.decodeRows<CanonicalFriendshipRow>(element).map { row ->
            FriendConnection(
                id = row.id,
                requesterId = row.requesterId,
                addresseeId = row.addresseeId,
                status = FriendConnectionStatus.fromString(row.status),
                createdAt = row.createdAt?.let {
                    runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull()
                }
            )
        }
    }
}
