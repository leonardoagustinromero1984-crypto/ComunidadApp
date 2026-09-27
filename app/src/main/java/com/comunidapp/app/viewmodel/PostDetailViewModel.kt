package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.model.PostComment
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.FeedRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PostDetailViewModel(
    private val postId: String,
    private val feedRepository: FeedRepository = DataProvider.feedRepository,
    private val authRepository: AuthRepository = AuthProvider.repository
) : ViewModel() {

    val post: StateFlow<FeedPost?> = feedRepository.observeFeedPosts()
        .map { posts -> posts.firstOrNull { it.id == postId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val comments: StateFlow<List<PostComment>> = feedRepository.observeComments(postId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val currentUserId: StateFlow<String?> = authRepository.observeAuthState()
        .map { it?.id }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            feedRepository.ensureVisiblePost(postId)
            feedRepository.refreshComments(postId)
        }
    }

    fun addComment(text: String) {
        val user = authRepository.getCurrentUser() ?: return
        val body = text.trim()
        if (body.isEmpty()) return
        viewModelScope.launch {
            feedRepository.addComment(postId, user.id, user.name, body)
        }
    }

    companion object {
        fun factory(postId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PostDetailViewModel(postId) as T
                }
            }
    }
}
