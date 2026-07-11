package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.FriendsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val friendsRepository: FriendsRepository = DataProvider.friendsRepository
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
            val users = friendsRepository.searchUsers(query, currentUser.id)
            val items = users.map { user ->
                val actionState = when {
                    friendsRepository.isFriend(currentUser.id, user.id) -> FriendActionState.FRIEND
                    friendsRepository.hasPendingRequest(currentUser.id, user.id) -> FriendActionState.PENDING
                    else -> FriendActionState.NONE
                }
                UserSearchItem(user = user, actionState = actionState)
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
            friendsRepository.sendFriendRequest(currentUser.id, userId)
                .onSuccess {
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
