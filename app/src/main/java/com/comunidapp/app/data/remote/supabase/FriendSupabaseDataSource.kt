package com.comunidapp.app.data.remote.supabase

import com.comunidapp.app.data.model.FriendConnection
import com.comunidapp.app.data.model.FriendConnectionStatus
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class FriendConnectionRow(
    val id: String,
    @SerialName("requester_id") val requesterId: String,
    @SerialName("addressee_id") val addresseeId: String,
    val status: String = FriendConnectionStatus.PENDING.name,
    @SerialName("created_at") val createdAt: String? = null
)

class FriendSupabaseDataSource {

    suspend fun fetchConnections(userId: String): List<FriendConnection> {
        return try {
            val asRequester = supabase.from(SupabaseTables.FRIEND_CONNECTIONS)
                .select {
                    filter { eq("requester_id", userId) }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<FriendConnectionRow>()

            val asAddressee = supabase.from(SupabaseTables.FRIEND_CONNECTIONS)
                .select {
                    filter { eq("addressee_id", userId) }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<FriendConnectionRow>()

            (asRequester + asAddressee)
                .distinctBy { it.id }
                .map(::parseFriendConnection)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun sendFriendRequest(requesterId: String, addresseeId: String): Result<Unit> {
        if (requesterId == addresseeId) {
            return Result.failure(IllegalArgumentException("No podés enviarte una solicitud a vos mismo"))
        }
        return try {
            val existing = fetchConnections(requesterId).firstOrNull { conn ->
                (conn.requesterId == requesterId && conn.addresseeId == addresseeId) ||
                    (conn.requesterId == addresseeId && conn.addresseeId == requesterId)
            }
            when {
                existing?.status == FriendConnectionStatus.ACCEPTED ->
                    return Result.failure(IllegalArgumentException("Ya son amigos"))
                existing?.status == FriendConnectionStatus.PENDING ->
                    return Result.failure(IllegalArgumentException("Ya hay una solicitud pendiente"))
                existing?.status == FriendConnectionStatus.REJECTED -> {
                    supabase.from(SupabaseTables.FRIEND_CONNECTIONS).update(
                        mapOf(
                            "status" to FriendConnectionStatus.PENDING.name,
                            "requester_id" to requesterId,
                            "addressee_id" to addresseeId,
                            "updated_at" to nowIso()
                        )
                    ) { filter { eq("id", existing.id) } }
                }
                else -> {
                    supabase.from(SupabaseTables.FRIEND_CONNECTIONS).insert(
                        FriendConnectionRow(
                            id = UUID.randomUUID().toString(),
                            requesterId = requesterId,
                            addresseeId = addresseeId,
                            status = FriendConnectionStatus.PENDING.name,
                            createdAt = nowIso()
                        )
                    )
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun respondToRequest(
        connectionId: String,
        status: FriendConnectionStatus,
        responderId: String
    ): Result<Unit> {
        return try {
            supabase.from(SupabaseTables.FRIEND_CONNECTIONS).update(
                mapOf(
                    "status" to status.name,
                    "updated_at" to nowIso()
                )
            ) {
                filter {
                    eq("id", connectionId)
                    eq("addressee_id", responderId)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteConnection(connectionId: String, requesterId: String): Result<Unit> {
        return try {
            supabase.from(SupabaseTables.FRIEND_CONNECTIONS).delete {
                filter {
                    eq("id", connectionId)
                    eq("requester_id", requesterId)
                    eq("status", FriendConnectionStatus.PENDING.name)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeConnections(userId: String): Flow<List<FriendConnection>> = friendPollingFlow {
        fetchConnections(userId)
    }
}

fun parseFriendConnection(row: FriendConnectionRow): FriendConnection = FriendConnection(
    id = row.id,
    requesterId = row.requesterId,
    addresseeId = row.addresseeId,
    status = FriendConnectionStatus.fromString(row.status),
    createdAt = row.createdAt?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
)

private fun friendPollingFlow(fetch: suspend () -> List<FriendConnection>): Flow<List<FriendConnection>> = flow {
    while (coroutineContext.isActive) {
        emit(runCatching { fetch() }.getOrDefault(emptyList()))
        delay(4_000)
    }
}
