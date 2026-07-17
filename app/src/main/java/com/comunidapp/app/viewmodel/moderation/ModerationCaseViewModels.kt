package com.comunidapp.app.viewmodel.moderation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.ModerationCaseDetail
import com.comunidapp.app.data.repository.ModerationRepository
import com.comunidapp.app.data.repository.PermissionRepository
import com.comunidapp.app.domain.authorization.PermissionCode
import com.comunidapp.app.domain.files.FileAssetOwner
import com.comunidapp.app.domain.files.FileAssetPurpose
import com.comunidapp.app.domain.files.FileAssetVisibility
import com.comunidapp.app.domain.files.FileResourceRef
import com.comunidapp.app.domain.files.FileResourceType
import com.comunidapp.app.domain.files.FileUiErrorMapper
import com.comunidapp.app.domain.files.FileUploadRequest
import com.comunidapp.app.domain.moderation.ModerationActionType
import com.comunidapp.app.domain.moderation.ModerationCase
import com.comunidapp.app.domain.moderation.ModerationCaseStatus
import com.comunidapp.app.domain.moderation.ModerationTargetRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ModerationCaseQueueUiState(
    val phase: AdministrativeScreenPhase = AdministrativeScreenPhase.Loading,
    val cases: List<ModerationCase> = emptyList(),
    val canManageCases: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null
)

class ModerationCaseQueueViewModel(
    private val moderationRepository: ModerationRepository = DataProvider.moderationRepository,
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val permissionRepository: PermissionRepository = DataProvider.permissionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModerationCaseQueueUiState())
    val uiState: StateFlow<ModerationCaseQueueUiState> = _uiState.asStateFlow()
    private var createLock = false

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update {
                ModerationCaseQueueUiState(phase = AdministrativeScreenPhase.Loading)
            }
            val gate = AdministrativeAccessGate.evaluate(
                authRepository,
                permissionRepository,
                PermissionCode.MODERATION_VIEW,
                extra = setOf(PermissionCode.MODERATION_MANAGE_CASES)
            )
            if (!gate.allowed) {
                _uiState.update {
                    ModerationCaseQueueUiState(phase = AdministrativeScreenPhase.AccessDenied)
                }
                return@launch
            }
            when (val result = moderationRepository.listCases()) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            phase = if (result.data.isEmpty()) {
                                AdministrativeScreenPhase.Empty
                            } else {
                                AdministrativeScreenPhase.Content
                            },
                            cases = result.data,
                            canManageCases = AdministrativeAccessGate.hasExtra(
                                gate,
                                PermissionCode.MODERATION_MANAGE_CASES
                            )
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

    fun createCase(title: String) {
        if (createLock) return
        if (_uiState.value.phase != AdministrativeScreenPhase.Content &&
            _uiState.value.phase != AdministrativeScreenPhase.Empty
        ) {
            return
        }
        if (!_uiState.value.canManageCases) {
            _uiState.update { it.copy(message = "No tenés permiso.") }
            return
        }
        createLock = true
        viewModelScope.launch {
            try {
                val actor = authRepository.getCurrentUser()?.id ?: return@launch
                _uiState.update { it.copy(phase = AdministrativeScreenPhase.Submitting) }
                when (
                    val result = moderationRepository.createCase(
                        title,
                        actor,
                        System.currentTimeMillis()
                    )
                ) {
                    is AppResult.Success -> {
                        _uiState.update { it.copy(message = "Caso creado") }
                        refresh()
                    }
                    is AppResult.Failure -> {
                        _uiState.update {
                            it.copy(
                                phase = AdministrativeScreenPhase.Content,
                                message = result.error.userMessage
                            )
                        }
                    }
                }
            } finally {
                createLock = false
            }
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ModerationCaseQueueViewModel() as T
        }
    }
}

data class ModerationCaseDetailUiState(
    val phase: AdministrativeScreenPhase = AdministrativeScreenPhase.Loading,
    val detail: ModerationCaseDetail? = null,
    val canViewSensitive: Boolean = false,
    val canManageCases: Boolean = false,
    val canApplyActions: Boolean = false,
    val evidenceAssetIds: List<String> = emptyList(),
    val confirmApplyAction: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null
)

class ModerationCaseDetailViewModel(
    private val caseId: String,
    private val moderationRepository: ModerationRepository = DataProvider.moderationRepository,
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val permissionRepository: PermissionRepository = DataProvider.permissionRepository,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModerationCaseDetailUiState())
    val uiState: StateFlow<ModerationCaseDetailUiState> = _uiState.asStateFlow()
    private var lock = false

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(phase = AdministrativeScreenPhase.Loading, detail = null)
            }
            val gate = AdministrativeAccessGate.evaluate(
                authRepository,
                permissionRepository,
                PermissionCode.MODERATION_VIEW,
                sensitivePermission = PermissionCode.MODERATION_VIEW_SENSITIVE,
                extra = setOf(
                    PermissionCode.MODERATION_MANAGE_CASES,
                    PermissionCode.MODERATION_APPLY_ACTIONS
                )
            )
            if (!gate.allowed) {
                _uiState.update {
                    ModerationCaseDetailUiState(phase = AdministrativeScreenPhase.AccessDenied)
                }
                return@launch
            }
            when (val result = moderationRepository.getCase(caseId)) {
                is AppResult.Success -> {
                    val notes = SensitiveDataPresentation.notesForStaff(
                        result.data.notes,
                        gate.canViewSensitive
                    )
                    val detail = result.data.copy(
                        notes = notes,
                        reports = result.data.reports.map {
                            SensitiveDataPresentation.redactReporterId(it, gate.canViewSensitive)
                        }
                    )
                    _uiState.update {
                        it.copy(
                            phase = AdministrativeScreenPhase.Content,
                            detail = detail,
                            canViewSensitive = gate.canViewSensitive,
                            canManageCases = AdministrativeAccessGate.hasExtra(
                                gate,
                                PermissionCode.MODERATION_MANAGE_CASES
                            ),
                            canApplyActions = AdministrativeAccessGate.hasExtra(
                                gate,
                                PermissionCode.MODERATION_APPLY_ACTIONS
                            )
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
        if (!_uiState.value.canManageCases) {
            _uiState.update { it.copy(message = "No tenés permiso para gestionar casos.") }
            return
        }
        mutate {
            val actor = authRepository.getCurrentUser()?.id ?: return@mutate AppResult.Failure(
                com.comunidapp.app.core.result.AppError(
                    kind = com.comunidapp.app.core.result.AppErrorKind.UNAUTHORIZED,
                    userMessage = "Tenés que iniciar sesión.",
                    technicalMessage = "NO_SESSION",
                    code = "UNAUTHORIZED"
                )
            )
            moderationRepository.assignCase(caseId, actor, clock())
        }
    }

    fun changeStatus(status: ModerationCaseStatus, closeReasonCode: String? = null) {
        if (!_uiState.value.canManageCases) {
            _uiState.update { it.copy(message = "No tenés permiso para gestionar casos.") }
            return
        }
        mutate {
            moderationRepository.changeCaseStatus(caseId, status, closeReasonCode, clock())
        }
    }

    fun addNote(body: String) {
        if (!_uiState.value.canManageCases) {
            _uiState.update { it.copy(message = "No tenés permiso para gestionar casos.") }
            return
        }
        mutate {
            val actor = authRepository.getCurrentUser()?.id ?: return@mutate AppResult.Failure(
                com.comunidapp.app.core.result.AppError(
                    kind = com.comunidapp.app.core.result.AppErrorKind.UNAUTHORIZED,
                    userMessage = "Tenés que iniciar sesión.",
                    technicalMessage = "NO_SESSION",
                    code = "UNAUTHORIZED"
                )
            )
            moderationRepository.addInternalNote(caseId, body, actor, clock())
        }
    }

    fun attachEvidence(uri: android.net.Uri) {
        if (!_uiState.value.canManageCases || !_uiState.value.canViewSensitive) {
            _uiState.update { it.copy(message = "No tenés permiso para adjuntar evidencia.") }
            return
        }
        viewModelScope.launch {
            val actor = authRepository.getCurrentUser()?.id ?: return@launch
            when (val upload = DataProvider.fileUploadCoordinator.startUpload(
                uriString = uri.toString(),
                request = FileUploadRequest(
                    purpose = FileAssetPurpose.MODERATION_EVIDENCE,
                    owner = FileAssetOwner.Platform(),
                    resourceRef = FileResourceRef(FileResourceType.MODERATION_CASE, caseId),
                    originalFilename = "evidencia.pdf",
                    declaredMimeType = "application/pdf",
                    sizeBytes = 1L,
                    requestedVisibility = FileAssetVisibility.AUTHORIZED_STAFF
                ),
                actorUserId = actor
            )) {
                is AppResult.Success -> _uiState.update {
                    it.copy(evidenceAssetIds = it.evidenceAssetIds + upload.data.assetId)
                }
                is AppResult.Failure -> _uiState.update {
                    it.copy(message = FileUiErrorMapper.message(upload.error))
                }
            }
        }
    }

    fun requestApplyActionConfirmation(confirm: Boolean) {
        _uiState.update { it.copy(confirmApplyAction = confirm) }
    }

    fun applyAction(
        target: ModerationTargetRef,
        actionType: ModerationActionType,
        reasonCode: String,
        reasonDetail: String?,
        expiresAtEpochMs: Long?,
        confirmed: Boolean
    ) {
        if (!confirmed) {
            _uiState.update { it.copy(confirmApplyAction = true, message = "Confirmá la medida.") }
            return
        }
        if (!_uiState.value.canApplyActions) {
            _uiState.update { it.copy(message = "No tenés permiso para aplicar medidas.") }
            return
        }
        mutate {
            val actor = authRepository.getCurrentUser()?.id ?: return@mutate AppResult.Failure(
                com.comunidapp.app.core.result.AppError(
                    kind = com.comunidapp.app.core.result.AppErrorKind.UNAUTHORIZED,
                    userMessage = "Tenés que iniciar sesión.",
                    technicalMessage = "NO_SESSION",
                    code = "UNAUTHORIZED"
                )
            )
            moderationRepository.recordAction(
                caseId, target, actionType, reasonCode, reasonDetail, actor, clock(), expiresAtEpochMs
            )
        }
        _uiState.update { it.copy(confirmApplyAction = false) }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    private fun mutate(block: suspend () -> AppResult<*>) {
        if (lock) return
        if (_uiState.value.phase != AdministrativeScreenPhase.Content) return
        viewModelScope.launch {
            if (lock) return@launch
            lock = true
            _uiState.update { it.copy(phase = AdministrativeScreenPhase.Submitting) }
            when (val result = block()) {
                is AppResult.Success -> {
                    lock = false
                    _uiState.update { it.copy(message = "Operación realizada") }
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
        fun factory(caseId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ModerationCaseDetailViewModel(caseId) as T
            }
    }
}
