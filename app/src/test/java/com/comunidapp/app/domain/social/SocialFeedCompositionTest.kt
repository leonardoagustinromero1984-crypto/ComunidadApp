package com.comunidapp.app.domain.social

import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.model.PostType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialFeedCompositionTest {

    private fun post(
        id: String,
        type: PostType,
        mime: String? = null,
        url: String? = null
    ) = FeedPost(
        id = id,
        authorId = "a",
        authorName = "Ana",
        type = type,
        title = type.name,
        content = "hola",
        imageUrl = url,
        mediaMime = mime
    )

    @Test
    fun publicationsExcludeReelsAndStories() {
        val posts = listOf(
            post("p1", PostType.GENERAL, mime = "video/mp4", url = "https://cdn/video.mp4"),
            post("r1", PostType.REEL, mime = "video/mp4", url = "https://cdn/reel.mp4"),
            post("s1", PostType.STORY, mime = "image/jpeg", url = "https://cdn/story.jpg"),
            post("p2", PostType.GENERAL, mime = "image/jpeg", url = "https://cdn/photo.jpg")
        )
        val publications = SocialFeedComposition.publications(posts)
        val clips = SocialFeedComposition.clips(posts)
        assertEquals(listOf("p1", "p2"), publications.map { it.id })
        assertEquals(listOf("r1"), clips.map { it.id })
        assertFalse(publications.any { it.type == PostType.REEL })
    }

    @Test
    fun postVideoIsNotAClip() {
        val videoPost = post(
            "pv",
            PostType.GENERAL,
            mime = "video/mp4",
            url = "https://cdn/post-video.mp4"
        )
        assertTrue(SocialFeedComposition.isPublicationVideo(videoPost))
        assertTrue(SocialFeedComposition.publications(listOf(videoPost)).isNotEmpty())
        assertTrue(SocialFeedComposition.clips(listOf(videoPost)).isEmpty())
        assertFalse(SocialFeedComposition.isPublicationVideo(post("r", PostType.REEL)))
    }
}
