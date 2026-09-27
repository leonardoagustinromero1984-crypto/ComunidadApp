package com.comunidapp.app.domain.social

import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.model.PostType
import org.junit.Assert.assertEquals
import org.junit.Test

class PublishPrivacyCreatePathTest {

    @Test
    fun privadoUiBuildsFollowersRpcValue() {
        val post = FeedPost(
            id = "",
            authorId = "u1",
            authorName = "Ana",
            type = PostType.GENERAL,
            title = "Hola",
            content = "Mundo",
            visibility = CanonicalSocialPostVisibility.FOLLOWERS
        )
        assertEquals("FOLLOWERS", post.visibility.toRpcValue())
    }

    @Test
    fun publicoUiBuildsPublicRpcValue() {
        assertEquals("PUBLIC", CanonicalSocialPostVisibility.PUBLIC.toRpcValue())
    }
}
