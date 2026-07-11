package com.comunidapp.app.data.mock

import com.comunidapp.app.data.model.Friendship
import com.comunidapp.app.data.model.FriendshipStatus
import com.comunidapp.app.data.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

object MockFriendsStore {

    private val friendships = MutableStateFlow(MockData.initialFriendships)

    fun observeFriendships(userId: String): Flow<List<Friendship>> =
        friendships.map { list ->
            list.filter {
                (it.userId == userId || it.friendId == userId) &&
                    it.status == FriendshipStatus.ACCEPTED
            }
        }

    fun observePendingReceived(userId: String): Flow<List<Friendship>> =
        friendships.map { list ->
            list.filter {
                it.friendId == userId && it.status == FriendshipStatus.PENDING
            }
        }

    fun getFriendIds(userId: String): List<String> =
        friendships.value
            .filter {
                it.status == FriendshipStatus.ACCEPTED &&
                    (it.userId == userId || it.friendId == userId)
            }
            .map { if (it.userId == userId) it.friendId else it.userId }

    fun isFriend(userId: String, otherUserId: String): Boolean =
        getFriendIds(userId).contains(otherUserId)

    fun hasPendingRequest(fromUserId: String, toUserId: String): Boolean =
        friendships.value.any {
            it.userId == fromUserId &&
                it.friendId == toUserId &&
                it.status == FriendshipStatus.PENDING
        }

    fun sendRequest(fromUserId: String, toUserId: String): Result<Unit> {
        if (fromUserId == toUserId) return Result.failure(IllegalArgumentException("No podés agregarte a vos mismo"))
        if (isFriend(fromUserId, toUserId)) return Result.failure(IllegalStateException("Ya son amigos"))
        if (hasPendingRequest(fromUserId, toUserId)) return Result.failure(IllegalStateException("Solicitud ya enviada"))
        friendships.update { current ->
            current + Friendship(fromUserId, toUserId, FriendshipStatus.PENDING)
        }
        return Result.success(Unit)
    }

    fun acceptRequest(userId: String, fromUserId: String): Result<Unit> {
        val index = friendships.value.indexOfFirst {
            it.userId == fromUserId && it.friendId == userId && it.status == FriendshipStatus.PENDING
        }
        if (index < 0) return Result.failure(IllegalStateException("Solicitud no encontrada"))
        friendships.update { current ->
            current.toMutableList().apply {
                this[index] = Friendship(fromUserId, userId, FriendshipStatus.ACCEPTED)
            }
        }
        return Result.success(Unit)
    }

    fun resolveFriends(userId: String): List<User> =
        getFriendIds(userId).mapNotNull { friendId ->
            MockUserStore.get(friendId) ?: MockData.users.find { it.id == friendId }
        }
}
