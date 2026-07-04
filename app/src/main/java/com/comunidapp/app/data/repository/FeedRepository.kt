package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.mock.InMemoryDataStore
import kotlinx.coroutines.flow.StateFlow

interface FeedRepository {
    fun observeFeedPosts(): StateFlow<List<FeedPost>>
    suspend fun addFeedPost(post: FeedPost): Result<Unit>
}

class MockFeedRepository : FeedRepository {
    override fun observeFeedPosts(): StateFlow<List<FeedPost>> = InMemoryDataStore.feedPosts

    override suspend fun addFeedPost(post: FeedPost): Result<Unit> {
        InMemoryDataStore.addFeedPost(post)
        return Result.success(Unit)
    }
}
