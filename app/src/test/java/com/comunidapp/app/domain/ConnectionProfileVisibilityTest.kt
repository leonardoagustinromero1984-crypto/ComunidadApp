package com.comunidapp.app.domain

import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.model.FriendConnection
import com.comunidapp.app.data.model.FriendConnectionStatus
import com.comunidapp.app.data.model.PostType
import com.comunidapp.app.data.model.ProfileRelation
import com.comunidapp.app.data.model.User
import com.comunidapp.app.domain.social.CanonicalSocialPostVisibility
import com.comunidapp.app.domain.social.SocialFeedVisibilityRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionProfileVisibilityTest {

    @Test
    fun acceptedConnectionIsFriendsEvenIfProfileIsPublic() {
        val target = User(id = "a", name = "Ana", email = "a@x", profilePrivate = false)
        val connections = listOf(
            FriendConnection("1", "b", "a", FriendConnectionStatus.ACCEPTED)
        )
        assertEquals(
            ProfileRelation.FRIENDS,
            ProfilePrivacy.resolveRelation("b", target, connections)
        )
    }

    @Test
    fun wallShowsPublicAndFollowersOnlyWhenAcceptedWithThatAuthor() {
        val posts = listOf(
            FeedPost("p1", "a", "Ana", type = PostType.GENERAL, title = "T", content = "C",
                visibility = CanonicalSocialPostVisibility.PUBLIC),
            FeedPost("p2", "a", "Ana", type = PostType.GENERAL, title = "T", content = "C",
                visibility = CanonicalSocialPostVisibility.FOLLOWERS),
            FeedPost("p3", "c", "Carla", type = PostType.GENERAL, title = "T", content = "C",
                visibility = CanonicalSocialPostVisibility.FOLLOWERS)
        )
        val accepted = listOf(FriendConnection("1", "b", "a", FriendConnectionStatus.ACCEPTED))
        val wall = SocialFeedVisibilityRules.filterAuthorWallPosts(
            posts = posts,
            authorId = "a",
            viewerId = "b",
            connections = accepted
        )
        assertEquals(listOf("p1", "p2"), wall.map { it.id })
        assertTrue(wall.none { it.authorId == "c" })
    }
}
