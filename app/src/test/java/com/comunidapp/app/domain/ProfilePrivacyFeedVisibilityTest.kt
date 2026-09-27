package com.comunidapp.app.domain

import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.model.PostType
import com.comunidapp.app.data.model.User
import com.comunidapp.app.domain.social.CanonicalSocialPostVisibility
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfilePrivacyFeedVisibilityTest {

    private val authorId = "author-1"
    private val viewerId = "viewer-2"

    private fun post(visibility: CanonicalSocialPostVisibility) = FeedPost(
        id = "p1",
        authorId = authorId,
        authorName = "Author",
        type = PostType.GENERAL,
        title = "T",
        content = "C",
        visibility = visibility
    )

    @Test
    fun followersPostHiddenFromStrangerEvenWithoutAuthorInUserMap() {
        val visible = ProfilePrivacy.filterVisiblePosts(
            posts = listOf(post(CanonicalSocialPostVisibility.FOLLOWERS)),
            usersById = emptyMap(),
            viewerId = viewerId,
            friendIds = emptySet()
        )
        assertEquals(0, visible.size)
    }

    @Test
    fun followersPostVisibleToAcceptedConnection() {
        val visible = ProfilePrivacy.filterVisiblePosts(
            posts = listOf(post(CanonicalSocialPostVisibility.FOLLOWERS)),
            usersById = emptyMap(),
            viewerId = viewerId,
            friendIds = setOf(authorId)
        )
        assertEquals(1, visible.size)
    }
}
