package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.FeedRepository
import com.comunidapp.app.data.repository.PlatformRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

data class SavedPostsUiState(
    val posts: List<FeedPost> = emptyList(),
    val likedPostIds: Set<String> = emptySet()
)

@OptIn(ExperimentalCoroutinesApi::class)
class SavedPostsViewModel(
    private val feedRepository: FeedRepository = DataProvider.feedRepository,
    private val platformRepository: PlatformRepository = DataProvider.platformRepository
) : ViewModel() {

    private val _posts = MutableStateFlow<List<FeedPost>>(emptyList())
    private val _uiState = MutableStateFlow(SavedPostsUiState())
    val uiState: StateFlow<SavedPostsUiState> = _uiState.asStateFlow()

    init {
        reload()
        viewModelScope.launch {
            combine(
                _posts,
                AuthProvider.repository.observeAuthState().flatMapLatest { user ->
                    if (user == null) flowOf(emptySet())
                    else feedRepository.observeLikedPostIds(user.id)
                }
            ) { posts, likedIds ->
                SavedPostsUiState(posts = posts, likedPostIds = likedIds)
            }.collect { _uiState.value = it }
        }
    }

    fun reload() {
        viewModelScope.launch {
            val probe = com.comunidapp.app.domain.perf.ScreenPerfProbe.begin("saved")
            probe.network {
                feedRepository.loadSavedPosts()
                    .onSuccess { loaded -> _posts.value = loaded }
            }
            probe.markFirstContent()
            probe.finish(com.comunidapp.app.domain.perf.ScreenPerfProbe.Ledger.snapshot())
        }
    }

    fun toggleLike(postId: String) {
        val userId = AuthProvider.repository.getCurrentUser()?.id ?: return
        viewModelScope.launch { feedRepository.toggleLike(postId, userId) }
    }

    fun toggleSave(postId: String) {
        val userId = AuthProvider.repository.getCurrentUser()?.id ?: return
        viewModelScope.launch {
            platformRepository.toggleSavePost(postId, userId)
                .onSuccess { reload() }
        }
    }
}
