package com.comunidapp.app.viewmodel.observability

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.OperationalObservabilityRepository
import com.comunidapp.app.data.repository.PermissionRepository
import com.comunidapp.app.domain.authorization.PermissionCode
import com.comunidapp.app.domain.observability.AlertIncident
import com.comunidapp.app.domain.observability.AlertIncidentState
import com.comunidapp.app.domain.observability.HealthCheck
import com.comunidapp.app.domain.observability.ObservabilityModule
import com.comunidapp.app.domain.observability.ObservabilitySensitivity
import com.comunidapp.app.domain.observability.OperationalSummary
import com.comunidapp.app.domain.observability.PerformanceMetric
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

private fun staffAuth(
    userId: String?,
    manage: Boolean
): ObservabilityAuthorizationContext = ObservabilityAuthorizationContext(
    actorId = userId,
    permissions = buildSet {
        add(ObservabilityPermission.OBSERVABILITY_VIEW)
        add(ObservabilityPermission.AUDIT_VIEW)
        if (manage) {
            add(ObservabilityPermission.OBSERVABILITY_MANAGE)
            add(ObservabilityPermission.ALERT_MANAGE)
        }
    },
    organizationIds = emptySet(),
    isPlatformActor = true,
    requestedSensitivity = ObservabilitySensitivity.INTERNAL,
    requestedAction = ObservabilityRequestedAction.VIEW
)

data class ObservabilityOverviewUiState(
    val phase: AdministrativeScreenPhase = AdministrativeScreenPhase.Loading,
    val summary: OperationalSummary? = null,
    val errorMessage: String? = null,
    val canManage: Boolean = false
)

class ObservabilityOverviewViewModel(
    private val repo: OperationalObservabilityRepository = DataProvider.operationalObservabilityRepository,
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val permissionRepository: PermissionRepository = DataProvider.permissionRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ObservabilityOverviewUiState())
    val uiState: StateFlow<ObservabilityOverviewUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { ObservabilityOverviewUiState(phase = AdministrativeScreenPhase.Loading) }
            val gate = AdministrativeAccessGate.evaluate(
                authRepository, permissionRepository, PermissionCode.AUDIT_VIEW
            )
            if (!gate.allowed) {
                _uiState.update {
                    ObservabilityOverviewUiState(phase = AdministrativeScreenPhase.AccessDenied)
                }
                return@launch
            }
            when (val result = repo.getOperationalSummary(staffAuth(gate.userId, manage = true))) {
                is AppResult.Success -> _uiState.update {
                    it.copy(
                        phase = AdministrativeScreenPhase.Content,
                        summary = result.data,
                        canManage = true
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
        _uiState.value = ObservabilityOverviewUiState()
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ObservabilityOverviewViewModel() as T
        }
    }
}

data class ObservabilityMetricsUiState(
    val phase: AdministrativeScreenPhase = AdministrativeScreenPhase.Loading,
    val metrics: List<PerformanceMetric> = emptyList(),
    val filters: ObservabilityFilterState = ObservabilityFilterState(),
    val errorMessage: String? = null
)

class ObservabilityMetricsViewModel(
    private val repo: OperationalObservabilityRepository = DataProvider.operationalObservabilityRepository,
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val permissionRepository: PermissionRepository = DataProvider.permissionRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ObservabilityMetricsUiState())
    val uiState: StateFlow<ObservabilityMetricsUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun setModule(raw: String) {
        val module = ObservabilityModule.fromString(raw)
        _uiState.update { it.copy(filters = it.filters.copy(module = module)) }
        refresh()
    }

    fun setMetricKey(key: String) {
        _uiState.update { it.copy(filters = it.filters.copy(metricKey = key)) }
        refresh()
    }

    fun clearFilters() {
        _uiState.update { it.copy(filters = it.filters.clear()) }
        refresh()
    }

    fun clearOnLogout() {
        _uiState.value = ObservabilityMetricsUiState()
    }

    fun refresh() {
        viewModelScope.launch {
            val filters = _uiState.value.filters
            _uiState.update { it.copy(phase = AdministrativeScreenPhase.Loading, errorMessage = null) }
            val gate = AdministrativeAccessGate.evaluate(
                authRepository, permissionRepository, PermissionCode.AUDIT_VIEW
            )
            if (!gate.allowed) {
                _uiState.update {
                    ObservabilityMetricsUiState(phase = AdministrativeScreenPhase.AccessDenied)
                }
                return@launch
            }
            when (
                val result = repo.listMetrics(
                    auth = staffAuth(gate.userId, manage = false),
                    module = filters.module,
                    metricKey = filters.metricKey.ifBlank { null }
                )
            ) {
                is AppResult.Success -> {
                    val filtered = result.data.filter {
                        filters.unit.isBlank() || it.unit.equals(filters.unit, ignoreCase = true)
                    }
                    _uiState.update {
                        it.copy(
                            phase = ObservabilityUiErrorMapper.phaseForEmptyOrContent(filtered.isEmpty()),
                            metrics = filtered,
                            filters = filters
                        )
                    }
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

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ObservabilityMetricsViewModel() as T
        }
    }
}

data class ObservabilityHealthUiState(
    val phase: AdministrativeScreenPhase = AdministrativeScreenPhase.Loading,
    val checks: List<HealthCheck> = emptyList(),
    val errorMessage: String? = null,
    val canRunManual: Boolean = false
)

class ObservabilityHealthViewModel(
    private val repo: OperationalObservabilityRepository = DataProvider.operationalObservabilityRepository,
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val permissionRepository: PermissionRepository = DataProvider.permissionRepository,
    private val correlationIds: SequentialCorrelationIdGenerator = SequentialCorrelationIdGenerator()
) : ViewModel() {
    private val _uiState = MutableStateFlow(ObservabilityHealthUiState())
    val uiState: StateFlow<ObservabilityHealthUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(phase = AdministrativeScreenPhase.Loading, errorMessage = null) }
            val gate = AdministrativeAccessGate.evaluate(
                authRepository, permissionRepository, PermissionCode.AUDIT_VIEW
            )
            if (!gate.allowed) {
                _uiState.update {
                    ObservabilityHealthUiState(phase = AdministrativeScreenPhase.AccessDenied)
                }
                return@launch
            }
            when (val result = repo.listHealthChecks(staffAuth(gate.userId, manage = true))) {
                is AppResult.Success -> _uiState.update {
                    it.copy(
                        phase = ObservabilityUiErrorMapper.phaseForEmptyOrContent(result.data.isEmpty()),
                        checks = result.data,
                        canRunManual = true
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

    fun runManual(checkKey: String) {
        viewModelScope.launch {
            val gate = AdministrativeAccessGate.evaluate(
                authRepository, permissionRepository, PermissionCode.AUDIT_VIEW
            )
            if (!gate.allowed || !_uiState.value.canRunManual) return@launch
            repo.runHealthCheckManual(
                staffAuth(gate.userId, manage = true),
                checkKey,
                correlationIds.next().value
            )
            refresh()
        }
    }

    fun clearOnLogout() {
        _uiState.value = ObservabilityHealthUiState()
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ObservabilityHealthViewModel() as T
        }
    }
}

data class ObservabilityIncidentsUiState(
    val phase: AdministrativeScreenPhase = AdministrativeScreenPhase.Loading,
    val incidents: List<AlertIncident> = emptyList(),
    val filters: ObservabilityFilterState = ObservabilityFilterState(),
    val errorMessage: String? = null,
    val canManage: Boolean = false
)

class ObservabilityIncidentsViewModel(
    private val repo: OperationalObservabilityRepository = DataProvider.operationalObservabilityRepository,
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val permissionRepository: PermissionRepository = DataProvider.permissionRepository,
    private val correlationIds: SequentialCorrelationIdGenerator = SequentialCorrelationIdGenerator()
) : ViewModel() {
    private val _uiState = MutableStateFlow(ObservabilityIncidentsUiState())
    val uiState: StateFlow<ObservabilityIncidentsUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun setStatusFilter(raw: String) {
        _uiState.update { it.copy(filters = it.filters.copy(incidentStatus = raw)) }
        refresh()
    }

    fun clearFilters() {
        _uiState.update { it.copy(filters = it.filters.clear()) }
        refresh()
    }

    fun clearOnLogout() {
        _uiState.value = ObservabilityIncidentsUiState()
    }

    fun refresh() {
        viewModelScope.launch {
            val filters = _uiState.value.filters
            _uiState.update { it.copy(phase = AdministrativeScreenPhase.Loading, errorMessage = null) }
            val gate = AdministrativeAccessGate.evaluate(
                authRepository, permissionRepository, PermissionCode.AUDIT_VIEW
            )
            if (!gate.allowed) {
                _uiState.update {
                    ObservabilityIncidentsUiState(phase = AdministrativeScreenPhase.AccessDenied)
                }
                return@launch
            }
            val state = AlertIncidentState.entries.firstOrNull {
                it.name.equals(filters.incidentStatus.trim(), ignoreCase = true)
            }
            when (
                val result = repo.listIncidents(
                    staffAuth(gate.userId, manage = true),
                    state = state
                )
            ) {
                is AppResult.Success -> _uiState.update {
                    it.copy(
                        phase = ObservabilityUiErrorMapper.phaseForEmptyOrContent(result.data.isEmpty()),
                        incidents = result.data,
                        filters = filters,
                        canManage = true
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

    fun acknowledge(incidentId: String) {
        viewModelScope.launch {
            if (!_uiState.value.canManage) return@launch
            val gate = AdministrativeAccessGate.evaluate(
                authRepository, permissionRepository, PermissionCode.AUDIT_VIEW
            )
            if (!gate.allowed) return@launch
            repo.acknowledgeIncident(
                staffAuth(gate.userId, manage = true),
                incidentId,
                correlationIds.next().value
            )
            refresh()
        }
    }

    fun resolve(incidentId: String) {
        viewModelScope.launch {
            if (!_uiState.value.canManage) return@launch
            val gate = AdministrativeAccessGate.evaluate(
                authRepository, permissionRepository, PermissionCode.AUDIT_VIEW
            )
            if (!gate.allowed) return@launch
            repo.resolveIncident(
                staffAuth(gate.userId, manage = true),
                incidentId,
                "MANUAL_RESOLVE",
                correlationIds.next().value
            )
            refresh()
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ObservabilityIncidentsViewModel() as T
        }
    }
}
