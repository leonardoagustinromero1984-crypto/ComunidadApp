package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.LostFoundPost
import com.comunidapp.app.data.mock.InMemoryDataStore
import kotlinx.coroutines.flow.StateFlow

interface LostFoundRepository {
    fun observeLostFoundPosts(): StateFlow<List<LostFoundPost>>
    fun addLostFoundPost(post: LostFoundPost)
}

class MockLostFoundRepository : LostFoundRepository {
    override fun observeLostFoundPosts(): StateFlow<List<LostFoundPost>> =
        InMemoryDataStore.lostFoundPosts

    override fun addLostFoundPost(post: LostFoundPost) {
        InMemoryDataStore.addLostFoundPost(post)
    }
}
