package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.FriendConnection
import com.comunidapp.app.data.model.FriendConnectionStatus
import com.comunidapp.app.data.mock.InMemoryDataStore
import com.comunidapp.app.data.remote.supabase.FriendSupabaseDataSource
import kotlinx.coroutines.flow.Flow

interface FriendRepository {
    fun observeConnections(userId: String): Flow<List<FriendConnection>>
    suspend fun sendFriendRequest(requesterId: String, addresseeId: String): Result<Unit>
    suspend fun respondToRequest(
        connectionId: String,
        accept: Boolean,
        responderId: String
    ): Result<Unit>
    suspend fun cancelRequest(connectionId: String, requesterId: String): Result<Unit>
}

class MockFriendRepository : FriendRepository {
    override fun observeConnections(userId: String): Flow<List<FriendConnection>> =
        InMemoryDataStore.observeFriendConnections(userId)

    override suspend fun sendFriendRequest(requesterId: String, addresseeId: String): Result<Unit> =
        InMemoryDataStore.sendFriendRequest(requesterId, addresseeId)

    override suspend fun respondToRequest(
        connectionId: String,
        accept: Boolean,
        responderId: String
    ): Result<Unit> = InMemoryDataStore.respondToFriendRequest(
        connectionId,
        accept,
        responderId
    )

    override suspend fun cancelRequest(connectionId: String, requesterId: String): Result<Unit> =
        InMemoryDataStore.cancelFriendRequest(connectionId, requesterId)
}

class SupabaseFriendRepository(
    private val dataSource: FriendSupabaseDataSource = FriendSupabaseDataSource()
) : FriendRepository {
    override fun observeConnections(userId: String): Flow<List<FriendConnection>> =
        dataSource.observeConnections(userId)

    override suspend fun sendFriendRequest(requesterId: String, addresseeId: String): Result<Unit> =
        dataSource.sendFriendRequest(requesterId, addresseeId)

    override suspend fun respondToRequest(
        connectionId: String,
        accept: Boolean,
        responderId: String
    ): Result<Unit> = dataSource.respondToRequest(
        connectionId,
        if (accept) FriendConnectionStatus.ACCEPTED else FriendConnectionStatus.REJECTED,
        responderId
    )

    override suspend fun cancelRequest(connectionId: String, requesterId: String): Result<Unit> =
        dataSource.deleteConnection(connectionId, requesterId)
}
