package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.model.PostComment
import com.comunidapp.app.data.mock.InMemoryDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface FeedRepository {
    fun observeFeedPosts(): StateFlow<List<FeedPost>>
    suspend fun refreshPosts(): Result<Unit>
    suspend fun addFeedPost(post: FeedPost): Result<String>
    suspend fun updateFeedPost(post: FeedPost): Result<Unit>
    suspend fun toggleLike(postId: String, userId: String): Result<Boolean>
    fun observeLikedPostIds(userId: String): Flow<Set<String>>
    fun observeComments(postId: String): Flow<List<PostComment>>
    suspend fun addComment(
        postId: String,
        authorId: String,
        authorName: String,
        content: String
    ): Result<Unit>
    suspend fun searchPosts(query: String): List<FeedPost>
}

class MockFeedRepository : FeedRepository {
    override fun observeFeedPosts(): StateFlow<List<FeedPost>> = InMemoryDataStore.feedPosts

    override suspend fun refreshPosts(): Result<Unit> {
        InMemoryDataStore.touchFeed()
        return Result.success(Unit)
    }

    override suspend fun addFeedPost(post: FeedPost): Result<String> {
        val id = post.id.ifBlank { "feed_${System.currentTimeMillis()}" }
        InMemoryDataStore.addFeedPost(post.copy(id = id))
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

    override suspend fun addComment(
        postId: String,
        authorId: String,
        authorName: String,
        content: String
    ): Result<Unit> = InMemoryDataStore.addComment(postId, authorId, authorName, content)

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
