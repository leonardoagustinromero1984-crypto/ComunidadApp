package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.model.PostComment
import com.comunidapp.app.data.model.ReportTargetType
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.FeedRepository
import com.comunidapp.app.data.repository.FriendRepository
import com.comunidapp.app.data.repository.PlatformRepository
import com.comunidapp.app.data.repository.UserRepository
import com.comunidapp.app.domain.ProfilePrivacy
import com.comunidapp.app.domain.user.ProfileAvatarResolver
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val feedRepository: FeedRepository = DataProvider.feedRepository,
    private val userRepository: UserRepository = DataProvider.userRepository,
    private val friendRepository: FriendRepository = DataProvider.friendRepository,
    private val platformRepository: PlatformRepository = DataProvider.platformRepository,
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val petRepository: com.comunidapp.app.data.repository.PetRepository = DataProvider.petRepository
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _visibleCount = MutableStateFlow(20)
    private val _serverHasMore = MutableStateFlow(false)
    private var loadingMore = false
    private val _commentsPostId = MutableStateFlow<String?>(null)
    val commentsPostId: StateFlow<String?> = _commentsPostId.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val blockedUserIds: StateFlow<Set<String>> = authRepository.observeAuthState()
        .flatMapLatest { user ->
            if (user == null) flowOf(emptySet())
            else platformRepository.observeBlockedUserIds(user.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val visibleFeedPosts = combine(
        feedRepository.observeFeedPosts(),
        userRepository.observeUsers(),
        authRepository.observeAuthState().flatMapLatest { user ->
            if (user == null) flowOf(emptyList())
            else friendRepository.observeConnections(user.id)
        },
        authRepository.observeAuthState(),
        blockedUserIds
    ) { posts, users, connections, currentUser, blocked ->
        val authorProfilePublicById = users.associate { it.id to ProfilePrivacy.isPublicProfile(it) }
        ProfilePrivacy.filterVisiblePosts(
            posts,
            currentUser?.id,
            connections,
            authorProfilePublicById
        ).filter { it.authorId !in blocked }
    }

    val posts: StateFlow<List<FeedPost>> = combine(
        visibleFeedPosts,
        _visibleCount
    ) { all, count -> all.take(count) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stories: StateFlow<List<FeedPost>> = feedRepository.observeActiveStories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val likedPostIds: StateFlow<Set<String>> = authRepository.observeAuthState()
        .flatMapLatest { user ->
            if (user == null) flowOf(emptySet())
            else feedRepository.observeLikedPostIds(user.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val savedPostIds: StateFlow<Set<String>> = authRepository.observeAuthState()
        .flatMapLatest { user ->
            if (user == null) flowOf(emptySet())
            else platformRepository.observeSavedPostIds(user.id)
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
        authRepository.observeAuthState(),
        blockedUserIds
    ) { users, currentUser, blocked ->
        if (currentUser == null) return@combine emptyList()
        val myLocation = users.find { it.id == currentUser.id }?.locationText
            ?: currentUser.locationText
        ProfilePrivacy.filterDiscoverableUsers(users, currentUser.id)
            .filter { user -> user.id !in blocked }
            .filter { user -> matchesLocation(myLocation, user.locationText) }
            .take(12)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Usuario actual — perfil persistido, no solo el JWT de auth. */
    val currentUser: StateFlow<User?> = authRepository.observeAuthState()
        .flatMapLatest { auth ->
            if (auth == null) flowOf(null)
            else userRepository.observeUser(auth.id).map { it ?: auth }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Nombre para saludo de Home (solo presentación). */
    val greetingName: StateFlow<String?> = currentUser
        .map { user -> user?.resolvedDisplayName?.takeIf { it.isNotBlank() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** URL cargable (http o storage firmado). Misma fuente que Perfil. */
    val avatarDisplayUrl: StateFlow<String?> = currentUser
        .distinctUntilChanged { a, b ->
            a?.id == b?.id &&
                a?.profileImageUrl == b?.profileImageUrl &&
                a?.avatarPath == b?.avatarPath
        }
        .flatMapLatest { user -> flow { emit(ProfileAvatarResolver.displayUrl(user)) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Mascotas activas del usuario — solo para carrusel de Inicio (fuente real). */
    val myPets: StateFlow<List<com.comunidapp.app.data.model.Pet>> = combine(
        petRepository.observePets(),
        authRepository.observeAuthState(),
        com.comunidapp.app.domain.context.OperationalContextProvider.active
    ) { pets, user, context ->
        val uid = user?.id ?: return@combine emptyList()
        com.comunidapp.app.domain.pets.PetManagementContext.filter(pets, context, uid)
            .filter { it.status.equals("ACTIVE", ignoreCase = true) }
            .sortedBy { it.name.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hasMore: StateFlow<Boolean> = combine(
        visibleFeedPosts.map { it.size },
        _visibleCount,
        _serverHasMore
    ) { total, visible, server -> visible < total || server }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            val probe = com.comunidapp.app.domain.perf.ScreenPerfProbe.begin("home")
            try {
                probe.network {
                    coroutineScope {
                        val postsJob = async { feedRepository.refreshPosts() }
                        val storiesJob = async { feedRepository.refreshStories() }
                        postsJob.await()
                        storiesJob.await()
                    }
                }
                _visibleCount.value = 20
                _serverHasMore.value = feedRepository.hasMorePosts()
                probe.markFirstContent()
            } finally {
                _isRefreshing.value = false
                probe.finish(com.comunidapp.app.domain.perf.ScreenPerfProbe.Ledger.snapshot())
            }
        }
    }

    fun loadMore() {
        if (loadingMore) return
        viewModelScope.launch {
            loadingMore = true
            try {
                val loaded = feedRepository.observeFeedPosts().value.size
                if (_visibleCount.value < loaded) {
                    _visibleCount.update { it + 20 }
                } else if (feedRepository.hasMorePosts()) {
                    feedRepository.loadMorePosts()
                    _visibleCount.update { it + 20 }
                }
                _serverHasMore.value = feedRepository.hasMorePosts()
            } finally {
                loadingMore = false
            }
        }
    }

    fun toggleLike(postId: String) {
        val userId = authRepository.getCurrentUser()?.id ?: return
        viewModelScope.launch {
            feedRepository.toggleLike(postId, userId)
        }
    }

    fun toggleSave(postId: String) {
        val userId = authRepository.getCurrentUser()?.id ?: return
        viewModelScope.launch {
            platformRepository.toggleSavePost(postId, userId)
                .onSuccess { saved ->
                    _actionMessage.value = if (saved) "Publicación guardada" else "Quitada de guardados"
                }
                .onFailure { error ->
                    _actionMessage.value = error.message ?: "No se pudo guardar"
                }
        }
    }

    fun reportPost(postId: String) {
        val userId = authRepository.getCurrentUser()?.id ?: return
        viewModelScope.launch {
            platformRepository.reportContent(
                reporterId = userId,
                targetType = ReportTargetType.POST,
                targetId = postId,
                reason = "Contenido inapropiado"
            ).onSuccess {
                _actionMessage.value = "Reporte enviado a LeoVer"
            }.onFailure { error ->
                _actionMessage.value = error.message ?: "No se pudo reportar"
            }
        }
    }

    fun blockAuthor(authorId: String) {
        val userId = authRepository.getCurrentUser()?.id ?: return
        viewModelScope.launch {
            platformRepository.blockUser(userId, authorId)
                .onSuccess { _actionMessage.value = "Autor bloqueado" }
                .onFailure { error ->
                    _actionMessage.value = error.message ?: "No se pudo bloquear"
                }
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun openComments(postId: String) {
        _commentsPostId.value = postId
        viewModelScope.launch { feedRepository.refreshComments(postId) }
    }

    fun closeComments() {
        _commentsPostId.value = null
    }

    fun sendComment(content: String) {
        val postId = _commentsPostId.value ?: return
        val user = authRepository.getCurrentUser() ?: return
        viewModelScope.launch {
            feedRepository.addComment(postId, user.id, user.name, content)
            feedRepository.refreshComments(postId)
        }
    }

    fun deleteOwnComment(commentId: String) {
        val postId = _commentsPostId.value
        viewModelScope.launch {
            feedRepository.deleteOwnComment(commentId)
            postId?.let { feedRepository.refreshComments(it) }
        }
    }

    private fun matchesLocation(myLocation: String?, otherLocation: String?): Boolean {
        if (myLocation.isNullOrBlank()) return true
        if (otherLocation.isNullOrBlank()) return false
        return otherLocation.contains(myLocation, ignoreCase = true) ||
            myLocation.contains(otherLocation, ignoreCase = true)
    }

    init {
        refresh()
    }
}
