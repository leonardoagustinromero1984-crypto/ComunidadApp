package com.comunidapp.app.viewmodel.observability

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.PermissionRepository
import com.comunidapp.app.data.repository.RetentionRepository
import com.comunidapp.app.domain.authorization.PermissionCode
import com.comunidapp.app.domain.authorization.RolePermissionMatrix
import com.comunidapp.app.domain.authorization.PlatformRoleCode
import com.comunidapp.app.domain.observability.ObservabilityPermissionSectionInfo
import com.comunidapp.app.domain.observability.ObservabilityRetentionPolicyRecord
import com.comunidapp.app.domain.observability.ObservabilityRetentionRun
import com.comunidapp.app.domain.observability.ObservabilitySensitivity
import com.comunidapp.app.domain.observability.RetentionPreviewResult
import com.comunidapp.app.domain.observability.authorization.ObservabilityAuthorizationContext
import com.comunidapp.app.domain.observability.authorization.ObservabilityPermission
import com.comunidapp.app.domain.observability.authorization.ObservabilityPermissionsResolver
import com.comunidapp.app.domain.observability.authorization.ObservabilityRequestedAction
import com.comunidapp.app.domain.observability.correlation.SequentialCorrelationIdGenerator
import com.comunidapp.app.viewmodel.moderation.AdministrativeAccessGate
import com.comunidapp.app.viewmodel.moderation.AdministrativeScreenPhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ObservabilityRetentionUiState(
    val phase: AdministrativeScreenPhase = AdministrativeScreenPhase.Loading,
    val policies: List<ObservabilityRetentionPolicyRecord> = emptyList(),
    val runs: List<ObservabilityRetentionRun> = emptyList(),
    val preview: RetentionPreviewResult? = null,
    val selectedPolicyId: String? = null,
    val confirmExecute: Boolean = false,
    val lastCorrelationId: String? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

class ObservabilityRetentionViewModel(
    private val repo: RetentionRepository = DataProvider.retentionRepository,
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val permissionRepository: PermissionRepository = DataProvider.permissionRepository,
    private val correlationIds: SequentialCorrelationIdGenerator = SequentialCorrelationIdGenerator()
) : ViewModel() {
    private val _uiState = MutableStateFlow(ObservabilityRetentionUiState())
    val uiState: StateFlow<ObservabilityRetentionUiState> = _uiState.asStateFlow()

    init { refresh() }

    private fun retentionAuth(userId: String?) = ObservabilityAuthorizationContext(
        actorId = userId,
        permissions = setOf(ObservabilityPermission.RETENTION_MANAGE, ObservabilityPermission.OBSERVABILITY_VIEW),
        organizationIds = emptySet(),
        isPlatformActor = true,
        requestedSensitivity = ObservabilitySensitivity.RESTRICTED,
        requestedAction = ObservabilityRequestedAction.MANAGE_RETENTION
    )

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(phase = AdministrativeScreenPhase.Loading, errorMessage = null) }
            val gate = AdministrativeAccessGate.evaluate(
                authRepository, permissionRepository, PermissionCode.RETENTION_MANAGE
            )
            if (!gate.allowed) {
                _uiState.update {
                    ObservabilityRetentionUiState(phase = AdministrativeScreenPhase.AccessDenied)
                }
                return@launch
            }
            val auth = retentionAuth(gate.userId)
            val policies = repo.listPolicies(auth)
            val runs = repo.listRuns(auth)
            if (policies is AppResult.Failure) {
                _uiState.update {
                    it.copy(
                        phase = AdministrativeScreenPhase.Error,
                        errorMessage = ObservabilityUiErrorMapper.userMessage(policies.error.code)
                    )
                }
                return@launch
            }
            val policyList = (policies as AppResult.Success).data
            val runList = (runs as? AppResult.Success)?.data.orEmpty()
            _uiState.update {
                it.copy(
                    phase = ObservabilityUiErrorMapper.phaseForEmptyOrContent(policyList.isEmpty()),
                    policies = policyList,
                    runs = runList
                )
            }
        }
    }

    fun selectPolicy(policyId: String) {
        _uiState.update {
            it.copy(selectedPolicyId = policyId, preview = null, confirmExecute = false, infoMessage = null)
        }
    }

    fun previewSelected() {
        viewModelScope.launch {
            val policyId = _uiState.value.selectedPolicyId ?: return@launch
            val gate = AdministrativeAccessGate.evaluate(
                authRepository, permissionRepository, PermissionCode.RETENTION_MANAGE
            )
            if (!gate.allowed) return@launch
            val cid = correlationIds.next().value
            when (val result = repo.preview(retentionAuth(gate.userId), policyId, 100, cid)) {
                is AppResult.Success -> _uiState.update {
                    it.copy(
                        preview = result.data,
                        confirmExecute = false,
                        lastCorrelationId = cid,
                        infoMessage = "Preview listo. Confirmá explícitamente antes de ejecutar.",
                        errorMessage = null
                    )
                }
                is AppResult.Failure -> _uiState.update {
                    it.copy(
                        errorMessage = ObservabilityUiErrorMapper.userMessage(result.error.code),
                        lastCorrelationId = cid
                    )
                }
            }
        }
    }

    fun requestExecuteConfirmation() {
        if (_uiState.value.preview == null) return
        _uiState.update { it.copy(confirmExecute = true) }
    }

    fun cancelExecuteConfirmation() {
        _uiState.update { it.copy(confirmExecute = false) }
    }

    fun executeConfirmed() {
        viewModelScope.launch {
            val preview = _uiState.value.preview ?: return@launch
            if (!_uiState.value.confirmExecute) return@launch
            val gate = AdministrativeAccessGate.evaluate(
                authRepository, permissionRepository, PermissionCode.RETENTION_MANAGE
            )
            if (!gate.allowed) return@launch
            val cid = correlationIds.next().value
            when (val result = repo.execute(retentionAuth(gate.userId), preview.runId, cid)) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            preview = null,
                            confirmExecute = false,
                            lastCorrelationId = cid,
                            infoMessage = "Ejecución registrada. Afectados: ${result.data.affectedCount}"
                        )
                    }
                    refresh()
                }
                is AppResult.Failure -> _uiState.update {
                    it.copy(
                        confirmExecute = false,
                        errorMessage = ObservabilityUiErrorMapper.userMessage(result.error.code),
                        lastCorrelationId = cid
                    )
                }
            }
        }
    }

    fun setLegalHold(policyId: String) {
        viewModelScope.launch {
            val gate = AdministrativeAccessGate.evaluate(
                authRepository, permissionRepository, PermissionCode.RETENTION_MANAGE
            )
            if (!gate.allowed) return@launch
            repo.setLegalHold(retentionAuth(gate.userId), policyId, correlationIds.next().value)
            refresh()
        }
    }

    fun releaseLegalHold(policyId: String) {
        viewModelScope.launch {
            val gate = AdministrativeAccessGate.evaluate(
                authRepository, permissionRepository, PermissionCode.RETENTION_MANAGE
            )
            if (!gate.allowed) return@launch
            repo.releaseLegalHold(retentionAuth(gate.userId), policyId, correlationIds.next().value)
            refresh()
        }
    }

    fun clearOnLogout() {
        _uiState.value = ObservabilityRetentionUiState()
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ObservabilityRetentionViewModel() as T
        }
    }
}

data class ObservabilityPermissionsInfoUiState(
    val phase: AdministrativeScreenPhase = AdministrativeScreenPhase.Loading,
    val sections: List<ObservabilityPermissionSectionInfo> = emptyList(),
    val errorMessage: String? = null
)

class ObservabilityPermissionsInfoViewModel(
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val permissionRepository: PermissionRepository = DataProvider.permissionRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ObservabilityPermissionsInfoUiState())
    val uiState: StateFlow<ObservabilityPermissionsInfoUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(phase = AdministrativeScreenPhase.Loading) }
            val gate = AdministrativeAccessGate.evaluate(
                authRepository, permissionRepository, PermissionCode.OBSERVABILITY_VIEW
            )
            if (!gate.allowed) {
                _uiState.update {
                    ObservabilityPermissionsInfoUiState(phase = AdministrativeScreenPhase.AccessDenied)
                }
                return@launch
            }
            val user = authRepository.getCurrentUser()
            if (user == null) {
                _uiState.update {
                    ObservabilityPermissionsInfoUiState(phase = AdministrativeScreenPhase.AccessDenied)
                }
                return@launch
            }
            permissionRepository.refresh(user.id)
            val granted = PermissionCode.entries.filter {
                permissionRepository.hasPermission(user.id, it)
            }.toSet()
            // AccountType / modules never authorize — pass dummy claims to prove deny path
            val sections = ObservabilityPermissionsResolver.resolve(
                granted = granted,
                accountTypeClaim = "ORG_ADMIN",
                activeModulesClaim = setOf("M07")
            )
            _uiState.update {
                it.copy(phase = AdministrativeScreenPhase.Content, sections = sections)
            }
        }
    }

    fun clearOnLogout() {
        _uiState.value = ObservabilityPermissionsInfoUiState()
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ObservabilityPermissionsInfoViewModel() as T
        }
    }
}

/** Ensures RolePermissionMatrix still seeds M07 dedicated permissions for ADMIN. */
object ObservabilityPermissionMatrixProbe {
    fun adminHasDedicated(): Boolean {
        val perms = RolePermissionMatrix.permissionsFor(setOf(PlatformRoleCode.ADMIN))
        return PermissionCode.RETENTION_MANAGE in perms &&
            PermissionCode.OBSERVABILITY_VIEW in perms &&
            PermissionCode.EXPORT_AUDIT_DATA in perms
    }
}
