package com.comunidapp.app.domain.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StoryRingPolicyTest {

    @Test
    fun noActiveStoryHasNoRing() {
        assertEquals(StoryRingPolicy.Style.NONE, StoryRingPolicy.style(hasActiveStory = false))
        assertFalse(StoryRingPolicy.ownHasActive(0))
    }

    @Test
    fun activeUnseenStoryUsesAccentRing() {
        assertEquals(StoryRingPolicy.Style.ACCENT, StoryRingPolicy.style(hasActiveStory = true))
        assertTrue(StoryRingPolicy.ownHasActive(1))
    }

    @Test
    fun seenStyleExistsButIsNotInventedByFeedAvatars() {
        assertEquals(
            StoryRingPolicy.Style.SEEN,
            StoryRingPolicy.style(hasActiveStory = true, seen = true)
        )
        val home = File("src/main/java/com/comunidapp/app/ui/screens/home/HomeScreen.kt").readText()
        val bubble = File("src/main/java/com/comunidapp/app/ui/screens/home/SocialHomeComponents.kt").readText()
        assertTrue(home.contains("stories.filter { it.isActiveStory() }"))
        assertTrue(home.contains("ownStories.isNotEmpty()"))
        assertTrue(bubble.contains("StoryRingPolicy.style("))
        assertTrue(bubble.contains("hasActiveStory = !isAdd && hasNew"))
        assertFalse(bubble.contains("BrandOrange, BrandOrange"))
    }
}
