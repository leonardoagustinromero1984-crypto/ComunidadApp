package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.FriendConnection
import com.comunidapp.app.data.model.FriendConnectionStatus
import com.comunidapp.app.data.model.NotificationType
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.FriendRepository
import com.comunidapp.app.data.repository.UserRepository
import com.comunidapp.app.domain.social.FriendshipErrorMapper
import com.comunidapp.app.domain.user.toBridgeUser
import com.comunidapp.app.notifications.NotificationDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FriendRequestItem(
    val connection: FriendConnection,
    val user: User
)

data class FriendRequestsUiState(
    val isLoading: Boolean = true,
    val incoming: List<FriendRequestItem> = emptyList(),
    val outgoing: List<FriendRequestItem> = emptyList(),
    val actionInProgressId: String? = null,
    val actionMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class FriendRequestsViewModel(
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val friendRepository: FriendRepository = DataProvider.friendRepository,
    private val userRepository: UserRepository = DataProvider.userRepository
) : ViewModel() {

    private val _actionInProgressId = MutableStateFlow<String?>(null)
    private val _actionMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<FriendRequestsUiState> = authRepository.observeAuthState()
        .flatMapLatest { authUser ->
            if (authUser == null) {
                flowOf(FriendRequestsUiState(isLoading = false))
            } else {
                friendRepository.observeConnections(authUser.id).flatMapLatest { connections ->
                    flow {
                        emit(
                            FriendRequestsUiState(
                                isLoading = true,
                                actionInProgressId = _actionInProgressId.value,
                                actionMessage = _actionMessage.value
                            )
                        )
                        val incoming = connections
                            .filter {
                                it.status == FriendConnectionStatus.PENDING &&
                                    it.addresseeId == authUser.id
                            }
                            .mapNotNull { connection ->
                                userRepository.getPublicProfile(authUser.id, connection.requesterId)
                                    .getOrNull()
                                    ?.toBridgeUser()
                                    ?.let { user -> FriendRequestItem(connection, user) }
                            }
                        val outgoing = connections
                            .filter {
                                it.status == FriendConnectionStatus.PENDING &&
                                    it.requesterId == authUser.id
                            }
                            .mapNotNull { connection ->
                                userRepository.getPublicProfile(authUser.id, connection.addresseeId)
                                    .getOrNull()
                                    ?.toBridgeUser()
                                    ?.let { user -> FriendRequestItem(connection, user) }
                            }
                        emit(
                            FriendRequestsUiState(
                                isLoading = false,
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
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            FriendRequestsUiState()
        )

    fun acceptRequest(connectionId: String) {
        val userId = authRepository.getCurrentUser()?.id ?: return
        viewModelScope.launch {
            _actionInProgressId.value = connectionId
            _actionMessage.value = null
            friendRepository.respondToRequest(connectionId, accept = true, responderId = userId)
                .onSuccess {
                    _actionMessage.value = "Solicitud aceptada"
                    val requesterId = uiState.value.incoming
                        .find { it.connection.id == connectionId }
                        ?.connection
                        ?.requesterId
                    if (!requesterId.isNullOrBlank()) {
                        NotificationDispatcher.notify(
                            userId = requesterId,
                            type = NotificationType.FRIEND_ACCEPTED,
                            title = "Solicitud aceptada",
                            body = "Tu solicitud de amistad fue aceptada en LeoVer",
                            relatedId = userId,
                            relatedType = "user"
                        )
                    }
                }
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

    fun cancelRequest(connectionId: String) {
        val userId = authRepository.getCurrentUser()?.id ?: return
        viewModelScope.launch {
            _actionInProgressId.value = connectionId
            _actionMessage.value = null
            friendRepository.cancelRequest(connectionId, userId)
                .onSuccess { _actionMessage.value = "Solicitud cancelada" }
                .onFailure {
                    _actionMessage.value = FriendshipErrorMapper.userMessage(
                        it,
                        FriendshipErrorMapper.Operation.CANCEL
                    )
                }
            _actionInProgressId.value = null
        }
    }

    fun clearActionMessage() {
        _actionMessage.update { null }
    }
}
