package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.M15MetricsPolicy
import com.comunidapp.app.data.model.M15OperationalMetrics
import com.comunidapp.app.data.model.M15OperationalMetricsQuery
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.remote.supabase.m15.M15ErrorMapper
import com.comunidapp.app.data.repository.M15M06NotificationBridge
import com.comunidapp.app.data.repository.M15OperationsRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class M15OperationsUiState {
    data object Loading : M15OperationsUiState()
    data class Content(
        val metrics: M15OperationalMetrics?,
        val m06Status: String,
        val remotePending: Boolean,
        val preparedHookCount: Int,
        val noPersonalDataNotice: String = "Métricas agregadas sin datos personales."
    ) : M15OperationsUiState()
    data class Error(val message: String) : M15OperationsUiState()
}

/** M15 Bloque 4 — dashboard operativo: métricas, privacidad, M06 y smoke manual. */
class M15OperationsViewModel(
    private val repository: M15OperationsRepository = DataProvider.m15OperationsRepository,
    private val useSupabase: Boolean = DataProvider.useSupabase
) : ViewModel() {
    private val _uiState = MutableStateFlow<M15OperationsUiState>(M15OperationsUiState.Loading)
    val uiState: StateFlow<M15OperationsUiState> = _uiState.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    val preparedHooks = repository.observePreparedM06Hooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        loadDefaultMetrics()
    }

    fun selectTab(index: Int) {
        _selectedTab.value = index
    }

    fun loadDefaultMetrics() {
        val now = System.currentTimeMillis()
        val from = now - TimeUnit.DAYS.toMillis(30)
        loadMetrics(from, now)
    }

    fun loadMetrics(fromInclusive: Long, toExclusive: Long) {
        viewModelScope.launch {
            _uiState.value = M15OperationsUiState.Loading
            val query = M15OperationalMetricsQuery(
                fromInclusive = fromInclusive,
                toExclusive = toExclusive,
                zoneIdName = M15MetricsPolicy.DEFAULT_ZONE
            )
            repository.getOperationalMetrics(query)
                .onSuccess { metrics ->
                    val m06 = repository.m06InfrastructureStatus(useSupabase)
                    val remotePending = m06 == M15M06NotificationBridge.REMOTE_VALIDATION_PENDING
                    _uiState.value = M15OperationsUiState.Content(
                        metrics = metrics,
                        m06Status = m06,
                        remotePending = remotePending,
                        preparedHookCount = preparedHooks.value.size
                    )
                    _message.value = null
                }
                .onFailure { e ->
                    val code = M15ErrorMapper.codeOf(e)
                    val remotePending = code == "M15_REMOTE_VALIDATION_PENDING" ||
                        code == "M15_NOTIFICATION_INFRASTRUCTURE_UNAVAILABLE"
                    if (remotePending) {
                        _uiState.value = M15OperationsUiState.Content(
                            metrics = null,
                            m06Status = code,
                            remotePending = true,
                            preparedHookCount = preparedHooks.value.size
                        )
                        _message.value = M15ErrorMapper.userMessage(code)
                    } else {
                        _uiState.value = M15OperationsUiState.Error(
                            M15ErrorMapper.userMessage(code)
                        )
                    }
                }
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M15OperationsViewModel() as T
        }
    }
}
