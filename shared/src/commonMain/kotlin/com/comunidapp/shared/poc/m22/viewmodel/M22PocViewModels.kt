package com.comunidapp.shared.poc.m22.viewmodel

import com.comunidapp.shared.poc.m22.data.M22PocCatalogRepository
import com.comunidapp.shared.poc.m22.domain.M22PocResilience
import com.comunidapp.shared.poc.m22.model.M22PocDetail
import com.comunidapp.shared.poc.m22.model.M22PocListing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * Multiplatform ViewModel without androidx.lifecycle.
 * Change vs production: uses CoroutineScope(SupervisorJob + Main/Default) instead of viewModelScope.
 */
sealed class M22PocCatalogUiState {
    data object Loading : M22PocCatalogUiState()
    data object Empty : M22PocCatalogUiState()
    data class Content(val items: List<M22PocListing>) : M22PocCatalogUiState()
    data class Error(val message: String) : M22PocCatalogUiState()
}

sealed class M22PocDetailUiState {
    data object Loading : M22PocDetailUiState()
    data object Empty : M22PocDetailUiState()
    data class Content(val provider: M22PocDetail) : M22PocDetailUiState()
    data class Error(val message: String) : M22PocDetailUiState()
}

class M22PocCatalogViewModel(
    private val repository: M22PocCatalogRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val _uiState = MutableStateFlow<M22PocCatalogUiState>(M22PocCatalogUiState.Loading)
    val uiState: StateFlow<M22PocCatalogUiState> = _uiState

    init {
        scope.launch {
            repository.observeCatalog()
                .catch { _uiState.value = M22PocCatalogUiState.Error(M22PocResilience.safeUserMessage(it)) }
                .collect { list ->
                    _uiState.value = if (list.isEmpty()) M22PocCatalogUiState.Empty
                    else M22PocCatalogUiState.Content(list)
                }
        }
    }

    fun clear() {
        scope.cancel()
    }
}

class M22PocDetailViewModel(
    providerId: String,
    repository: M22PocCatalogRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val _uiState = MutableStateFlow<M22PocDetailUiState>(M22PocDetailUiState.Loading)
    val uiState: StateFlow<M22PocDetailUiState> = _uiState

    init {
        scope.launch {
            repository.observeDetail(providerId)
                .catch { _uiState.value = M22PocDetailUiState.Error(M22PocResilience.safeUserMessage(it)) }
                .collect { detail ->
                    _uiState.value = detail?.let(M22PocDetailUiState::Content) ?: M22PocDetailUiState.Empty
                }
        }
    }

    fun clear() {
        scope.cancel()
    }
}
