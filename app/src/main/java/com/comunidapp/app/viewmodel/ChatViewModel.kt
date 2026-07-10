package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.ChatMessage
import com.comunidapp.app.data.model.Conversation
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.ChatRepository
import com.comunidapp.app.data.model.NotificationType
import com.comunidapp.app.notifications.NotificationDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatListViewModel(
    private val chatRepository: ChatRepository = DataProvider.chatRepository
) : ViewModel() {

    private val userId = AuthProvider.repository.getCurrentUser()?.id.orEmpty()

    val conversations: StateFlow<List<Conversation>> =
        if (userId.isBlank()) {
            MutableStateFlow(emptyList())
        } else {
            chatRepository.observeConversations(userId)
                .catch { emit(emptyList()) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        }
}

sealed class ChatStartState {
    data object Loading : ChatStartState()
    data class Ready(val conversationId: String) : ChatStartState()
    data class Error(val message: String) : ChatStartState()
}

class ChatStartViewModel(
    private val peerUserId: String,
    private val peerName: String,
    private val chatRepository: ChatRepository = DataProvider.chatRepository
) : ViewModel() {

    private val _state = MutableStateFlow<ChatStartState>(ChatStartState.Loading)
    val state: StateFlow<ChatStartState> = _state.asStateFlow()

    init {
        openConversation()
    }

    fun openConversation() {
        val currentUser = AuthProvider.repository.getCurrentUser()
        if (currentUser == null) {
            _state.value = ChatStartState.Error("Iniciá sesión para chatear")
            return
        }
        if (peerUserId.isBlank()) {
            _state.value = ChatStartState.Error("Usuario destino no válido")
            return
        }
        viewModelScope.launch {
            _state.value = ChatStartState.Loading
            chatRepository.getOrCreateConversation(
                currentUser = currentUser,
                peerUserId = peerUserId,
                peerName = peerName
            ).onSuccess { conversationId ->
                _state.value = ChatStartState.Ready(conversationId)
            }.onFailure { error ->
                _state.value = ChatStartState.Error(error.message ?: "No se pudo abrir el chat")
            }
        }
    }

    companion object {
        fun factory(peerUserId: String, peerName: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ChatStartViewModel(
                        peerUserId = peerUserId,
                        peerName = peerName
                    ) as T
                }
            }
    }
}

class ChatThreadViewModel(
    private val conversationId: String,
    private val peerUserId: String = "",
    private val chatRepository: ChatRepository = DataProvider.chatRepository
) : ViewModel() {

    val messages: StateFlow<List<ChatMessage>> =
        if (conversationId.isBlank()) {
            MutableStateFlow(emptyList())
        } else {
            chatRepository.observeMessages(conversationId)
                .catch { emit(emptyList()) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        }

    private val _sendState = MutableStateFlow<SendMessageState>(SendMessageState.Idle)
    val sendState: StateFlow<SendMessageState> = _sendState.asStateFlow()

    fun sendMessage(content: String) {
        val sender = AuthProvider.repository.getCurrentUser() ?: return
        if (conversationId.isBlank()) {
            _sendState.value = SendMessageState.Error("Conversación no válida")
            return
        }
        if (content.isBlank()) return
        viewModelScope.launch {
            _sendState.value = SendMessageState.Sending
            chatRepository.sendMessage(conversationId, sender, content)
                .onSuccess {
                    _sendState.value = SendMessageState.Sent
                    val targetId = peerUserId.ifBlank {
                        runCatching {
                            chatRepository.observeConversations(sender.id)
                                .first()
                                .find { it.id == conversationId }
                                ?.peerUserId
                        }.getOrNull().orEmpty()
                    }
                    if (targetId.isNotBlank()) {
                        NotificationDispatcher.notify(
                            userId = targetId,
                            type = NotificationType.CHAT_MESSAGE,
                            title = "Nuevo mensaje",
                            body = "${sender.name}: ${content.take(80)}",
                            relatedId = conversationId,
                            relatedType = "conversation"
                        )
                    }
                }
                .onFailure { error ->
                    _sendState.value = SendMessageState.Error(error.message ?: "Error al enviar")
                }
        }
    }

    fun clearSendState() {
        _sendState.value = SendMessageState.Idle
    }

    companion object {
        fun factory(conversationId: String, peerUserId: String = ""): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ChatThreadViewModel(
                        conversationId = conversationId,
                        peerUserId = peerUserId
                    ) as T
                }
            }
    }
}

sealed class SendMessageState {
    data object Idle : SendMessageState()
    data object Sending : SendMessageState()
    data object Sent : SendMessageState()
    data class Error(val message: String) : SendMessageState()
}
