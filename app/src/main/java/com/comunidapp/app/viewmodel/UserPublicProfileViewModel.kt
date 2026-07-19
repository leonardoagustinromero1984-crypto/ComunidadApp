package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.model.FriendConnection
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.ProfileRelation
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.FeedRepository
import com.comunidapp.app.data.repository.FriendRepository
import com.comunidapp.app.data.repository.PetRepository
import com.comunidapp.app.data.repository.UserRepository
import com.comunidapp.app.domain.ProfilePrivacy
import com.comunidapp.app.data.model.NotificationType
import com.comunidapp.app.domain.user.toBridgeUser
import com.comunidapp.app.notifications.NotificationDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PublicProfileUiState(
    val isLoading: Boolean = true,
    val user: User? = null,
    val pets: List<Pet> = emptyList(),
    val posts: List<FeedPost> = emptyList(),
    val relation: ProfileRelation = ProfileRelation.LOCKED,
    val connectionId: String? = null,
    val actionInProgress: Boolean = false,
    val actionMessage: String? = null,
    val errorMessage: String? = null
) {
    val canViewFullProfile: Boolean = ProfilePrivacy.canViewFullProfile(relation)
    val canInteract: Boolean = ProfilePrivacy.canInteract(relation)
}

@OptIn(ExperimentalCoroutinesApi::class)
class UserPublicProfileViewModel(
    private val userId: String,
    private val userRepository: UserRepository = DataProvider.userRepository,
    private val petRepository: PetRepository = DataProvider.petRepository,
    private val feedRepository: FeedRepository = DataProvider.feedRepository,
    private val friendRepository: FriendRepository = DataProvider.friendRepository,
    private val authRepository: AuthRepository = AuthProvider.repository
) : ViewModel() {

    private val _actionMessage = MutableStateFlow<String?>(null)
    private val _actionInProgress = MutableStateFlow(false)

    val uiState: StateFlow<PublicProfileUiState> =
        authRepository.observeAuthState().flatMapLatest { currentUser ->
            if (userId.isBlank()) {
                flowOf(PublicProfileUiState(isLoading = false, errorMessage = "Perfil no válido"))
            } else {
                val connectionsFlow = if (currentUser == null) {
                    flowOf(emptyList())
                } else {
                    friendRepository.observeConnections(currentUser.id)
                        .catch { emit(emptyList()) }
                }
                combine(
                    connectionsFlow,
                    kotlinx.coroutines.flow.flow {
                        val viewerId = currentUser?.id
                        if (viewerId == null) {
                            emit(null)
                            return@flow
                        }
                        while (true) {
                            val public = userRepository.getPublicProfile(viewerId, userId).getOrNull()
                            emit(public?.toBridgeUser())
                            kotlinx.coroutines.delay(4_000)
                        }
                    }.catch { emit(null) },
                    petRepository.observePets(),
                    feedRepository.observeFeedPosts(),
                    combine(_actionMessage, _actionInProgress) { message, inProgress ->
                        message to inProgress
                    }
                ) { connections, user, pets, posts, actionState ->
                    val (actionMessage, actionInProgress) = actionState
                    buildUiState(
                        viewerId = currentUser?.id,
                        user = user,
                        connections = connections,
                        pets = pets,
                        posts = posts,
                        actionMessage = actionMessage,
                        actionInProgress = actionInProgress
                    )
                }
            }
        }.catch {
            emit(
                PublicProfileUiState(
                    isLoading = false,
                    errorMessage = "No se pudo cargar el perfil"
                )
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            PublicProfileUiState()
        )

    private fun buildUiState(
        viewerId: String?,
        user: User?,
        connections: List<FriendConnection>,
        pets: List<Pet>,
        posts: List<FeedPost>,
        actionMessage: String?,
        actionInProgress: Boolean
    ): PublicProfileUiState {
        if (user == null) {
            return PublicProfileUiState(
                isLoading = false,
                errorMessage = "Usuario no encontrado",
                actionMessage = actionMessage,
                actionInProgress = actionInProgress
            )
        }

        val relation = ProfilePrivacy.resolveRelation(viewerId, user, connections)
        val connection = connections.firstOrNull { conn ->
            (conn.requesterId == viewerId && conn.addresseeId == userId) ||
                (conn.requesterId == userId && conn.addresseeId == viewerId)
        }
        val canView = ProfilePrivacy.canViewFullProfile(relation)

        return PublicProfileUiState(
            isLoading = false,
            user = user,
            pets = if (canView) pets.filter { it.ownerId == userId } else emptyList(),
            posts = if (canView) posts.filter { it.authorId == userId } else emptyList(),
            relation = relation,
            connectionId = connection?.id,
            actionInProgress = actionInProgress,
            actionMessage = actionMessage
        )
    }

    fun sendFriendRequest() {
        val viewer = authRepository.getCurrentUser() ?: return
        if (userId.isBlank() || viewer.id == userId) return
        viewModelScope.launch {
            _actionInProgress.value = true
            _actionMessage.value = null
            friendRepository.sendFriendRequest(viewer.id, userId)
                .onSuccess {
                    _actionMessage.value = "Solicitud enviada"
                    NotificationDispatcher.notify(
                        userId = userId,
                        type = NotificationType.FRIEND_REQUEST,
                        title = "Nueva solicitud de amistad",
                        body = "${viewer.name} quiere conectar con vos en LeoVer",
                        relatedId = viewer.id,
                        relatedType = "user"
                    )
                }
                .onFailure { _actionMessage.value = it.message ?: "No se pudo enviar la solicitud" }
            _actionInProgress.value = false
        }
    }

    fun acceptFriendRequest() {
        val viewer = authRepository.getCurrentUser() ?: return
        val connectionId = uiState.value.connectionId ?: return
        viewModelScope.launch {
            _actionInProgress.value = true
            _actionMessage.value = null
            friendRepository.respondToRequest(connectionId, accept = true, responderId = viewer.id)
                .onSuccess {
                    _actionMessage.value = "Ahora son amigos"
                    // El perfil que estamos viendo es quien envió la solicitud
                    NotificationDispatcher.notify(
                        userId = userId,
                        type = NotificationType.FRIEND_ACCEPTED,
                        title = "Solicitud aceptada",
                        body = "${viewer.name} aceptó tu solicitud en LeoVer",
                        relatedId = viewer.id,
                        relatedType = "user"
                    )
                }
                .onFailure { _actionMessage.value = it.message ?: "No se pudo aceptar la solicitud" }
            _actionInProgress.value = false
        }
    }

    fun rejectFriendRequest() {
        val viewer = authRepository.getCurrentUser() ?: return
        val connectionId = uiState.value.connectionId ?: return
        viewModelScope.launch {
            _actionInProgress.value = true
            _actionMessage.value = null
            friendRepository.respondToRequest(connectionId, accept = false, responderId = viewer.id)
                .onSuccess { _actionMessage.value = "Solicitud rechazada" }
                .onFailure { _actionMessage.value = it.message ?: "No se pudo rechazar la solicitud" }
            _actionInProgress.value = false
        }
    }

    fun cancelFriendRequest() {
        val viewer = authRepository.getCurrentUser() ?: return
        val connectionId = uiState.value.connectionId ?: return
        viewModelScope.launch {
            _actionInProgress.value = true
            _actionMessage.value = null
            friendRepository.cancelRequest(connectionId, viewer.id)
                .onSuccess { _actionMessage.value = "Solicitud cancelada" }
                .onFailure { _actionMessage.value = it.message ?: "No se pudo cancelar la solicitud" }
            _actionInProgress.value = false
        }
    }

    fun clearActionMessage() {
        _actionMessage.update { null }
    }

    companion object {
        fun factory(userId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return UserPublicProfileViewModel(userId = userId) as T
                }
            }
    }
}
