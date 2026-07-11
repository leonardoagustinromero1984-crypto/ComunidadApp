package com.comunidapp.app.data.repository

import com.comunidapp.app.data.mock.MockFriendsStore
import com.comunidapp.app.data.model.Friendship
import com.comunidapp.app.data.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface FriendsRepository {
    fun observeFriends(userId: String): Flow<List<User>>
    fun observePendingRequests(userId: String): Flow<List<Friendship>>
    suspend fun searchUsers(query: String, currentUserId: String): List<User>
    suspend fun sendFriendRequest(fromUserId: String, toUserId: String): Result<Unit>
    suspend fun acceptFriendRequest(userId: String, fromUserId: String): Result<Unit>
    suspend fun isFriend(userId: String, otherUserId: String): Boolean
    suspend fun hasPendingRequest(fromUserId: String, toUserId: String): Boolean
}

class MockFriendsRepository(
    private val userRepository: UserRepository
) : FriendsRepository {

    override fun observeFriends(userId: String): Flow<List<User>> =
        MockFriendsStore.observeFriendships(userId).map { MockFriendsStore.resolveFriends(userId) }

    override fun observePendingRequests(userId: String): Flow<List<Friendship>> =
        MockFriendsStore.observePendingReceived(userId)

    override suspend fun searchUsers(query: String, currentUserId: String): List<User> {
        if (query.isBlank()) return emptyList()
        val normalized = query.trim().lowercase()
        val friendIds = MockFriendsStore.getFriendIds(currentUserId).toSet()
        return userRepository.searchUsers(normalized, currentUserId)
            .filter { it.id !in friendIds }
            .take(20)
    }

    override suspend fun sendFriendRequest(fromUserId: String, toUserId: String): Result<Unit> =
        MockFriendsStore.sendRequest(fromUserId, toUserId)

    override suspend fun acceptFriendRequest(userId: String, fromUserId: String): Result<Unit> =
        MockFriendsStore.acceptRequest(userId, fromUserId)

    override suspend fun isFriend(userId: String, otherUserId: String): Boolean =
        MockFriendsStore.isFriend(userId, otherUserId)

    override suspend fun hasPendingRequest(fromUserId: String, toUserId: String): Boolean =
        MockFriendsStore.hasPendingRequest(fromUserId, toUserId)
}
