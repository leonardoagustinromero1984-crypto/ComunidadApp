package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.FriendConnection
import com.comunidapp.app.data.model.FriendConnectionStatus
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.FriendRepository
import com.comunidapp.app.data.repository.UserRepository
import kotlinx.coroutines.flow.stateIn
import com.comunidapp.app.domain.ProfilePrivacy
import com.comunidapp.app.domain.social.FriendshipErrorMapper
import com.comunidapp.app.domain.user.toBridgeUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FriendListItem(
    val user: User,
    val connection: FriendConnection,
    val pending: Boolean = false
)

data class FriendsListUiState(
    val isLoading: Boolean = true,
    val friends: List<FriendListItem> = emptyList(),
    val incoming: List<FriendListItem> = emptyList(),
    val outgoing: List<FriendListItem> = emptyList(),
    val actionInProgressId: String? = null,
    val actionMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class FriendsListViewModel(
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val friendRepository: FriendRepository = DataProvider.friendRepository,
    private val userRepository: UserRepository = DataProvider.userRepository
) : ViewModel() {

    private val _actionInProgressId = MutableStateFlow<String?>(null)
    private val _actionMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<FriendsListUiState> = authRepository.observeAuthState()
        .flatMapLatest { authUser ->
            if (authUser == null) {
                flowOf(FriendsListUiState(isLoading = false))
            } else {
                friendRepository.observeConnections(authUser.id).flatMapLatest { connections ->
                    flow {
                        emit(FriendsListUiState(isLoading = true))
                        val accepted = connections.filter { it.status == FriendConnectionStatus.ACCEPTED }
                        val pendingOut = connections.filter {
                            it.status == FriendConnectionStatus.PENDING &&
                                it.requesterId == authUser.id
                        }
                        val pendingIn = connections.filter {
                            it.status == FriendConnectionStatus.PENDING &&
                                it.addresseeId == authUser.id
                        }
                        val friends = resolve(authUser.id, accepted)
                        val incoming = resolve(authUser.id, pendingIn)
                        val outgoing = resolve(authUser.id, pendingOut)
                        emit(
                            FriendsListUiState(
                                isLoading = false,
                                friends = friends,
                                incoming = incoming,
                                outgoing = outgoing,
                                actionInProgressId = _actionInProgressId.value,
                                actionMessage = _actionMessage.value
                            )
                        )
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FriendsListUiState())

    private suspend fun resolve(
        viewerId: String,
        connections: List<FriendConnection>
    ): List<FriendListItem> {
        val ids = ProfilePrivacy.friendIdsFor(viewerId, connections).ifEmpty {
            connections.map { conn ->
                if (conn.requesterId == viewerId) conn.addresseeId else conn.requesterId
            }.toSet()
        }
        return connections.mapNotNull { connection ->
            val otherId = if (connection.requesterId == viewerId) {
                connection.addresseeId
            } else {
                connection.requesterId
            }
            val public = userRepository.getPublicProfile(viewerId, otherId).getOrNull()
            val user = public?.toBridgeUser() ?: return@mapNotNull null
            FriendListItem(
                user = user,
                connection = connection,
                pending = connection.status == FriendConnectionStatus.PENDING
            )
        }.filter { it.user.id in ids || it.pending }
    }

    fun acceptRequest(connectionId: String) {
        val userId = authRepository.getCurrentUser()?.id ?: return
        viewModelScope.launch {
            _actionInProgressId.value = connectionId
            _actionMessage.value = null
            friendRepository.respondToRequest(connectionId, accept = true, responderId = userId)
                .onSuccess { _actionMessage.value = "Solicitud aceptada" }
                .onFailure {
                    _actionMessage.value = FriendshipErrorMapper.userMessage(
                        it,
                        FriendshipErrorMapper.Operation.RESPOND
                    )
                }
            _actionInProgressId.value = null
        }
    }

    fun rejectRequest(connectionId: String) {
        val userId = authRepository.getCurrentUser()?.id ?: return
        viewModelScope.launch {
            _actionInProgressId.value = connectionId
            _actionMessage.value = null
            friendRepository.respondToRequest(connectionId, accept = false, responderId = userId)
                .onSuccess { _actionMessage.value = "Solicitud rechazada" }
                .onFailure {
                    _actionMessage.value = FriendshipErrorMapper.userMessage(
                        it,
                        FriendshipErrorMapper.Operation.RESPOND
                    )
                }
            _actionInProgressId.value = null
        }
    }
}
