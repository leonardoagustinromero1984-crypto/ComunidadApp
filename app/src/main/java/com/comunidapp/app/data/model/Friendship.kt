package com.comunidapp.app.data.model

enum class FriendshipStatus {
    PENDING,
    ACCEPTED
}

data class Friendship(
    val userId: String,
    val friendId: String,
    val status: FriendshipStatus = FriendshipStatus.ACCEPTED
)
