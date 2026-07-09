package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.model.PostComment
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.FeedRepository
import com.comunidapp.app.data.repository.FriendRepository
import com.comunidapp.app.data.repository.UserRepository
import com.comunidapp.app.domain.ProfilePrivacy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val feedRepository: FeedRepository = DataProvider.feedRepository,
    private val userRepository: UserRepository = DataProvider.userRepository,
    private val friendRepository: FriendRepository = DataProvider.friendRepository,
    private val authRepository: AuthRepository = AuthProvider.repository
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _visibleCount = MutableStateFlow(20)
    private val _commentsPostId = MutableStateFlow<String?>(null)
    val commentsPostId: StateFlow<String?> = _commentsPostId.asStateFlow()

    private val visibleFeedPosts = combine(
        feedRepository.observeFeedPosts(),
        userRepository.observeUsers(),
        authRepository.observeAuthState().flatMapLatest { user ->
            if (user == null) flowOf(emptyList())
            else friendRepository.observeConnections(user.id)
        },
        authRepository.observeAuthState()
    ) { posts, users, connections, currentUser ->
        val usersById = users.associateBy { it.id }
        val friendIds = currentUser?.let {
            ProfilePrivacy.friendIdsFor(it.id, connections)
        }.orEmpty()
        ProfilePrivacy.filterVisiblePosts(posts, usersById, currentUser?.id, friendIds)
    }

    val posts: StateFlow<List<FeedPost>> = combine(
        visibleFeedPosts,
        _visibleCount
    ) { all, count -> all.take(count) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val likedPostIds: StateFlow<Set<String>> = authRepository.observeAuthState()
        .flatMapLatest { user ->
            if (user == null) flowOf(emptySet())
            else feedRepository.observeLikedPostIds(user.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val comments: StateFlow<List<PostComment>> = _commentsPostId
        .flatMapLatest { postId ->
            if (postId == null) flowOf(emptyList())
            else feedRepository.observeComments(postId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val nearbyUsers: StateFlow<List<User>> = combine(
        userRepository.observeUsers(),
        authRepository.observeAuthState()
    ) { users, currentUser ->
        if (currentUser == null) return@combine emptyList()
        val myLocation = users.find { it.id == currentUser.id }?.locationText
            ?: currentUser.locationText
        ProfilePrivacy.filterDiscoverableUsers(users, currentUser.id)
            .filter { user -> matchesLocation(myLocation, user.locationText) }
            .take(12)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hasMore: StateFlow<Boolean> = combine(
        visibleFeedPosts.map { it.size },
        _visibleCount
    ) { total, visible -> visible < total }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            feedRepository.refreshPosts()
            _isRefreshing.value = false
        }
    }

    fun loadMore() {
        _visibleCount.update { it + 20 }
    }

    fun toggleLike(postId: String) {
        val userId = authRepository.getCurrentUser()?.id ?: return
        viewModelScope.launch {
            feedRepository.toggleLike(postId, userId)
        }
    }

    fun openComments(postId: String) {
        _commentsPostId.value = postId
    }

    fun closeComments() {
        _commentsPostId.value = null
    }

    fun sendComment(content: String) {
        val postId = _commentsPostId.value ?: return
        val user = authRepository.getCurrentUser() ?: return
        viewModelScope.launch {
            feedRepository.addComment(postId, user.id, user.name, content)
        }
    }

    private fun matchesLocation(myLocation: String?, otherLocation: String?): Boolean {
        if (myLocation.isNullOrBlank()) return true
        if (otherLocation.isNullOrBlank()) return false
        return otherLocation.contains(myLocation, ignoreCase = true) ||
            myLocation.contains(otherLocation, ignoreCase = true)
    }
}
