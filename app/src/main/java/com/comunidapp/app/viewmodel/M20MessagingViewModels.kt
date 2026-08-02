package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.M20ConversationStatus
import com.comunidapp.app.data.model.M20PublicConversation
import com.comunidapp.app.data.model.M20PublicMessage
import com.comunidapp.app.data.model.SendM20MessageInput
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.M20MessagingRepository
import com.comunidapp.app.domain.m20.M20MessagingResilience
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class M20ConversationListUiState {
    data object Loading : M20ConversationListUiState()
    data object Empty : M20ConversationListUiState()
    data class Content(val items: List<M20PublicConversation>) : M20ConversationListUiState()
    data class Error(val message: String) : M20ConversationListUiState()
}

class M20ConversationListViewModel(
    private val repository: M20MessagingRepository = DataProvider.m20MessagingRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<M20ConversationListUiState>(M20ConversationListUiState.Loading)
    val uiState: StateFlow<M20ConversationListUiState> = _uiState.asStateFlow()
    private var observeJob: Job? = null

    init { observe() }

    fun observe() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            _uiState.value = M20ConversationListUiState.Loading
            repository.observeConversations().collect { list ->
                _uiState.value = if (list.isEmpty()) {
                    M20ConversationListUiState.Empty
                } else {
                    M20ConversationListUiState.Content(list)
                }
            }
        }
    }

    fun refresh() {
        observe()
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M20ConversationListViewModel() as T
        }
    }
}

sealed class M20ThreadUiState {
    data object Loading : M20ThreadUiState()
    data class Content(
        val conversation: M20PublicConversation?,
        val messages: List<M20PublicMessage>
    ) : M20ThreadUiState()
    data class Error(val message: String) : M20ThreadUiState()
}

class M20ThreadViewModel(
    private val conversationId: String,
    private val repository: M20MessagingRepository = DataProvider.m20MessagingRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<M20ThreadUiState>(M20ThreadUiState.Loading)
    val uiState: StateFlow<M20ThreadUiState> = _uiState.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    init {
        viewModelScope.launch {
            repository.markConversationRead(conversationId)
            repository.getConversation(conversationId)
                .onSuccess { conv ->
                    repository.observeMessages(conversationId).collect { messages ->
                        _uiState.value = M20ThreadUiState.Content(conversation = conv, messages = messages)
                    }
                }
                .onFailure {
                    _uiState.value = M20ThreadUiState.Error(M20MessagingResilience.safeUserMessage(it))
                }
        }
    }

    fun sendMessage(content: String) {
        if (content.isBlank()) return
        viewModelScope.launch {
            _sending.value = true
            repository.sendMessage(SendM20MessageInput(conversationId = conversationId, content = content))
                .onSuccess { _message.value = "Mensaje enviado." }
                .onFailure { _message.value = M20MessagingResilience.safeUserMessage(it) }
            _sending.value = false
        }
    }

    fun blockUser() {
        viewModelScope.launch {
            repository.blockUser(conversationId)
                .onSuccess { _message.value = "Usuario bloqueado (stub)." }
                .onFailure { _message.value = M20MessagingResilience.safeUserMessage(it) }
        }
    }

    fun archiveConversation() {
        viewModelScope.launch {
            repository.archiveConversation(conversationId)
                .onSuccess { _message.value = "Conversación archivada." }
                .onFailure { _message.value = M20MessagingResilience.safeUserMessage(it) }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    companion object {
        fun factory(conversationId: String): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M20ThreadViewModel(conversationId) as T
        }
    }
}

fun m20ConversationStatusLabel(status: M20ConversationStatus): String = when (status) {
    M20ConversationStatus.ACTIVE -> "Activa"
    M20ConversationStatus.ARCHIVED -> "Archivada"
    M20ConversationStatus.BLOCKED -> "Bloqueada"
}

fun m20MessageStatusLabel(status: com.comunidapp.app.data.model.M20MessageStatus): String =
    when (status) {
        com.comunidapp.app.data.model.M20MessageStatus.SENT -> "Enviado"
        com.comunidapp.app.data.model.M20MessageStatus.DELIVERED -> "Entregado"
        com.comunidapp.app.data.model.M20MessageStatus.READ -> "Leído"
    }
