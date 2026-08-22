package com.comunidapp.app.domain.social

import com.comunidapp.app.data.model.FeedPost

data class StoryAuthorTrayItem(
    val authorId: String,
    val authorName: String,
    val imageUrl: String?,
    val segmentsOldestFirst: List<FeedPost>
)

object StoryTrayGrouping {
    fun groupByAuthor(stories: List<FeedPost>): List<StoryAuthorTrayItem> {
        return stories
            .filter { it.authorId.isNotBlank() }
            .groupBy { it.authorId }
            .map { (authorId, segments) ->
                val ordered = segmentsOldestFirst(segments)
                val latest = ordered.lastOrNull()
                StoryAuthorTrayItem(
                    authorId = authorId,
                    authorName = latest?.authorName.orEmpty(),
                    imageUrl = latest?.imageUrl ?: latest?.authorImageUrl,
                    segmentsOldestFirst = ordered
                )
            }
            .sortedByDescending { it.segmentsOldestFirst.lastOrNull()?.createdAt ?: 0L }
    }

    fun segmentsOldestFirst(stories: List<FeedPost>): List<FeedPost> =
        stories.sortedWith(compareBy<FeedPost> { it.createdAt ?: Long.MAX_VALUE }.thenBy { it.id })
}
