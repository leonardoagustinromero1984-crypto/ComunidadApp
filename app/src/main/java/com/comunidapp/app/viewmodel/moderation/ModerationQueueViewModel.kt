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
import com.comunidapp.app.domain.moderation.ModerationTargetType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ModerationQueueUiState(
    val phase: AdministrativeScreenPhase = AdministrativeScreenPhase.Loading,
    val reports: List<ModerationReport> = emptyList(),
    val filtered: List<ModerationReport> = emptyList(),
    val canViewSensitive: Boolean = false,
    val canManageReports: Boolean = false,
    val statusFilter: ModerationReportStatus? = null,
    val priorityFilter: ModerationPriority? = null,
    val targetFilter: ModerationTargetType? = null,
    val hasCaseFilter: Boolean? = null,
    val message: String? = null,
    val errorMessage: String? = null
)

class ModerationQueueViewModel(
    private val moderationRepository: ModerationRepository = DataProvider.moderationRepository,
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val permissionRepository: PermissionRepository = DataProvider.permissionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModerationQueueUiState())
    val uiState: StateFlow<ModerationQueueUiState> = _uiState.asStateFlow()

    private var submitting = false

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(phase = AdministrativeScreenPhase.Loading, errorMessage = null, reports = emptyList())
            }
            val gate = AdministrativeAccessGate.evaluate(
                authRepository,
                permissionRepository,
                PermissionCode.MODERATION_VIEW,
                sensitivePermission = PermissionCode.MODERATION_VIEW_SENSITIVE,
                extra = setOf(PermissionCode.MODERATION_MANAGE_REPORTS)
            )
            if (!gate.allowed) {
                _uiState.update {
                    ModerationQueueUiState(phase = AdministrativeScreenPhase.AccessDenied)
                }
                return@launch
            }
            when (val result = moderationRepository.listModerationQueue()) {
                is AppResult.Success -> {
                    val list = result.data.map {
                        SensitiveDataPresentation.redactReporterId(it, gate.canViewSensitive)
                    }
                    _uiState.update {
                        val base = it.copy(
                            phase = if (list.isEmpty()) {
                                AdministrativeScreenPhase.Empty
                            } else {
                                AdministrativeScreenPhase.Content
                            },
                            reports = list,
                            canViewSensitive = gate.canViewSensitive,
                            canManageReports = AdministrativeAccessGate.hasExtra(
                                gate,
                                PermissionCode.MODERATION_MANAGE_REPORTS
                            ),
                            errorMessage = null
                        )
                        base.copy(filtered = applyFilters(base))
                    }
                }
                is AppResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            phase = AdministrativeScreenPhase.Error,
                            reports = emptyList(),
                            filtered = emptyList(),
                            errorMessage = result.error.userMessage
                        )
                    }
                }
            }
        }
    }

    fun setStatusFilter(status: ModerationReportStatus?) {
        _uiState.update {
            val next = it.copy(statusFilter = status)
            next.copy(filtered = applyFilters(next))
        }
    }

    fun setPriorityFilter(priority: ModerationPriority?) {
        _uiState.update {
            val next = it.copy(priorityFilter = priority)
            next.copy(filtered = applyFilters(next))
        }
    }

    fun setTargetFilter(target: ModerationTargetType?) {
        _uiState.update {
            val next = it.copy(targetFilter = target)
            next.copy(filtered = applyFilters(next))
        }
    }

    fun setHasCaseFilter(hasCase: Boolean?) {
        _uiState.update {
            val next = it.copy(hasCaseFilter = hasCase)
            next.copy(filtered = applyFilters(next))
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    private fun applyFilters(state: ModerationQueueUiState): List<ModerationReport> =
        state.reports.filter { r ->
            (state.statusFilter == null || r.status == state.statusFilter) &&
                (state.priorityFilter == null || r.priority == state.priorityFilter) &&
                (state.targetFilter == null || r.target.type == state.targetFilter) &&
                (state.hasCaseFilter == null || (r.caseId != null) == state.hasCaseFilter)
        }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ModerationQueueViewModel() as T
        }
    }
}
