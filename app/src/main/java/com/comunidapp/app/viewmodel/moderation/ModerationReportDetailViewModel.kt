package com.comunidapp.app.viewmodel.moderation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.ModerationRepository
import com.comunidapp.app.data.repository.PermissionRepository
import com.comunidapp.app.domain.authorization.PermissionCode
import com.comunidapp.app.domain.moderation.ModerationPriority
import com.comunidapp.app.domain.moderation.ModerationReport
import com.comunidapp.app.domain.moderation.ModerationReportStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ModerationReportDetailUiState(
    val phase: AdministrativeScreenPhase = AdministrativeScreenPhase.Loading,
    val report: ModerationReport? = null,
    val canViewSensitive: Boolean = false,
    val canManageReports: Boolean = false,
    val canManageCases: Boolean = false,
    val visibleReporterId: String? = null,
    val message: String? = null,
    val errorMessage: String? = null,
    val submitting: Boolean = false
)

class ModerationReportDetailViewModel(
    private val reportId: String,
    private val moderationRepository: ModerationRepository = DataProvider.moderationRepository,
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val permissionRepository: PermissionRepository = DataProvider.permissionRepository,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModerationReportDetailUiState())
    val uiState: StateFlow<ModerationReportDetailUiState> = _uiState.asStateFlow()

    private var mutationLock = false

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(phase = AdministrativeScreenPhase.Loading, report = null, errorMessage = null)
            }
            val gate = AdministrativeAccessGate.evaluate(
                authRepository,
                permissionRepository,
                PermissionCode.MODERATION_VIEW,
                sensitivePermission = PermissionCode.MODERATION_VIEW_SENSITIVE,
                extra = setOf(
                    PermissionCode.MODERATION_MANAGE_REPORTS,
                    PermissionCode.MODERATION_MANAGE_CASES
                )
            )
            if (!gate.allowed) {
                _uiState.update {
                    ModerationReportDetailUiState(phase = AdministrativeScreenPhase.AccessDenied)
                }
                return@launch
            }
            when (val result = moderationRepository.getReportForStaff(reportId)) {
                is AppResult.Success -> {
                    val report = SensitiveDataPresentation.redactReporterId(
                        result.data,
                        gate.canViewSensitive
                    )
                    _uiState.update {
                        it.copy(
                            phase = AdministrativeScreenPhase.Content,
                            report = report,
                            canViewSensitive = gate.canViewSensitive,
                            canManageReports = AdministrativeAccessGate.hasExtra(
                                gate,
                                PermissionCode.MODERATION_MANAGE_REPORTS
                            ),
                            canManageCases = AdministrativeAccessGate.hasExtra(
                                gate,
                                PermissionCode.MODERATION_MANAGE_CASES
                            ),
                            visibleReporterId = SensitiveDataPresentation.reporterIdOrNull(
                                result.data,
                                gate.canViewSensitive
                            ),
                            errorMessage = null
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

    fun triage(status: ModerationReportStatus, priority: ModerationPriority? = null) {
        if (!_uiState.value.canManageReports) {
            _uiState.update { it.copy(message = "No tenés permiso para gestionar reportes.") }
            return
        }
        mutate("Reporte actualizado") {
            moderationRepository.triageReport(reportId, status, priority, clock())
        }
    }

    fun markDuplicate(duplicateOfReportId: String) {
        if (!_uiState.value.canManageReports) {
            _uiState.update { it.copy(message = "No tenés permiso para gestionar reportes.") }
            return
        }
        mutate("Marcado como duplicado") {
            moderationRepository.markReportDuplicate(reportId, duplicateOfReportId, clock())
        }
    }

    fun createCaseAndAttach(title: String) {
        if (mutationLock) return
        val userId = _uiState.value.let { if (it.canManageCases) authRepository.getCurrentUser()?.id else null }
        viewModelScope.launch {
            if (mutationLock) return@launch
            if (!_uiState.value.canManageCases) {
                _uiState.update { it.copy(message = "No tenés permiso para gestionar casos.") }
                return@launch
            }
            val actor = authRepository.getCurrentUser()?.id ?: return@launch
            mutationLock = true
            _uiState.update {
                it.copy(submitting = true, phase = AdministrativeScreenPhase.Submitting)
            }
            when (val created = moderationRepository.createCase(title, actor, clock())) {
                is AppResult.Success -> {
                    when (
                        val attached = moderationRepository.attachReportToCase(
                            reportId,
                            created.data.id,
                            clock()
                        )
                    ) {
                        is AppResult.Success -> {
                            mutationLock = false
                            _uiState.update { it.copy(message = "Caso creado y reporte adjunto") }
                            refresh()
                        }
                        is AppResult.Failure -> {
                            mutationLock = false
                            _uiState.update {
                                it.copy(
                                    submitting = false,
                                    phase = AdministrativeScreenPhase.Content,
                                    message = attached.error.userMessage
                                )
                            }
                        }
                    }
                }
                is AppResult.Failure -> {
                    mutationLock = false
                    _uiState.update {
                        it.copy(
                            submitting = false,
                            phase = AdministrativeScreenPhase.Content,
                            message = created.error.userMessage
                        )
                    }
                }
            }
        }
        // silence unused
        userId
    }

    fun attachToCase(caseId: String) {
        if (!_uiState.value.canManageCases) {
            _uiState.update { it.copy(message = "No tenés permiso para gestionar casos.") }
            return
        }
        mutate("Reporte adjunto al caso") {
            moderationRepository.attachReportToCase(reportId, caseId, clock())
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    private fun mutate(successMessage: String, block: suspend () -> AppResult<*>) {
        if (mutationLock) return
        if (_uiState.value.phase != AdministrativeScreenPhase.Content) return
        mutationLock = true
        viewModelScope.launch {
            _uiState.update {
                it.copy(submitting = true, phase = AdministrativeScreenPhase.Submitting)
            }
            when (val result = block()) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(message = successMessage, submitting = false) }
                    refresh()
                    mutationLock = false
                }
                is AppResult.Failure -> {
                    mutationLock = false
                    _uiState.update {
                        it.copy(
                            submitting = false,
                            phase = AdministrativeScreenPhase.Content,
                            message = result.error.userMessage
                        )
                    }
                }
            }
        }
    }

    companion object {
        fun factory(reportId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ModerationReportDetailViewModel(reportId) as T
            }
    }
}
