package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.repository.FeedRepository
import com.comunidapp.app.data.repository.MockFeedRepository
import com.comunidapp.app.data.model.FeedPost
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(
    feedRepository: FeedRepository = MockFeedRepository()
) : ViewModel() {

    val posts: StateFlow<List<FeedPost>> = feedRepository.observeFeedPosts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
