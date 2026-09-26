package com.comunidapp.app.domain.social

import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.model.FriendConnection

/**
 * Client-side mirror of `_canon_social_post_visible` (1079+).
 *
 * - Self: always
 * - ACCEPTED Manada: always (any post visibility)
 * - Strangers: only PUBLIC posts from PUBLIC_LIMITED profiles
 *
 * PENDING / transfer / pet relationships never unlock social content.
 */
object SocialFeedVisibilityRules {

    fun isVisibleToViewer(
        postVisibility: CanonicalSocialPostVisibility,
        authorId: String,
        viewerId: String,
        friendshipWithAuthor: AuthorFriendship,
        authorProfilePublic: Boolean = true
    ): Boolean {
        if (authorId == viewerId) return true
        if (friendshipWithAuthor == AuthorFriendship.ACCEPTED) return true
        return postVisibility == CanonicalSocialPostVisibility.PUBLIC && authorProfilePublic
    }

    fun isVisibleToViewer(
        postVisibility: CanonicalSocialPostVisibility,
        authorId: String,
        viewerId: String,
        acceptedConnectionAuthorIds: Set<String>,
        authorProfilePublic: Boolean = true
    ): Boolean = isVisibleToViewer(
        postVisibility = postVisibility,
        authorId = authorId,
        viewerId = viewerId,
        friendshipWithAuthor = if (authorId in acceptedConnectionAuthorIds) {
            AuthorFriendship.ACCEPTED
        } else {
            AuthorFriendship.NONE
        },
        authorProfilePublic = authorProfilePublic
    )

    fun isVisibleToViewer(
        post: FeedPost,
        viewerId: String,
        connections: List<FriendConnection>,
        authorProfilePublic: Boolean = true
    ): Boolean = isVisibleToViewer(
        postVisibility = post.visibility,
        authorId = post.authorId,
        viewerId = viewerId,
        friendshipWithAuthor = AuthorFriendshipResolver.between(viewerId, post.authorId, connections),
        authorProfilePublic = authorProfilePublic
    )

    fun filterVisiblePosts(
        posts: List<FeedPost>,
        viewerId: String?,
        connections: List<FriendConnection>,
        authorProfilePublicById: Map<String, Boolean> = emptyMap()
    ): List<FeedPost> {
        if (viewerId == null) return emptyList()
        return posts.filter { post ->
            isVisibleToViewer(
                post = post,
                viewerId = viewerId,
                connections = connections,
                authorProfilePublic = authorProfilePublicById[post.authorId] ?: true
            )
        }
    }

    fun filterAuthorWallPosts(
        posts: List<FeedPost>,
        authorId: String,
        viewerId: String?,
        connections: List<FriendConnection>,
        authorProfilePublic: Boolean = true
    ): List<FeedPost> {
        if (viewerId == null) return emptyList()
        return posts.filter { post ->
            post.authorId == authorId &&
                isVisibleToViewer(
                    post = post,
                    viewerId = viewerId,
                    connections = connections,
                    authorProfilePublic = authorProfilePublic
                )
        }
    }

    fun filterAuthorWallPosts(
        posts: List<FeedPost>,
        authorId: String,
        viewerId: String?,
        acceptedConnectionAuthorIds: Set<String>,
        authorProfilePublic: Boolean = true
    ): List<FeedPost> {
        if (viewerId == null) return emptyList()
        return posts.filter { post ->
            post.authorId == authorId &&
                isVisibleToViewer(
                    postVisibility = post.visibility,
                    authorId = post.authorId,
                    viewerId = viewerId,
                    acceptedConnectionAuthorIds = acceptedConnectionAuthorIds,
                    authorProfilePublic = authorProfilePublic
                )
        }
    }
}
