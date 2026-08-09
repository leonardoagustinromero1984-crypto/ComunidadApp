package com.comunidapp.app.domain.social

import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.model.PostType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryExpirationTest {

    @Test
    fun expiresAtFrom_adds24Hours() {
        val created = 1_700_000_000_000L
        assertEquals(created + 24L * 60L * 60L * 1000L, StoryExpiration.expiresAtFrom(created))
    }

    @Test
    fun isActive_falseWhenExpired() {
        val now = 1_800_000_000_000L
        assertFalse(StoryExpiration.isActive(now - 1, now))
        assertTrue(StoryExpiration.isActive(now + 1, now))
        assertTrue(StoryExpiration.isActive(null, now))
    }

    @Test
    fun feedPost_filtersExpiredStories() {
        val now = System.currentTimeMillis()
        val active = FeedPost(
            id = "1",
            authorId = "a",
            authorName = "Ana",
            type = PostType.STORY,
            title = "Historia",
            content = "Hola",
            createdAt = now,
            expiresAt = now + StoryExpiration.DURATION_MS
        )
        val expired = active.copy(id = "2", expiresAt = now - 1)
        assertTrue(active.isActiveStory(now))
        assertFalse(expired.isActiveStory(now))
        val visible = listOf(active, expired).filter { it.isActiveStory(now) }
        assertEquals(1, visible.size)
        assertEquals("1", visible.first().id)
    }

    @Test
    fun feedPost_storyWithoutExpiresAt_usesCreatedAtPlus24h() {
        val now = System.currentTimeMillis()
        val active = FeedPost(
            id = "legacy-active",
            authorId = "a",
            authorName = "Ana",
            type = PostType.STORY,
            title = "Historia",
            content = "Hola",
            createdAt = now - 1_000L,
            expiresAt = null
        )
        val expired = active.copy(
            id = "legacy-expired",
            createdAt = now - StoryExpiration.DURATION_MS - 1_000L
        )
        assertTrue(active.isActiveStory(now))
        assertFalse(expired.isActiveStory(now))
    }

    @Test
    fun postTypes_includeReelAndStory() {
        assertEquals(PostType.REEL, PostType.fromString("REEL"))
        assertEquals(PostType.STORY, PostType.fromString("story"))
    }
}
