package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.mock.InMemoryDataStore
import kotlinx.coroutines.flow.StateFlow

interface FeedRepository {
    fun observeFeedPosts(): StateFlow<List<FeedPost>>
    fun addFeedPost(post: FeedPost)
}

class MockFeedRepository : FeedRepository {
    override fun observeFeedPosts(): StateFlow<List<FeedPost>> = InMemoryDataStore.feedPosts

    override fun addFeedPost(post: FeedPost) {
        InMemoryDataStore.addFeedPost(post)
    }
}
