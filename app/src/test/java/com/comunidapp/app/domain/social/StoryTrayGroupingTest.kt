package com.comunidapp.app.domain.social

import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.model.PostType
import org.junit.Assert.assertEquals
import org.junit.Test

class StoryTrayGroupingTest {

    @Test
    fun sameAuthor_twoStories_oneTrayItem_oldestFirst() {
        val older = story("s1", "a1", "Ana", 100L)
        val newer = story("s2", "a1", "Ana", 200L)
        val other = story("s3", "b2", "Beto", 150L)
        val tray = StoryTrayGrouping.groupByAuthor(listOf(newer, older, other))
        assertEquals(2, tray.size)
        val ana = tray.first { it.authorId == "a1" }
        assertEquals(listOf("s1", "s2"), ana.segmentsOldestFirst.map { it.id })
        assertEquals("s2", ana.imageUrl)
    }

    @Test
    fun segmentsKeepOrderAcrossSort() {
        val a = story("s1", "a1", "Ana", 300L)
        val b = story("s2", "a1", "Ana", 100L)
        val ordered = StoryTrayGrouping.segmentsOldestFirst(listOf(a, b))
        assertEquals(listOf("s2", "s1"), ordered.map { it.id })
        assertEquals(listOf("s2", "s1"), StoryTrayGrouping.segmentsOldestFirst(ordered).map { it.id })
    }

    private fun story(id: String, authorId: String, name: String, createdAt: Long) = FeedPost(
        id = id,
        authorId = authorId,
        authorName = name,
        type = PostType.STORY,
        title = "Historia",
        content = "",
        imageUrl = id,
        createdAt = createdAt
    )
}
