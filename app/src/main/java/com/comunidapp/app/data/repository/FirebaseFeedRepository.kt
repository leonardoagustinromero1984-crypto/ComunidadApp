package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.remote.firestore.PostFirestoreDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FirebaseFeedRepository(
    private val dataSource: PostFirestoreDataSource = PostFirestoreDataSource()
) : FeedRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _feedPosts = MutableStateFlow<List<FeedPost>>(emptyList())
    override fun observeFeedPosts(): StateFlow<List<FeedPost>> = _feedPosts.asStateFlow()

    init {
        scope.launch {
            dataSource.observePosts().collect { posts ->
                _feedPosts.value = posts
            }
        }
    }

    override suspend fun addFeedPost(post: FeedPost): Result<String> = dataSource.addPost(post)

    override suspend fun updateFeedPost(post: FeedPost): Result<Unit> = dataSource.updatePost(post)
}
