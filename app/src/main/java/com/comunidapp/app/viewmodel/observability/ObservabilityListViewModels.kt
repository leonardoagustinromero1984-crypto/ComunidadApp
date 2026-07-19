package com.comunidapp.app.viewmodel.observability

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.ApplicationErrorRepository
import com.comunidapp.app.data.repository.AuditEventRepository
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.ObservabilityExportRepository
import com.comunidapp.app.data.repository.PermissionRepository
import com.comunidapp.app.domain.authorization.PermissionCode
import com.comunidapp.app.domain.observability.ApplicationError
import com.comunidapp.app.domain.observability.AuditEvent
import com.comunidapp.app.domain.observability.ObservabilityExport
import com.comunidapp.app.domain.observability.ObservabilityExportState
import com.comunidapp.app.domain.observability.ObservabilitySensitivity
import com.comunidapp.app.domain.observability.authorization.ObservabilityAuthorizationContext
import com.comunidapp.app.domain.observability.authorization.ObservabilityPermission
import com.comunidapp.app.domain.observability.authorization.ObservabilityRequestedAction
import com.comunidapp.app.domain.observability.correlation.SequentialCorrelationIdGenerator
import com.comunidapp.app.viewmodel.moderation.AdministrativeAccessGate
import com.comunidapp.app.viewmodel.moderation.AdministrativeScreenPhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/** Filas opacas para UI — sin contenido leído / sin PII / sin paths. */
data class ObservabilityOpaqueRow(
    val id: String,
    val primary: String,
    val secondary: String,
    val tertiary: String = ""
)

data class ObservabilityAuditListUiState(
    val phase: AdministrativeScreenPhase = AdministrativeScreenPhase.Loading,
    val rows: List<ObservabilityOpaqueRow> = emptyList(),
    val errorMessage: String? = null,
    val canViewSensitive: Boolean = false
)

class ObservabilityAuditListViewModel(
    private val repo: AuditEventRepository = DataProvider.auditEventRepository,
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val permissionRepository: PermissionRepository = DataProvider.permissionRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ObservabilityAuditListUiState())
    val uiState: StateFlow<ObservabilityAuditListUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { ObservabilityAuditListUiState(phase = AdministrativeScreenPhase.Loading) }
            val gate = AdministrativeAccessGate.evaluate(
                authRepository,
                permissionRepository,
                PermissionCode.OBSERVABILITY_VIEW,
                sensitivePermission = PermissionCode.AUDIT_VIEW_SENSITIVE
            )
            if (!gate.allowed) {
                _uiState.update {
                    ObservabilityAuditListUiState(phase = AdministrativeScreenPhase.AccessDenied)
                }
                return@launch
            }
            val auth = ObservabilityAuthorizationContext(
                actorId = gate.userId,
                permissions = buildSet {
                    add(ObservabilityPermission.OBSERVABILITY_VIEW)
                    if (gate.canViewSensitive) add(ObservabilityPermission.AUDIT_VIEW_SENSITIVE)
                },
                organizationIds = emptySet(),
                isPlatformActor = true,
                requestedSensitivity = if (gate.canViewSensitive) {
                    ObservabilitySensitivity.SECURITY_SENSITIVE
                } else {
                    ObservabilitySensitivity.RESTRICTED
                },
                requestedAction = ObservabilityRequestedAction.VIEW
            )
            when (val result = repo.list(auth)) {
                is AppResult.Success -> {
                    val rows = result.data.map { it.toOpaqueRow() }
                    _uiState.update {
                        ObservabilityAuditListUiState(
                            phase = ObservabilityUiErrorMapper.phaseForEmptyOrContent(rows.isEmpty()),
                            rows = rows,
                            canViewSensitive = gate.canViewSensitive
                        )
                    }
                }
                is AppResult.Failure -> _uiState.update {
                    ObservabilityAuditListUiState(
                        phase = AdministrativeScreenPhase.Error,
                        errorMessage = ObservabilityUiErrorMapper.userMessage(result.error.code)
                    )
                }
            }
        }
    }

    fun clearOnLogout() {
        _uiState.value = ObservabilityAuditListUiState()
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ObservabilityAuditListViewModel() as T
        }
    }
}

data class ObservabilityErrorsListUiState(
    val phase: AdministrativeScreenPhase = AdministrativeScreenPhase.Loading,
    val rows: List<ObservabilityOpaqueRow> = emptyList(),
    val errorMessage: String? = null
)

class ObservabilityErrorsListViewModel(
    private val repo: ApplicationErrorRepository = DataProvider.applicationErrorRepository,
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val permissionRepository: PermissionRepository = DataProvider.permissionRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ObservabilityErrorsListUiState())
    val uiState: StateFlow<ObservabilityErrorsListUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { ObservabilityErrorsListUiState(phase = AdministrativeScreenPhase.Loading) }
            val gate = AdministrativeAccessGate.evaluate(
                authRepository, permissionRepository, PermissionCode.OBSERVABILITY_VIEW
            )
            if (!gate.allowed) {
                _uiState.update {
                    ObservabilityErrorsListUiState(phase = AdministrativeScreenPhase.AccessDenied)
                }
                return@launch
            }
            val auth = ObservabilityAuthorizationContext(
                actorId = gate.userId,
                permissions = setOf(ObservabilityPermission.OBSERVABILITY_VIEW),
                organizationIds = emptySet(),
                isPlatformActor = true,
                requestedSensitivity = ObservabilitySensitivity.INTERNAL,
                requestedAction = ObservabilityRequestedAction.VIEW
            )
            when (val result = repo.list(auth)) {
                is AppResult.Success -> {
                    val rows = result.data.map {
                        ObservabilityOpaqueRow(
                            id = it.id,
                            primary = it.errorCode.name,
                            secondary = "${it.module} · ${it.sanitized.fingerprint}",
                            tertiary = it.correlationId?.value.orEmpty()
                        )
                    }
                    _uiState.update {
                        ObservabilityErrorsListUiState(
                            phase = ObservabilityUiErrorMapper.phaseForEmptyOrContent(rows.isEmpty()),
                            rows = rows
                        )
                    }
                }
                is AppResult.Failure -> _uiState.update {
                    ObservabilityErrorsListUiState(
                        phase = AdministrativeScreenPhase.Error,
                        errorMessage = ObservabilityUiErrorMapper.userMessage(result.error.code)
                    )
                }
            }
        }
    }

    fun clearOnLogout() {
        _uiState.value = ObservabilityErrorsListUiState()
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ObservabilityErrorsListViewModel() as T
        }
    }
}

data class ObservabilityExportsUiState(
    val phase: AdministrativeScreenPhase = AdministrativeScreenPhase.Loading,
    val lastExport: ObservabilityExport? = null,
    val infoMessage: String? = "EXPORTACIÓN DE ARCHIVO PENDIENTE",
    val errorMessage: String? = null
)

class ObservabilityExportsViewModel(
    private val repo: ObservabilityExportRepository = DataProvider.observabilityExportRepository,
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val permissionRepository: PermissionRepository = DataProvider.permissionRepository,
    private val correlationIds: SequentialCorrelationIdGenerator = SequentialCorrelationIdGenerator()
) : ViewModel() {
    private val _uiState = MutableStateFlow(ObservabilityExportsUiState())
    val uiState: StateFlow<ObservabilityExportsUiState> = _uiState.asStateFlow()

    init {
        _uiState.value = ObservabilityExportsUiState(phase = AdministrativeScreenPhase.Content)
    }

    fun requestExport() {
        viewModelScope.launch {
            val gate = AdministrativeAccessGate.evaluate(
                authRepository, permissionRepository, PermissionCode.EXPORT_AUDIT_DATA
            )
            if (!gate.allowed) {
                _uiState.update {
                    it.copy(
                        phase = AdministrativeScreenPhase.AccessDenied,
                        errorMessage = "Sin permiso export.audit_data"
                    )
                }
                return@launch
            }
            val auth = ObservabilityAuthorizationContext(
                actorId = gate.userId,
                permissions = setOf(ObservabilityPermission.EXPORT_AUDIT_DATA),
                organizationIds = emptySet(),
                isPlatformActor = true,
                requestedSensitivity = ObservabilitySensitivity.RESTRICTED,
                requestedAction = ObservabilityRequestedAction.EXPORT
            )
            val req = ObservabilityExport(
                id = "",
                requestedBy = gate.userId.orEmpty(),
                sensitivity = ObservabilitySensitivity.RESTRICTED,
                state = ObservabilityExportState.REQUESTED,
                correlationId = correlationIds.next(),
                requestedAt = Instant.now(),
                filePending = true
            )
            when (val result = repo.request(req, auth)) {
                is AppResult.Success -> _uiState.update {
                    ObservabilityExportsUiState(
                        phase = AdministrativeScreenPhase.Content,
                        lastExport = result.data,
                        infoMessage = "Estado ${result.data.state}. Archivo: PENDIENTE (sin signed URL)."
                    )
                }
                is AppResult.Failure -> _uiState.update {
                    it.copy(
                        phase = AdministrativeScreenPhase.Error,
                        errorMessage = ObservabilityUiErrorMapper.userMessage(result.error.code)
                    )
                }
            }
        }
    }

    fun clearOnLogout() {
        _uiState.value = ObservabilityExportsUiState()
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ObservabilityExportsViewModel() as T
        }
    }
}

private fun AuditEvent.toOpaqueRow(): ObservabilityOpaqueRow = ObservabilityOpaqueRow(
    id = id,
    primary = eventKey,
    secondary = "${module.name} · ${result.name} · ${sensitivity.name}",
    tertiary = correlationId?.value.orEmpty()
)
