package com.comunidapp.app.viewmodel.moderation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AdministrativeAuditEvent
import com.comunidapp.app.data.repository.AdministrativeAuditRepository
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.PermissionRepository
import com.comunidapp.app.domain.authorization.PermissionCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdministrativeAuditUiState(
    val phase: AdministrativeScreenPhase = AdministrativeScreenPhase.Loading,
    val events: List<AdministrativeAuditEvent> = emptyList(),
    val actorFilter: String = "",
    val entityFilter: String = "",
    val actionFilter: String = "",
    val filtered: List<AdministrativeAuditEvent> = emptyList(),
    val errorMessage: String? = null
)

class AdministrativeAuditViewModel(
    private val auditRepository: AdministrativeAuditRepository =
        DataProvider.administrativeAuditRepository,
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val permissionRepository: PermissionRepository = DataProvider.permissionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdministrativeAuditUiState())
    val uiState: StateFlow<AdministrativeAuditUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val gate = AdministrativeAccessGate.evaluate(
                authRepository,
                permissionRepository,
                PermissionCode.AUDIT_VIEW
            )
            if (!gate.allowed) {
                _uiState.update {
                    AdministrativeAuditUiState(phase = AdministrativeScreenPhase.AccessDenied)
                }
                return@launch
            }
            _uiState.update { it.copy(phase = AdministrativeScreenPhase.Loading, events = emptyList()) }
            when (val result = auditRepository.listAdministrativeEvents()) {
                is AppResult.Success -> {
                    _uiState.update {
                        val base = it.copy(
                            phase = if (result.data.isEmpty()) {
                                AdministrativeScreenPhase.Empty
                            } else {
                                AdministrativeScreenPhase.Content
                            },
                            events = result.data
                        )
                        base.copy(filtered = applyFilters(base))
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

    fun setActorFilter(v: String) {
        _uiState.update {
            val next = it.copy(actorFilter = v)
            next.copy(filtered = applyFilters(next))
        }
    }

    fun setEntityFilter(v: String) {
        _uiState.update {
            val next = it.copy(entityFilter = v)
            next.copy(filtered = applyFilters(next))
        }
    }

    fun setActionFilter(v: String) {
        _uiState.update {
            val next = it.copy(actionFilter = v)
            next.copy(filtered = applyFilters(next))
        }
    }

    private fun applyFilters(state: AdministrativeAuditUiState): List<AdministrativeAuditEvent> =
        state.events.filter { e ->
            (state.actorFilter.isBlank() || e.actorUserId.contains(state.actorFilter, true)) &&
                (state.entityFilter.isBlank() || e.resourceType.contains(state.entityFilter, true)) &&
                (state.actionFilter.isBlank() || e.action.contains(state.actionFilter, true))
        }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AdministrativeAuditViewModel() as T
        }
    }
}
