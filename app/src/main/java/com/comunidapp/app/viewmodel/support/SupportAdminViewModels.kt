package com.comunidapp.app.viewmodel.support

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.PermissionRepository
import com.comunidapp.app.data.repository.SupportRepository
import com.comunidapp.app.domain.authorization.PermissionCode
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
import com.comunidapp.app.viewmodel.moderation.AdministrativeAccessGate
import com.comunidapp.app.viewmodel.moderation.AdministrativeScreenPhase
import com.comunidapp.app.viewmodel.moderation.SensitiveDataPresentation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SupportAdminQueueUiState(
    val phase: AdministrativeScreenPhase = AdministrativeScreenPhase.Loading,
    val tickets: List<SupportTicket> = emptyList(),
    val canManage: Boolean = false,
    val canViewSensitive: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null
)

class SupportAdminQueueViewModel(
    private val supportRepository: SupportRepository = DataProvider.supportRepository,
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val permissionRepository: PermissionRepository = DataProvider.permissionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SupportAdminQueueUiState())
    val uiState: StateFlow<SupportAdminQueueUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update {
                SupportAdminQueueUiState(phase = AdministrativeScreenPhase.Loading)
            }
            val gate = AdministrativeAccessGate.evaluate(
                authRepository,
                permissionRepository,
                PermissionCode.SUPPORT_VIEW,
                sensitivePermission = PermissionCode.SUPPORT_VIEW_SENSITIVE,
                extra = setOf(PermissionCode.SUPPORT_MANAGE)
            )
            if (!gate.allowed) {
                _uiState.update {
                    SupportAdminQueueUiState(phase = AdministrativeScreenPhase.AccessDenied)
                }
                return@launch
            }
            when (val result = supportRepository.listSupportQueue()) {
                is AppResult.Success -> {
                    val canSensitive = gate.canViewSensitive
                    val tickets = if (canSensitive) {
                        result.data
                    } else {
                        result.data.filter {
                            it.category != SupportCategory.PRIVACY &&
                                it.category != SupportCategory.SAFETY
                        }
                    }
                    _uiState.update {
                        it.copy(
                            phase = if (tickets.isEmpty()) {
                                AdministrativeScreenPhase.Empty
                            } else {
                                AdministrativeScreenPhase.Content
                            },
                            tickets = tickets,
                            canManage = AdministrativeAccessGate.hasExtra(
                                gate,
                                PermissionCode.SUPPORT_MANAGE
                            ),
                            canViewSensitive = canSensitive
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
                SupportAdminQueueViewModel() as T
        }
    }
}

data class SupportTicketAdminDetailUiState(
    val phase: AdministrativeScreenPhase = AdministrativeScreenPhase.Loading,
    val ticket: SupportTicket? = null,
    val messages: List<SupportMessage> = emptyList(),
    val canManage: Boolean = false,
    val canViewSensitive: Boolean = false,
    val draft: String = "",
    val internalDraft: String = "",
    val requesterVisibleAttachmentAssetIds: List<String> = emptyList(),
    val internalAttachmentAssetIds: List<String> = emptyList(),
    val confirmClose: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null
)

class SupportTicketAdminDetailViewModel(
    private val ticketId: String,
    private val supportRepository: SupportRepository = DataProvider.supportRepository,
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val permissionRepository: PermissionRepository = DataProvider.permissionRepository,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val _uiState = MutableStateFlow(SupportTicketAdminDetailUiState())
    val uiState: StateFlow<SupportTicketAdminDetailUiState> = _uiState.asStateFlow()
    private var lock = false

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update {
                SupportTicketAdminDetailUiState(phase = AdministrativeScreenPhase.Loading)
            }
            val gate = AdministrativeAccessGate.evaluate(
                authRepository,
                permissionRepository,
                PermissionCode.SUPPORT_VIEW,
                sensitivePermission = PermissionCode.SUPPORT_VIEW_SENSITIVE,
                extra = setOf(PermissionCode.SUPPORT_MANAGE)
            )
            if (!gate.allowed) {
                _uiState.update {
                    SupportTicketAdminDetailUiState(phase = AdministrativeScreenPhase.AccessDenied)
                }
                return@launch
            }
            when (val result = supportRepository.getTicketDetail(ticketId)) {
                is AppResult.Success -> {
                    val ticket = result.data.ticket
                    if ((ticket.category == SupportCategory.PRIVACY ||
                            ticket.category == SupportCategory.SAFETY) &&
                        !gate.canViewSensitive
                    ) {
                        _uiState.update {
                            SupportTicketAdminDetailUiState(
                                phase = AdministrativeScreenPhase.AccessDenied
                            )
                        }
                        return@launch
                    }
                    val msgs = SensitiveDataPresentation.messagesForStaff(
                        result.data.messages,
                        includeInternal = gate.canViewSensitive ||
                            AdministrativeAccessGate.hasExtra(gate, PermissionCode.SUPPORT_MANAGE)
                    )
                    _uiState.update {
                        it.copy(
                            phase = AdministrativeScreenPhase.Content,
                            ticket = ticket,
                            messages = msgs,
                            canManage = AdministrativeAccessGate.hasExtra(
                                gate,
                                PermissionCode.SUPPORT_MANAGE
                            ),
                            canViewSensitive = gate.canViewSensitive
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

    fun assignToMe() {
        mutate {
            val actor = authRepository.getCurrentUser()?.id ?: return@mutate failAuth()
            supportRepository.assignTicket(ticketId, actor, clock())
        }
    }

    fun changeStatus(status: SupportTicketStatus, closeReason: String? = null) {
        if (status == SupportTicketStatus.CLOSED && !_uiState.value.confirmClose) {
            _uiState.update { it.copy(confirmClose = true, message = "Confirmá el cierre.") }
            return
        }
        mutate {
            supportRepository.changeTicketStatus(ticketId, status, closeReason, clock())
        }
        _uiState.update { it.copy(confirmClose = false) }
    }

    fun confirmClose(confirm: Boolean) {
        _uiState.update { it.copy(confirmClose = confirm) }
    }

    fun onDraftChange(v: String) = _uiState.update { it.copy(draft = v) }
    fun onInternalDraftChange(v: String) = _uiState.update { it.copy(internalDraft = v) }

    fun sendVisibleMessage() {
        mutate {
            val actor = authRepository.getCurrentUser()?.id ?: return@mutate failAuth()
            supportRepository.addRequesterMessage(ticketId, actor, _uiState.value.draft, clock())
        }
        _uiState.update { it.copy(draft = "") }
    }

    fun sendInternalNote() {
        mutate {
            val actor = authRepository.getCurrentUser()?.id ?: return@mutate failAuth()
            supportRepository.addInternalMessage(
                ticketId, actor, _uiState.value.internalDraft, clock()
            )
        }
        _uiState.update { it.copy(internalDraft = "") }
    }

    fun attachFile(uri: android.net.Uri, internal: Boolean) {
        if (!_uiState.value.canManage || (internal && !_uiState.value.canViewSensitive)) {
            _uiState.update { it.copy(message = "No tenés permiso para adjuntar este archivo.") }
            return
        }
        viewModelScope.launch {
            val actor = authRepository.getCurrentUser()?.id ?: return@launch
            when (val upload = DataProvider.fileUploadCoordinator.startUpload(
                uriString = uri.toString(),
                request = FileUploadRequest(
                    purpose = FileAssetPurpose.SUPPORT_ATTACHMENT,
                    owner = FileAssetOwner.Platform(),
                    resourceRef = FileResourceRef(FileResourceType.SUPPORT_TICKET, ticketId),
                    originalFilename = "adjunto.pdf",
                    declaredMimeType = "application/pdf",
                    sizeBytes = 1L,
                    requestedVisibility = if (internal) {
                        FileAssetVisibility.AUTHORIZED_STAFF
                    } else {
                        FileAssetVisibility.RESOURCE_PARTICIPANTS
                    }
                ),
                actorUserId = actor
            )) {
                is AppResult.Success -> _uiState.update {
                    if (internal) {
                        it.copy(
                            internalAttachmentAssetIds =
                                it.internalAttachmentAssetIds + upload.data.assetId
                        )
                    } else {
                        it.copy(
                            requesterVisibleAttachmentAssetIds =
                                it.requesterVisibleAttachmentAssetIds + upload.data.assetId
                        )
                    }
                }
                is AppResult.Failure -> _uiState.update {
                    it.copy(message = FileUiErrorMapper.message(upload.error))
                }
            }
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    private fun failAuth(): AppResult.Failure =
        AppResult.Failure(
            com.comunidapp.app.core.result.AppError(
                kind = com.comunidapp.app.core.result.AppErrorKind.UNAUTHORIZED,
                userMessage = "Tenés que iniciar sesión.",
                technicalMessage = "NO_SESSION",
                code = "UNAUTHORIZED"
            )
        )

    private fun mutate(block: suspend () -> AppResult<*>) {
        if (lock) return
        viewModelScope.launch {
            if (lock) return@launch
            if (!_uiState.value.canManage) {
                _uiState.update { it.copy(message = "No tenés permiso de gestión.") }
                return@launch
            }
            lock = true
            _uiState.update { it.copy(phase = AdministrativeScreenPhase.Submitting) }
            when (val result = block()) {
                is AppResult.Success -> {
                    lock = false
                    _uiState.update { it.copy(message = "Actualizado") }
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

    companion object {
        fun factory(ticketId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SupportTicketAdminDetailViewModel(ticketId) as T
            }
    }
}
