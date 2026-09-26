package com.comunidapp.app.domain.social

import com.comunidapp.app.data.model.FriendConnection
import com.comunidapp.app.data.model.FriendConnectionStatus

enum class AuthorFriendship {
    NONE,
    PENDING,
    ACCEPTED
}

object AuthorFriendshipResolver {

    fun between(
        viewerId: String?,
        authorId: String?,
        connections: List<FriendConnection>
    ): AuthorFriendship {
        if (viewerId.isNullOrBlank() || authorId.isNullOrBlank()) return AuthorFriendship.NONE
        if (viewerId == authorId) return AuthorFriendship.ACCEPTED
        val link = connections.firstOrNull { conn ->
            involves(conn, viewerId, authorId) &&
                conn.status != FriendConnectionStatus.REJECTED
        } ?: return AuthorFriendship.NONE
        return when (link.status) {
            FriendConnectionStatus.ACCEPTED -> AuthorFriendship.ACCEPTED
            FriendConnectionStatus.PENDING -> AuthorFriendship.PENDING
            FriendConnectionStatus.REJECTED -> AuthorFriendship.NONE
        }
    }

    private fun involves(connection: FriendConnection, userId: String, otherId: String): Boolean =
        (connection.requesterId == userId && connection.addresseeId == otherId) ||
            (connection.requesterId == otherId && connection.addresseeId == userId)
}
