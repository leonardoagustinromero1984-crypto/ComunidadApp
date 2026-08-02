package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.M22ProviderCategory
import com.comunidapp.app.data.model.M22ProviderProfile
import com.comunidapp.app.data.model.M22PublicProviderDetail
import com.comunidapp.app.data.model.M22PublicProviderListing
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.M22ProviderRepository
import com.comunidapp.app.domain.m22.M22ProviderResilience
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

sealed class M22HubUiState {
    data object Loading : M22HubUiState()
    data class Content(val providerCount: Int) : M22HubUiState()
    data object Empty : M22HubUiState()
    data class Error(val message: String) : M22HubUiState()
}
class M22HubViewModel(private val repository: M22ProviderRepository = DataProvider.m22ProviderRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<M22HubUiState>(M22HubUiState.Loading)
    val uiState: StateFlow<M22HubUiState> = _uiState
    init { viewModelScope.launch { repository.observeCatalog().catch { _uiState.value = M22HubUiState.Error(M22ProviderResilience.safeUserMessage(it)) }.collect { _uiState.value = if (it.isEmpty()) M22HubUiState.Empty else M22HubUiState.Content(it.size) } } }
}

sealed class M22CatalogUiState {
    data object Loading : M22CatalogUiState()
    data class Content(val items: List<M22PublicProviderListing>) : M22CatalogUiState()
    data object Empty : M22CatalogUiState()
    data class Error(val message: String) : M22CatalogUiState()
}
class M22CatalogViewModel(
    private val category: M22ProviderCategory? = null,
    private val repository: M22ProviderRepository = DataProvider.m22ProviderRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<M22CatalogUiState>(M22CatalogUiState.Loading)
    val uiState: StateFlow<M22CatalogUiState> = _uiState
    init { viewModelScope.launch { repository.observeCatalog(category).catch { _uiState.value = M22CatalogUiState.Error(M22ProviderResilience.safeUserMessage(it)) }.collect { _uiState.value = if (it.isEmpty()) M22CatalogUiState.Empty else M22CatalogUiState.Content(it) } } }
}

sealed class M22DetailUiState {
    data object Loading : M22DetailUiState()
    data class Content(val provider: M22PublicProviderDetail) : M22DetailUiState()
    data object Empty : M22DetailUiState()
    data class Error(val message: String) : M22DetailUiState()
}
class M22DetailViewModel(
    providerId: String,
    repository: M22ProviderRepository = DataProvider.m22ProviderRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<M22DetailUiState>(M22DetailUiState.Loading)
    val uiState: StateFlow<M22DetailUiState> = _uiState
    init { viewModelScope.launch { repository.observeProviderDetail(providerId).catch { _uiState.value = M22DetailUiState.Error(M22ProviderResilience.safeUserMessage(it)) }.collect { _uiState.value = it?.let(M22DetailUiState::Content) ?: M22DetailUiState.Empty } } }
}

sealed class M22ManageUiState {
    data object Loading : M22ManageUiState()
    data class Content(val providers: List<M22ProviderProfile>) : M22ManageUiState()
    data object Empty : M22ManageUiState()
    data class Error(val message: String) : M22ManageUiState()
}
class M22ManageViewModel(private val repository: M22ProviderRepository = DataProvider.m22ProviderRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<M22ManageUiState>(M22ManageUiState.Loading)
    val uiState: StateFlow<M22ManageUiState> = _uiState
    init { viewModelScope.launch { repository.observeMyProviders().catch { _uiState.value = M22ManageUiState.Error(M22ProviderResilience.safeUserMessage(it)) }.collect { _uiState.value = if (it.isEmpty()) M22ManageUiState.Empty else M22ManageUiState.Content(it) } } }

    fun publish(providerId: String) = perform { repository.publishProvider(providerId) }
    fun suspend(providerId: String) = perform { repository.suspendProvider(providerId) }

    private fun perform(operation: suspend () -> Result<M22ProviderProfile>) {
        viewModelScope.launch {
            operation().exceptionOrNull()?.let {
                _uiState.value = M22ManageUiState.Error(M22ProviderResilience.safeUserMessage(it))
            }
        }
    }
}

object M22ViewModelFactories {
    fun catalog(category: M22ProviderCategory? = null) = factory { M22CatalogViewModel(category) }
    fun detail(providerId: String) = factory { M22DetailViewModel(providerId) }
    private fun factory(create: () -> ViewModel): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
    }
}
