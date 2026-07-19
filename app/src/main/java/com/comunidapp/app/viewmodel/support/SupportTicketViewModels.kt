package com.comunidapp.app.viewmodel.support

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.SupportRepository
import com.comunidapp.app.domain.files.FileAssetOwner
import com.comunidapp.app.domain.files.FileAssetPurpose
import com.comunidapp.app.domain.files.FileAssetVisibility
import com.comunidapp.app.domain.files.FileResourceRef
import com.comunidapp.app.domain.files.FileResourceType
import com.comunidapp.app.domain.files.FileUiErrorMapper
import com.comunidapp.app.domain.files.FileUploadRequest
import com.comunidapp.app.domain.support.SupportCategory
import com.comunidapp.app.domain.support.SupportMessage
import com.comunidapp.app.domain.support.SupportTicket
import com.comunidapp.app.domain.support.SupportTicketStatus
import com.comunidapp.app.domain.support.SupportValidators
import com.comunidapp.app.viewmodel.moderation.AdministrativeScreenPhase
import com.comunidapp.app.viewmodel.moderation.SensitiveDataPresentation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MySupportTicketsUiState(
    val phase: AdministrativeScreenPhase = AdministrativeScreenPhase.Loading,
    val tickets: List<SupportTicket> = emptyList(),
    val message: String? = null,
    val errorMessage: String? = null
)

class MySupportTicketsViewModel(
    private val supportRepository: SupportRepository = DataProvider.supportRepository,
    private val authRepository: AuthRepository = AuthProvider.repository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MySupportTicketsUiState())
    val uiState: StateFlow<MySupportTicketsUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val user = authRepository.getCurrentUser()
            if (user == null) {
                _uiState.update {
                    MySupportTicketsUiState(phase = AdministrativeScreenPhase.AccessDenied)
                }
                return@launch
            }
            _uiState.update { it.copy(phase = AdministrativeScreenPhase.Loading) }
            when (val result = supportRepository.getMyTickets(user.id)) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            phase = if (result.data.isEmpty()) {
                                AdministrativeScreenPhase.Empty
                            } else {
                                AdministrativeScreenPhase.Content
                            },
                            tickets = result.data
                        )
                    }
                }
                is AppResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            phase = AdministrativeScreenPhase.Error,
                            errorMessage = result.error.userMessage
                        )
                    }
                }
            }
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MySupportTicketsViewModel() as T
        }
    }
}

data class CreateSupportTicketUiState(
    val phase: AdministrativeScreenPhase = AdministrativeScreenPhase.Content,
    val category: SupportCategory = SupportCategory.OTHER,
    val subject: String = "",
    val description: String = "",
    val showSensitiveWarning: Boolean = false,
    val createdTicketId: String? = null,
    val message: String? = null
)

class CreateSupportTicketViewModel(
    private val supportRepository: SupportRepository = DataProvider.supportRepository,
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateSupportTicketUiState())
    val uiState: StateFlow<CreateSupportTicketUiState> = _uiState.asStateFlow()
    private var lock = false

    fun setCategory(category: SupportCategory) {
        _uiState.update {
            it.copy(
                category = category,
                showSensitiveWarning = SupportValidators.isSensitiveCategory(category)
            )
        }
    }

    fun setSubject(v: String) = _uiState.update { it.copy(subject = v) }
    fun setDescription(v: String) = _uiState.update { it.copy(description = v) }

    fun submit() {
        if (lock) return
        viewModelScope.launch {
            if (lock) return@launch
            val user = authRepository.getCurrentUser()
            if (user == null) {
                _uiState.update {
                    it.copy(phase = AdministrativeScreenPhase.AccessDenied, message = "Iniciá sesión.")
                }
                return@launch
            }
            lock = true
            _uiState.update { it.copy(phase = AdministrativeScreenPhase.Submitting) }
            val s = _uiState.value
            when (
                val result = supportRepository.createTicket(
                    user.id, s.category, s.subject, s.description, clock()
                )
            ) {
                is AppResult.Success -> {
                    lock = false
                    _uiState.update {
                        it.copy(
                            phase = AdministrativeScreenPhase.Content,
                            createdTicketId = result.data.id,
                            message = "Ticket creado"
                        )
                    }
                }
                is AppResult.Failure -> {
                    lock = false
                    _uiState.update {
                        it.copy(
                            phase = AdministrativeScreenPhase.Content,
                            message = result.error.userMessage
                        )
                    }
                }
            }
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                CreateSupportTicketViewModel() as T
        }
    }
}

data class SupportTicketDetailUiState(
    val phase: AdministrativeScreenPhase = AdministrativeScreenPhase.Loading,
    val ticket: SupportTicket? = null,
    val messages: List<SupportMessage> = emptyList(),
    val attachmentAssetIds: List<String> = emptyList(),
    val draft: String = "",
    val message: String? = null,
    val errorMessage: String? = null
)

class SupportTicketDetailViewModel(
    private val ticketId: String,
    private val supportRepository: SupportRepository = DataProvider.supportRepository,
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val _uiState = MutableStateFlow(SupportTicketDetailUiState())
    val uiState: StateFlow<SupportTicketDetailUiState> = _uiState.asStateFlow()
    private var lock = false

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val user = authRepository.getCurrentUser()
            if (user == null) {
                _uiState.update {
                    SupportTicketDetailUiState(phase = AdministrativeScreenPhase.AccessDenied)
                }
                return@launch
            }
            _uiState.update { it.copy(phase = AdministrativeScreenPhase.Loading) }
            when (val result = supportRepository.getTicketDetail(ticketId)) {
                is AppResult.Success -> {
                    val visible = SensitiveDataPresentation.messagesForRequester(result.data.messages)
                    _uiState.update {
                        it.copy(
                            phase = AdministrativeScreenPhase.Content,
                            ticket = result.data.ticket,
                            messages = visible
                        )
                    }
                }
                is AppResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            phase = AdministrativeScreenPhase.Error,
                            errorMessage = result.error.userMessage
                        )
                    }
                }
            }
        }
    }

    fun onDraftChange(v: String) = _uiState.update { it.copy(draft = v) }

    fun sendMessage() {
        if (lock) return
        viewModelScope.launch {
            if (lock) return@launch
            val user = authRepository.getCurrentUser() ?: return@launch
            val body = _uiState.value.draft
            lock = true
            _uiState.update { it.copy(phase = AdministrativeScreenPhase.Submitting) }
            when (
                val result = supportRepository.addRequesterMessage(ticketId, user.id, body, clock())
            ) {
                is AppResult.Success -> {
                    lock = false
                    _uiState.update { it.copy(draft = "", message = "Mensaje enviado") }
                    refresh()
                }
                is AppResult.Failure -> {
                    lock = false
                    _uiState.update {
                        it.copy(
                            phase = AdministrativeScreenPhase.Content,
                            message = result.error.userMessage
                        )
                    }
                }
            }
        }
    }

    fun attachFile(uri: android.net.Uri) {
        viewModelScope.launch {
            val user = authRepository.getCurrentUser() ?: return@launch
            when (val upload = DataProvider.fileUploadCoordinator.startUpload(
                uriString = uri.toString(),
                request = FileUploadRequest(
                    purpose = FileAssetPurpose.SUPPORT_ATTACHMENT,
                    owner = FileAssetOwner.User(user.id),
                    resourceRef = FileResourceRef(FileResourceType.SUPPORT_TICKET, ticketId),
                    originalFilename = "adjunto.pdf",
                    declaredMimeType = "application/pdf",
                    sizeBytes = 1L,
                    requestedVisibility = FileAssetVisibility.RESOURCE_PARTICIPANTS
                ),
                actorUserId = user.id
            )) {
                is AppResult.Success -> _uiState.update {
                    it.copy(attachmentAssetIds = it.attachmentAssetIds + upload.data.assetId)
                }
                is AppResult.Failure -> _uiState.update {
                    it.copy(message = FileUiErrorMapper.message(upload.error))
                }
            }
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    companion object {
        fun factory(ticketId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SupportTicketDetailViewModel(ticketId) as T
            }
    }
}
