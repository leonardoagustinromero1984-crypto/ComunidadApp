package com.comunidapp.app.domain.social

import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.model.FriendConnection
import com.comunidapp.app.data.model.FriendConnectionStatus
import com.comunidapp.app.data.model.PostType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialFeedAuthorPrivacyAbcTest {

    private val authorA = "author-a"
    private val authorC = "author-c"
    private val viewerB = "viewer-b"

    private fun followersPost(id: String, authorId: String) = FeedPost(
        id = id,
        authorId = authorId,
        authorName = authorId,
        type = PostType.GENERAL,
        title = "T",
        content = "C",
        visibility = CanonicalSocialPostVisibility.FOLLOWERS
    )

    private fun pendingBA() = FriendConnection(
        id = "req-ba",
        requesterId = viewerB,
        addresseeId = authorA,
        status = FriendConnectionStatus.PENDING
    )

    private fun acceptedBA() = pendingBA().copy(status = FriendConnectionStatus.ACCEPTED)

    @Test
    fun bWithoutRelationSeesNeitherANorC() {
        val posts = listOf(followersPost("a", authorA), followersPost("c", authorC))
        val visible = SocialFeedVisibilityRules.filterVisiblePosts(posts, viewerB, emptyList())
        assertTrue(visible.isEmpty())
    }

    @Test
    fun bPendingToAStillSeesNeitherANorC() {
        val posts = listOf(followersPost("a", authorA), followersPost("c", authorC))
        val visible = SocialFeedVisibilityRules.filterVisiblePosts(posts, viewerB, listOf(pendingBA()))
        assertTrue(visible.isEmpty())
        assertEquals(
            AuthorFriendship.PENDING,
            AuthorFriendshipResolver.between(viewerB, authorA, listOf(pendingBA()))
        )
        assertEquals(
            AuthorFriendship.NONE,
            AuthorFriendshipResolver.between(viewerB, authorC, listOf(pendingBA()))
        )
    }

    @Test
    fun afterAAcceptsBSeesAButNotC() {
        val posts = listOf(followersPost("a", authorA), followersPost("c", authorC))
        val visible = SocialFeedVisibilityRules.filterVisiblePosts(posts, viewerB, listOf(acceptedBA()))
        assertEquals(listOf("a"), visible.map { it.id })
        assertFalse(visible.any { it.authorId == authorC })
    }
}
