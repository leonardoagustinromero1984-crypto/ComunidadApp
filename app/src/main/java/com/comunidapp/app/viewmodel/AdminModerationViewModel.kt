package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.model.ContentReport
import com.comunidapp.app.data.model.ReportStatus
import com.comunidapp.app.data.model.ReportTargetType
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.ModerationRepository
import com.comunidapp.app.data.repository.PermissionRepository
import com.comunidapp.app.domain.authorization.PermissionCode
import com.comunidapp.app.domain.moderation.ModerationReport
import com.comunidapp.app.domain.moderation.ModerationReportStatus
import com.comunidapp.app.domain.moderation.ModerationTargetType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Legacy bridge: maps M04 ModerationRepository queue to ContentReport for AdminModerationScreen.
 * Prefer ModerationQueueViewModel / ModerationQueueScreen (ADMIN_MODERATION route).
 */
data class AdminModerationUiState(
    val accessAllowed: Boolean = false,
    val accessChecked: Boolean = false,
    val reports: List<ContentReport> = emptyList(),
    val message: String? = null
)

class AdminModerationViewModel(
    private val moderationRepository: ModerationRepository = DataProvider.moderationRepository,
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val permissionRepository: PermissionRepository = DataProvider.permissionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminModerationUiState())
    val uiState: StateFlow<AdminModerationUiState> = _uiState.asStateFlow()

    private var submitting = false

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val user = authRepository.getCurrentUser()
            if (user == null) {
                _uiState.update {
                    AdminModerationUiState(accessAllowed = false, accessChecked = true)
                }
                return@launch
            }
            permissionRepository.refresh(user.id)
            val allowed = permissionRepository.hasPermission(user.id, PermissionCode.MODERATION_VIEW)
            if (!allowed) {
                _uiState.update {
                    AdminModerationUiState(accessAllowed = false, accessChecked = true)
                }
                return@launch
            }
            when (val result = moderationRepository.listModerationQueue()) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            accessAllowed = true,
                            accessChecked = true,
                            reports = result.data.map { r -> r.toLegacyContentReport() }
                        )
                    }
                }
                is AppResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            accessAllowed = true,
                            accessChecked = true,
                            reports = emptyList(),
                            message = result.error.userMessage
                        )
                    }
                }
            }
        }
    }

    fun dismissReport(id: String) = triage(id, ModerationReportStatus.DISMISSED, "Reporte desestimado")

    fun actionReport(id: String) =
        triage(id, ModerationReportStatus.ACTION_REQUIRED, "Acción aplicada al reporte")

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    private fun triage(id: String, status: ModerationReportStatus, successMessage: String) {
        if (submitting) return
        viewModelScope.launch {
            if (submitting) return@launch
            val user = authRepository.getCurrentUser() ?: return@launch
            val allowed = permissionRepository.hasPermission(
                user.id,
                PermissionCode.MODERATION_MANAGE_REPORTS
            )
            if (!allowed) {
                _uiState.update { it.copy(message = "No tenés permiso para moderar.") }
                return@launch
            }
            submitting = true
            when (
                val result = moderationRepository.triageReport(
                    id,
                    status,
                    null,
                    System.currentTimeMillis()
                )
            ) {
                is AppResult.Success -> {
                    submitting = false
                    _uiState.update { it.copy(message = successMessage) }
                    refresh()
                }
                is AppResult.Failure -> {
                    submitting = false
                    _uiState.update { it.copy(message = result.error.userMessage) }
                }
            }
        }
    }

    private fun ModerationReport.toLegacyContentReport(): ContentReport {
        val targetType = when (target.type) {
            ModerationTargetType.USER_PROFILE -> ReportTargetType.USER
            ModerationTargetType.COMMENT -> ReportTargetType.COMMENT
            else -> ReportTargetType.POST
        }
        val legacyStatus = when (status) {
            ModerationReportStatus.OPEN -> ReportStatus.OPEN
            ModerationReportStatus.DISMISSED -> ReportStatus.DISMISSED
            ModerationReportStatus.ACTION_REQUIRED,
            ModerationReportStatus.RESOLVED -> ReportStatus.ACTIONED
            else -> ReportStatus.REVIEWED
        }
        return ContentReport(
            id = id,
            reporterId = reporterId,
            targetType = targetType,
            targetId = target.targetId,
            reason = reasonCode,
            details = description,
            status = legacyStatus,
            createdAt = createdAtEpochMs
        )
    }
}
