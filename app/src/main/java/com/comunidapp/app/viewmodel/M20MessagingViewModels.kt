package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.CreateM20DirectConversationInput
import com.comunidapp.app.data.model.EditM20MessageInput
import com.comunidapp.app.data.model.M20ConversationStatus
import com.comunidapp.app.data.model.M20DeletedContent
import com.comunidapp.app.data.model.M20MockUsers
import com.comunidapp.app.data.model.M20PublicConversation
import com.comunidapp.app.data.model.M20PublicMessage
import com.comunidapp.app.data.model.SendM20MessageInput
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.M20MessagingRepository
import com.comunidapp.app.domain.m20.M20MessagingResilience
import java.util.UUID
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

    fun startDirectConversation(peerUserId: String) {
        viewModelScope.launch {
            repository.createDirectConversation(CreateM20DirectConversationInput(peerUserId = peerUserId))
                .onFailure {
                    _uiState.value = M20ConversationListUiState.Error(
                        M20MessagingResilience.safeUserMessage(it)
                    )
                }
        }
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
    data object Empty : M20ThreadUiState()
    data object Sending : M20ThreadUiState()
    data class SendFailed(val message: String) : M20ThreadUiState()
    data object Blocked : M20ThreadUiState()
    data object Archived : M20ThreadUiState()
    data object PermissionDenied : M20ThreadUiState()
    data object AttachmentUnavailable : M20ThreadUiState()
    data class Content(
        val conversation: M20PublicConversation?,
        val messages: List<M20PublicMessage>,
        val hasMore: Boolean = false,
        val loadingMore: Boolean = false,
        val replyTo: M20PublicMessage? = null,
        val editing: M20PublicMessage? = null
    ) : M20ThreadUiState()
    data class PartialData(
        val conversation: M20PublicConversation?,
        val messages: List<M20PublicMessage>,
        val message: String,
        val hasMore: Boolean = false
    ) : M20ThreadUiState()
    data class Error(val message: String) : M20ThreadUiState()
}

class M20ThreadViewModel(
    private val conversationId: String,
    private val repository: M20MessagingRepository = DataProvider.m20MessagingRepository,
    private val actorUserId: () -> String? = { AuthProvider.repository.getCurrentUser()?.id ?: M20MockUsers.ADMIN }
) : ViewModel() {
    private val _uiState = MutableStateFlow<M20ThreadUiState>(M20ThreadUiState.Loading)
    val uiState: StateFlow<M20ThreadUiState> = _uiState.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private var nextCursor: String? = null
    private var accumulated = mutableListOf<M20PublicMessage>()
    private var conversation: M20PublicConversation? = null

    init {
        viewModelScope.launch {
            repository.markConversationRead(conversationId)
            loadInitial()
        }
    }

    private suspend fun loadInitial() {
        _uiState.value = M20ThreadUiState.Loading
        repository.getConversation(conversationId)
            .onSuccess { conv ->
                conversation = conv
                when (conv.status) {
                    M20ConversationStatus.BLOCKED -> _uiState.value = M20ThreadUiState.Blocked
                    M20ConversationStatus.ARCHIVED -> _uiState.value = M20ThreadUiState.Archived
                    else -> loadPage(replace = true)
                }
            }
            .onFailure {
                _uiState.value = M20ThreadUiState.Error(M20MessagingResilience.safeUserMessage(it))
            }
    }

    private suspend fun loadPage(replace: Boolean) {
        repository.getMessagesPage(conversationId, cursor = if (replace) null else nextCursor)
            .onSuccess { page ->
                val merged = if (replace) page.items else (accumulated + page.items).distinctBy { it.id }
                accumulated = merged.toMutableList()
                nextCursor = page.nextCursor
                _uiState.value = when {
                    merged.isEmpty() -> M20ThreadUiState.Empty
                    else -> M20ThreadUiState.Content(
                        conversation = conversation,
                        messages = merged,
                        hasMore = page.hasMore
                    )
                }
            }
            .onFailure {
                val preserved = accumulated.toList()
                val msg = M20MessagingResilience.safeUserMessage(it)
                _uiState.value = if (preserved.isNotEmpty()) {
                    M20ThreadUiState.PartialData(conversation, preserved, msg, nextCursor != null)
                } else {
                    M20ThreadUiState.Error(msg)
                }
            }
    }

    fun loadMore() {
        val current = _uiState.value
        if (current !is M20ThreadUiState.Content || current.loadingMore || !current.hasMore) return
        viewModelScope.launch {
            _uiState.value = current.copy(loadingMore = true)
            loadPage(replace = false)
        }
    }

    fun setReplyTo(message: M20PublicMessage?) {
        val current = _uiState.value
        if (current is M20ThreadUiState.Content) {
            _uiState.value = current.copy(replyTo = message, editing = null)
        }
    }

    fun setEditing(message: M20PublicMessage?) {
        val current = _uiState.value
        if (current is M20ThreadUiState.Content) {
            _uiState.value = current.copy(editing = message, replyTo = null)
        }
    }

    fun cancelComposerModes() {
        val current = _uiState.value
        if (current is M20ThreadUiState.Content) {
            _uiState.value = current.copy(replyTo = null, editing = null)
        }
    }

    fun sendMessage(content: String, attachmentRef: String? = null) {
        if (content.isBlank() && attachmentRef.isNullOrBlank()) return
        viewModelScope.launch {
            val current = _uiState.value
            if (current is M20ThreadUiState.Content && current.editing != null) {
                editMessage(current.editing.id, content)
                return@launch
            }
            _uiState.value = M20ThreadUiState.Sending
            val replyId = (current as? M20ThreadUiState.Content)?.replyTo?.id
            repository.sendMessage(
                SendM20MessageInput(
                    conversationId = conversationId,
                    content = content,
                    clientMessageId = "client_${UUID.randomUUID()}",
                    replyToMessageId = replyId,
                    attachmentRef = attachmentRef
                )
            )
                .onSuccess {
                    cancelComposerModes()
                    loadPage(replace = true)
                    _message.value = "Mensaje enviado."
                }
                .onFailure {
                    _uiState.value = M20ThreadUiState.SendFailed(M20MessagingResilience.safeUserMessage(it))
                    loadPage(replace = true)
                }
        }
    }

    fun editMessage(messageId: String, content: String) {
        viewModelScope.launch {
            repository.editMessage(EditM20MessageInput(messageId, content))
                .onSuccess {
                    cancelComposerModes()
                    loadPage(replace = true)
                    _message.value = "Mensaje editado."
                }
                .onFailure {
                    _message.value = M20MessagingResilience.safeUserMessage(it)
                }
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            repository.deleteMessage(messageId)
                .onSuccess {
                    loadPage(replace = true)
                    _message.value = M20DeletedContent.PLACEHOLDER
                }
                .onFailure {
                    _message.value = M20MessagingResilience.safeUserMessage(it)
                }
        }
    }

    fun blockUser() {
        viewModelScope.launch {
            repository.blockUser(conversationId)
                .onSuccess {
                    _uiState.value = M20ThreadUiState.Blocked
                    _message.value = "Usuario bloqueado."
                }
                .onFailure {
                    _message.value = M20MessagingResilience.safeUserMessage(it)
                }
        }
    }

    fun unblockUser() {
        viewModelScope.launch {
            repository.unblockUser(conversationId)
                .onSuccess {
                    loadInitial()
                    _message.value = "Usuario desbloqueado."
                }
                .onFailure {
                    _message.value = M20MessagingResilience.safeUserMessage(it)
                }
        }
    }

    fun archiveConversation() {
        viewModelScope.launch {
            repository.archiveConversation(conversationId)
                .onSuccess {
                    _uiState.value = M20ThreadUiState.Archived
                    _message.value = "Conversación archivada."
                }
                .onFailure {
                    _message.value = M20MessagingResilience.safeUserMessage(it)
                }
        }
    }

    fun reportConversation() {
        viewModelScope.launch {
            repository.reportConversation(conversationId)
                .onSuccess { _message.value = "Reporte enviado." }
                .onFailure { _message.value = M20MessagingResilience.safeUserMessage(it) }
        }
    }

    fun reportMessage(messageId: String) {
        viewModelScope.launch {
            repository.reportMessage(messageId)
                .onSuccess { _message.value = "Reporte enviado." }
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
        com.comunidapp.app.data.model.M20MessageStatus.PENDING_LOCAL -> "Pendiente"
        com.comunidapp.app.data.model.M20MessageStatus.SENT -> "Enviado"
        com.comunidapp.app.data.model.M20MessageStatus.DELIVERED -> "Entregado"
        com.comunidapp.app.data.model.M20MessageStatus.READ -> "Leído"
        com.comunidapp.app.data.model.M20MessageStatus.FAILED -> "Fallido"
        com.comunidapp.app.data.model.M20MessageStatus.EDITED -> "Editado"
        com.comunidapp.app.data.model.M20MessageStatus.DELETED -> "Eliminado"
    }
