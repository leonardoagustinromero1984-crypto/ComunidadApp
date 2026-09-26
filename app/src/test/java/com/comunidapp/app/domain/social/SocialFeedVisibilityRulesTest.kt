package com.comunidapp.app.domain.social

import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.model.PostType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialFeedVisibilityRulesTest {

    private val authorA = "author-a"
    private val viewerB = "viewer-b"
    private val viewerC = "viewer-c"

    private fun post(visibility: CanonicalSocialPostVisibility) = FeedPost(
        id = "p1",
        authorId = authorA,
        authorName = "Author",
        type = PostType.GENERAL,
        title = "T",
        content = "C",
        visibility = visibility
    )

    @Test
    fun publicPostVisibleToStranger() {
        assertTrue(
            SocialFeedVisibilityRules.isVisibleToViewer(
                postVisibility = CanonicalSocialPostVisibility.PUBLIC,
                authorId = authorA,
                viewerId = viewerB,
                acceptedConnectionAuthorIds = emptySet()
            )
        )
    }

    @Test
    fun privateFollowersPostHiddenFromStranger() {
        assertFalse(
            SocialFeedVisibilityRules.isVisibleToViewer(
                postVisibility = CanonicalSocialPostVisibility.FOLLOWERS,
                authorId = authorA,
                viewerId = viewerB,
                acceptedConnectionAuthorIds = emptySet()
            )
        )
    }

    @Test
    fun privateFollowersPostVisibleAfterAcceptedConnection() {
        assertTrue(
            SocialFeedVisibilityRules.isVisibleToViewer(
                postVisibility = CanonicalSocialPostVisibility.FOLLOWERS,
                authorId = authorA,
                viewerId = viewerB,
                acceptedConnectionAuthorIds = setOf(authorA)
            )
        )
    }

    @Test
    fun privadoUiMapsToFollowersRpc() {
        assertEquals("FOLLOWERS", CanonicalSocialPostVisibility.FOLLOWERS.toRpcValue())
        assertEquals("Privado", CanonicalSocialPostVisibility.label(CanonicalSocialPostVisibility.FOLLOWERS))
        assertEquals(2, CanonicalSocialPostVisibility.uiPublishOptions().size)
    }

    @Test
    fun wallFiltersNonConnectionToPublicOnly() {
        val posts = listOf(
            post(CanonicalSocialPostVisibility.PUBLIC),
            post(CanonicalSocialPostVisibility.FOLLOWERS).copy(id = "p2")
        )
        val visible = SocialFeedVisibilityRules.filterAuthorWallPosts(
            posts = posts,
            authorId = authorA,
            viewerId = viewerC,
            acceptedConnectionAuthorIds = emptySet()
        )
        assertEquals(1, visible.size)
        assertEquals(CanonicalSocialPostVisibility.PUBLIC, visible.first().visibility)
    }
}
