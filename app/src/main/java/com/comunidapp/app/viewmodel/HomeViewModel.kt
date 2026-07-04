package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.FeedRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(
    feedRepository: FeedRepository = DataProvider.feedRepository
) : ViewModel() {

    val posts: StateFlow<List<FeedPost>> = feedRepository.observeFeedPosts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
