package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.CreateM19PostInput
import com.comunidapp.app.data.model.M19FeedFilter
import com.comunidapp.app.data.model.M19MockOrganizations
import com.comunidapp.app.data.model.M19Post
import com.comunidapp.app.data.model.M19PostStatus
import com.comunidapp.app.data.model.M19PublicComment
import com.comunidapp.app.data.model.M19PublicPost
import com.comunidapp.app.data.model.M19ReactionType
import com.comunidapp.app.data.model.UpdateM19PostInput
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.remote.supabase.m19.M19SocialErrorMapper
import com.comunidapp.app.data.repository.M19SocialRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

sealed class M19SocialFeedUiState {
    data object Loading : M19SocialFeedUiState()
    data object Empty : M19SocialFeedUiState()
    data class Content(val items: List<M19PublicPost>) : M19SocialFeedUiState()
    data class Error(val message: String) : M19SocialFeedUiState()
}

class M19SocialFeedViewModel(
    private val repository: M19SocialRepository = DataProvider.m19SocialRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<M19SocialFeedUiState>(M19SocialFeedUiState.Loading)
    val uiState: StateFlow<M19SocialFeedUiState> = _uiState.asStateFlow()
    private val _filter = MutableStateFlow(M19FeedFilter())
    val filter: StateFlow<M19FeedFilter> = _filter.asStateFlow()
    private var loadJob: Job? = null

    init { load() }

    fun setQuery(value: String) {
        _filter.value = _filter.value.copy(query = value)
        load()
    }

    fun setOrganization(organizationId: String?) {
        _filter.value = _filter.value.copy(organizationId = organizationId)
        load()
    }

    fun clearFilters() {
        _filter.value = M19FeedFilter()
        load()
    }

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = M19SocialFeedUiState.Loading
            repository.searchFeed(_filter.value)
                .onSuccess { list ->
                    _uiState.value = if (list.isEmpty()) M19SocialFeedUiState.Empty
                    else M19SocialFeedUiState.Content(list)
                }
                .onFailure {
                    _uiState.value = M19SocialFeedUiState.Error(
                        M19SocialErrorMapper.userMessage(M19SocialErrorMapper.codeOf(it))
                    )
                }
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M19SocialFeedViewModel() as T
        }
    }
}

class M19PostDetailViewModel(
    private val postId: String,
    private val repository: M19SocialRepository = DataProvider.m19SocialRepository
) : ViewModel() {
    private val _post = MutableStateFlow<M19PublicPost?>(null)
    val post: StateFlow<M19PublicPost?> = _post.asStateFlow()
    private val _comments = MutableStateFlow<List<M19PublicComment>>(emptyList())
    val comments: StateFlow<List<M19PublicComment>> = _comments.asStateFlow()
    private val _myReaction = MutableStateFlow<M19ReactionType?>(null)
    val myReaction: StateFlow<M19ReactionType?> = _myReaction.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            repository.getPublicPostById(postId)
                .onSuccess { _post.value = it }
                .onFailure {
                    _message.value = M19SocialErrorMapper.userMessage(M19SocialErrorMapper.codeOf(it))
                }
            repository.listPublicComments(postId)
                .onSuccess { _comments.value = it }
            _myReaction.value = repository.getMyReaction(postId)
            _loading.value = false
        }
    }

    fun addComment(content: String) {
        viewModelScope.launch {
            repository.addComment(postId, content)
                .onSuccess {
                    _message.value = "Comentario publicado."
                    refresh()
                }
                .onFailure {
                    _message.value = M19SocialErrorMapper.userMessage(M19SocialErrorMapper.codeOf(it))
                }
        }
    }

    fun react(type: M19ReactionType) {
        viewModelScope.launch {
            repository.addReaction(postId, type)
                .onSuccess { refresh() }
                .onFailure {
                    _message.value = M19SocialErrorMapper.userMessage(M19SocialErrorMapper.codeOf(it))
                }
        }
    }

    fun removeReaction() {
        viewModelScope.launch {
            repository.removeReaction(postId)
                .onSuccess { refresh() }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    companion object {
        fun factory(postId: String): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M19PostDetailViewModel(postId) as T
        }
    }
}

sealed class M19PostsManageUiState {
    data object Loading : M19PostsManageUiState()
    data object Empty : M19PostsManageUiState()
    data class Content(val items: List<M19Post>) : M19PostsManageUiState()
    data class Error(val message: String) : M19PostsManageUiState()
}

class M19PostsManageViewModel(
    private val repository: M19SocialRepository = DataProvider.m19SocialRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<M19PostsManageUiState>(M19PostsManageUiState.Loading)
    val uiState: StateFlow<M19PostsManageUiState> = _uiState.asStateFlow()
    private val _canManage = MutableStateFlow(false)
    val canManage: StateFlow<Boolean> = _canManage.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _canManage.value = repository.canManageOrganization(M19MockOrganizations.ORG_NORTE)
            if (!_canManage.value) {
                _uiState.value = M19PostsManageUiState.Error("Sin permisos de gestión.")
                return@launch
            }
            repository.observePostsForOrganization(M19MockOrganizations.ORG_NORTE).collect { list ->
                _uiState.value = if (list.isEmpty()) M19PostsManageUiState.Empty
                else M19PostsManageUiState.Content(list)
            }
        }
    }

    fun publish(postId: String) {
        viewModelScope.launch {
            repository.publishPost(postId)
                .onSuccess { _message.value = "Publicación visible en el feed." }
                .onFailure {
                    _message.value = M19SocialErrorMapper.userMessage(M19SocialErrorMapper.codeOf(it))
                }
        }
    }

    fun hide(postId: String) {
        viewModelScope.launch {
            repository.hidePost(postId)
                .onSuccess { _message.value = "Publicación oculta del feed." }
                .onFailure {
                    _message.value = M19SocialErrorMapper.userMessage(M19SocialErrorMapper.codeOf(it))
                }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M19PostsManageViewModel() as T
        }
    }
}

sealed class M19PostEditUiState {
    data object Loading : M19PostEditUiState()
    data class Form(
        val title: String = "",
        val content: String = "",
        val organizationId: String = M19MockOrganizations.ORG_NORTE,
        val isEdit: Boolean = false
    ) : M19PostEditUiState()
    data class Error(val message: String) : M19PostEditUiState()
}

class M19PostEditViewModel(
    private val postId: String?,
    private val repository: M19SocialRepository = DataProvider.m19SocialRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<M19PostEditUiState>(M19PostEditUiState.Loading)
    val uiState: StateFlow<M19PostEditUiState> = _uiState.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        if (postId == null) {
            _uiState.value = M19PostEditUiState.Form(isEdit = false)
        } else {
            viewModelScope.launch {
                repository.observePostById(postId).collect { post ->
                    if (post == null) {
                        _uiState.value = M19PostEditUiState.Error("Publicación no encontrada.")
                    } else {
                        _uiState.value = M19PostEditUiState.Form(
                            title = post.title,
                            content = post.content,
                            organizationId = post.organizationId,
                            isEdit = true
                        )
                    }
                }
            }
        }
    }

    fun save(title: String, content: String, onSaved: (String) -> Unit) {
        viewModelScope.launch {
            val result = if (postId == null) {
                repository.createPost(
                    CreateM19PostInput(
                        organizationId = M19MockOrganizations.ORG_NORTE,
                        title = title,
                        content = content
                    )
                )
            } else {
                repository.updatePost(
                    UpdateM19PostInput(postId = postId, title = title, content = content)
                )
            }
            result
                .onSuccess { onSaved(it.id) }
                .onFailure {
                    _message.value = M19SocialErrorMapper.userMessage(M19SocialErrorMapper.codeOf(it))
                }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    companion object {
        fun factory(postId: String?): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M19PostEditViewModel(postId) as T
        }
    }
}

fun m19PostStatusLabel(status: M19PostStatus): String = when (status) {
    M19PostStatus.DRAFT -> "Borrador"
    M19PostStatus.PUBLISHED -> "Publicado"
    M19PostStatus.HIDDEN -> "Oculto"
    M19PostStatus.REMOVED -> "Eliminado"
}

fun m19ReactionTypeLabel(type: M19ReactionType): String = when (type) {
    M19ReactionType.LIKE -> "Me gusta"
    M19ReactionType.SUPPORT -> "Apoyo"
    M19ReactionType.CELEBRATE -> "Celebrar"
}
