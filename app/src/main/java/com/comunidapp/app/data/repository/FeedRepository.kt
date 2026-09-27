package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.model.PostComment
import com.comunidapp.app.data.mock.InMemoryDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface FeedRepository {
    fun observeFeedPosts(): StateFlow<List<FeedPost>>
    fun observeActiveStories(): StateFlow<List<FeedPost>>
    suspend fun refreshPosts(): Result<Unit>
    suspend fun refreshStories(): Result<Unit>
    fun hasMorePosts(): Boolean = false
    suspend fun loadMorePosts(): Result<Unit> = Result.success(Unit)
    suspend fun loadSavedPosts(): Result<List<FeedPost>> = Result.success(emptyList())
    suspend fun ensureVisiblePost(postId: String): Result<FeedPost?> = Result.success(null)
    suspend fun addFeedPost(post: FeedPost): Result<String>
    suspend fun addStory(post: FeedPost, mediaAssetId: String): Result<String>
    suspend fun addReel(post: FeedPost, mediaAssetId: String): Result<String>
    suspend fun findOwnReelIdByMediaAsset(assetId: String): Result<String?> = Result.success(null)
    suspend fun updateFeedPost(post: FeedPost): Result<Unit>
    suspend fun toggleLike(postId: String, userId: String): Result<Boolean>
    fun observeLikedPostIds(userId: String): Flow<Set<String>>
    fun observeComments(postId: String): Flow<List<PostComment>>
    suspend fun refreshComments(postId: String): Result<Unit>
    suspend fun addComment(
        postId: String,
        authorId: String,
        authorName: String,
        content: String
    ): Result<Unit>
    suspend fun deleteOwnComment(commentId: String): Result<Unit>
    suspend fun searchPosts(query: String): List<FeedPost>

    /** Drop in-memory feed so user A cannot leak into user B. */
    fun clearAccountCache() {}
}

class MockFeedRepository : FeedRepository {
    override fun observeFeedPosts(): StateFlow<List<FeedPost>> = InMemoryDataStore.feedPosts
    override fun observeActiveStories(): StateFlow<List<FeedPost>> = InMemoryDataStore.activeStories

    override suspend fun refreshPosts(): Result<Unit> {
        InMemoryDataStore.touchFeed()
        return Result.success(Unit)
    }

    override suspend fun refreshStories(): Result<Unit> {
        InMemoryDataStore.touchStories()
        return Result.success(Unit)
    }

    override suspend fun addFeedPost(post: FeedPost): Result<String> {
        val id = post.id.ifBlank { "feed_${System.currentTimeMillis()}" }
        InMemoryDataStore.addFeedPost(post.copy(id = id))
        return Result.success(id)
    }

    override suspend fun addStory(post: FeedPost, mediaAssetId: String): Result<String> {
        val id = post.id.ifBlank { "story_${System.currentTimeMillis()}" }
        InMemoryDataStore.addFeedPost(
            post.copy(
                id = id,
                type = com.comunidapp.app.data.model.PostType.STORY,
                mediaAssetId = mediaAssetId,
                imageUrl = post.imageUrl ?: mediaAssetId,
                mediaAvailability = com.comunidapp.app.data.model.FeedMediaAvailability.AVAILABLE
            )
        )
        return Result.success(id)
    }

    override suspend fun addReel(post: FeedPost, mediaAssetId: String): Result<String> {
        val id = post.id.ifBlank { "reel_${System.currentTimeMillis()}" }
        InMemoryDataStore.addFeedPost(
            post.copy(
                id = id,
                type = com.comunidapp.app.data.model.PostType.REEL,
                mediaAssetId = mediaAssetId,
                imageUrl = post.imageUrl ?: mediaAssetId,
                mediaAvailability = com.comunidapp.app.data.model.FeedMediaAvailability.AVAILABLE
            )
        )
        return Result.success(id)
    }

    override suspend fun updateFeedPost(post: FeedPost): Result<Unit> {
        InMemoryDataStore.updateFeedPost(post)
        return Result.success(Unit)
    }

    override suspend fun toggleLike(postId: String, userId: String): Result<Boolean> =
        InMemoryDataStore.toggleLike(postId, userId)

    override fun observeLikedPostIds(userId: String): Flow<Set<String>> =
        InMemoryDataStore.observeLikedPosts(userId)

    override fun observeComments(postId: String): Flow<List<PostComment>> =
        InMemoryDataStore.observeComments(postId)

    override suspend fun refreshComments(postId: String): Result<Unit> = Result.success(Unit)

    override suspend fun addComment(
        postId: String,
        authorId: String,
        authorName: String,
        content: String
    ): Result<Unit> = InMemoryDataStore.addComment(postId, authorId, authorName, content)

    override suspend fun deleteOwnComment(commentId: String): Result<Unit> =
        InMemoryDataStore.deleteOwnComment(commentId)

    override suspend fun searchPosts(query: String): List<FeedPost> {
        if (query.isBlank()) return emptyList()
        return InMemoryDataStore.feedPosts.value.filter { post ->
            post.title.contains(query, ignoreCase = true) ||
                post.content.contains(query, ignoreCase = true) ||
                post.authorName.contains(query, ignoreCase = true) ||
                post.locationText?.contains(query, ignoreCase = true) == true
        }
    }
}
