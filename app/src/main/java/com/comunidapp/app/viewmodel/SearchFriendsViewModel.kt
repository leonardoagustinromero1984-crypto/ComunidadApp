package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.FriendConnectionStatus
import com.comunidapp.app.data.model.NotificationType
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.FriendRepository
import com.comunidapp.app.data.repository.UserRepository
import com.comunidapp.app.domain.ProfilePrivacy
import com.comunidapp.app.notifications.NotificationDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FriendActionState {
    NONE,
    FRIEND,
    PENDING,
    LOADING
}

data class UserSearchItem(
    val user: User,
    val actionState: FriendActionState = FriendActionState.NONE
)

data class SearchFriendsUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<UserSearchItem> = emptyList(),
    val message: String? = null
)

class SearchFriendsViewModel(
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val userRepository: UserRepository = DataProvider.userRepository,
    private val friendRepository: FriendRepository = DataProvider.friendRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchFriendsUiState())
    val uiState: StateFlow<SearchFriendsUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query, message = null) }
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _uiState.update { it.copy(results = emptyList(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            delay(300)
            val currentUser = authRepository.getCurrentUser() ?: return@launch
            val connections = friendRepository.observeConnections(currentUser.id).first()
            val friendIds = ProfilePrivacy.friendIdsFor(currentUser.id, connections)
            val users = userRepository.searchUsers(query, currentUser.id)
                .filter { it.id !in friendIds }
                .take(20)
            val items = users.map { user ->
                val pending = connections.any {
                    it.status == FriendConnectionStatus.PENDING &&
                        (
                            (it.requesterId == currentUser.id && it.addresseeId == user.id) ||
                                (it.requesterId == user.id && it.addresseeId == currentUser.id)
                            )
                }
                UserSearchItem(
                    user = user,
                    actionState = if (pending) FriendActionState.PENDING else FriendActionState.NONE
                )
            }
            _uiState.update {
                it.copy(
                    isSearching = false,
                    results = items,
                    message = if (items.isEmpty()) "No encontramos usuarios con ese nombre." else null
                )
            }
        }
    }

    fun sendFriendRequest(userId: String) {
        viewModelScope.launch {
            val currentUser = authRepository.getCurrentUser() ?: return@launch
            _uiState.update { state ->
                state.copy(
                    results = state.results.map {
                        if (it.user.id == userId) it.copy(actionState = FriendActionState.LOADING) else it
                    }
                )
            }
            friendRepository.sendFriendRequest(currentUser.id, userId)
                .onSuccess {
                    NotificationDispatcher.notify(
                        userId = userId,
                        type = NotificationType.FRIEND_REQUEST,
                        title = "Nueva solicitud de amistad",
                        body = "${currentUser.name} quiere ser tu amigo en LeoVer",
                        relatedId = currentUser.id,
                        relatedType = "user"
                    )
                    _uiState.update { state ->
                        state.copy(
                            results = state.results.map {
                                if (it.user.id == userId) it.copy(actionState = FriendActionState.PENDING) else it
                            },
                            message = "Solicitud enviada"
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { state ->
                        state.copy(
                            results = state.results.map {
                                if (it.user.id == userId) it.copy(actionState = FriendActionState.NONE) else it
                            },
                            message = error.message ?: "No se pudo enviar la solicitud"
                        )
                    }
                }
        }
    }
}
