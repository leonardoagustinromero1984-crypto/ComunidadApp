package com.comunidapp.app.domain

import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.model.FriendConnection
import com.comunidapp.app.data.model.FriendConnectionStatus
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.ProfileRelation
import com.comunidapp.app.data.model.User

object ProfilePrivacy {

    fun isPublicProfile(user: User): Boolean = user.isProfilePublic

    fun resolveRelation(
        viewerId: String?,
        target: User,
        connections: List<FriendConnection>
    ): ProfileRelation {
        if (viewerId == null) return ProfileRelation.LOCKED
        if (viewerId == target.id) return ProfileRelation.SELF
        if (isPublicProfile(target)) return ProfileRelation.PUBLIC_PROFILE

        val link = connections.firstOrNull { conn ->
            involves(conn, viewerId, target.id) && conn.status != FriendConnectionStatus.REJECTED
        } ?: return ProfileRelation.LOCKED

        return when (link.status) {
            FriendConnectionStatus.ACCEPTED -> ProfileRelation.FRIENDS
            FriendConnectionStatus.PENDING -> {
                if (link.requesterId == viewerId) ProfileRelation.PENDING_OUTGOING
                else ProfileRelation.PENDING_INCOMING
            }
            FriendConnectionStatus.REJECTED -> ProfileRelation.LOCKED
        }
    }

    fun canViewFullProfile(relation: ProfileRelation): Boolean =
        relation == ProfileRelation.SELF ||
            relation == ProfileRelation.PUBLIC_PROFILE ||
            relation == ProfileRelation.FRIENDS

    fun canInteract(relation: ProfileRelation): Boolean = canViewFullProfile(relation)

    fun friendIdsFor(userId: String, connections: List<FriendConnection>): Set<String> =
        connections
            .filter { it.status == FriendConnectionStatus.ACCEPTED && involves(it, userId) }
            .map { otherParty(it, userId) }
            .toSet()

    fun filterVisiblePosts(
        posts: List<FeedPost>,
        usersById: Map<String, User>,
        viewerId: String?,
        friendIds: Set<String>
    ): List<FeedPost> {
        if (viewerId == null) return emptyList()
        return posts.filter { post -> canViewPost(post, usersById, viewerId, friendIds) }
    }

    fun filterVisiblePets(
        pets: List<Pet>,
        usersById: Map<String, User>,
        viewerId: String?,
        friendIds: Set<String>
    ): List<Pet> {
        if (viewerId == null) return emptyList()
        return pets.filter { pet ->
            val owner = usersById[pet.ownerId] ?: return@filter false
            canViewUserContent(owner, viewerId, friendIds)
        }
    }

    fun filterDiscoverableUsers(
        users: List<User>,
        viewerId: String?
    ): List<User> {
        if (viewerId == null) return emptyList()
        return users.filter { it.id != viewerId }
    }

    private fun canViewPost(
        post: FeedPost,
        usersById: Map<String, User>,
        viewerId: String,
        friendIds: Set<String>
    ): Boolean {
        if (post.authorId == viewerId) return true
        val author = usersById[post.authorId] ?: return false
        return canViewUserContent(author, viewerId, friendIds)
    }

    private fun canViewUserContent(
        owner: User,
        viewerId: String,
        friendIds: Set<String>
    ): Boolean {
        if (owner.id == viewerId) return true
        if (isPublicProfile(owner)) return true
        return owner.id in friendIds
    }

    private fun involves(connection: FriendConnection, userId: String, otherId: String): Boolean =
        (connection.requesterId == userId && connection.addresseeId == otherId) ||
            (connection.requesterId == otherId && connection.addresseeId == userId)

    private fun involves(connection: FriendConnection, userId: String): Boolean =
        connection.requesterId == userId || connection.addresseeId == userId

    private fun otherParty(connection: FriendConnection, userId: String): String =
        if (connection.requesterId == userId) connection.addresseeId else connection.requesterId
}
