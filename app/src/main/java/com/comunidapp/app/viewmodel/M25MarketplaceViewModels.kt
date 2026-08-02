package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.*
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.M25CartRepository
import com.comunidapp.app.data.repository.M25MarketplaceRepository
import com.comunidapp.app.data.repository.M25OrderRepository
import com.comunidapp.app.domain.m25.M25MarketplaceResilience
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

sealed class M25HubUiState {
    data object Loading : M25HubUiState()
    data class Content(val shopCount: Int) : M25HubUiState()
    data object Empty : M25HubUiState()
    data class Error(val message: String) : M25HubUiState()
}

class M25HubViewModel(private val repository: M25MarketplaceRepository = DataProvider.m25MarketplaceRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<M25HubUiState>(M25HubUiState.Loading)
    val uiState: StateFlow<M25HubUiState> = _uiState
    init {
        viewModelScope.launch {
            repository.observeCatalog().catch { _uiState.value = M25HubUiState.Error(M25MarketplaceResilience.safeUserMessage(it)) }
                .collect { _uiState.value = if (it.isEmpty()) M25HubUiState.Empty else M25HubUiState.Content(it.size) }
        }
    }
}

sealed class M25CatalogUiState {
    data object Loading : M25CatalogUiState()
    data class Content(val items: List<M25PublicShopListing>) : M25CatalogUiState()
    data object Empty : M25CatalogUiState()
    data class Error(val message: String) : M25CatalogUiState()
}

class M25CatalogViewModel(
    private val category: M25ShopCategory? = null,
    private val repository: M25MarketplaceRepository = DataProvider.m25MarketplaceRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<M25CatalogUiState>(M25CatalogUiState.Loading)
    val uiState: StateFlow<M25CatalogUiState> = _uiState
    init {
        viewModelScope.launch {
            repository.observeCatalog(M25CatalogFilter(category = category)).catch {
                _uiState.value = M25CatalogUiState.Error(M25MarketplaceResilience.safeUserMessage(it))
            }.collect { _uiState.value = if (it.isEmpty()) M25CatalogUiState.Empty else M25CatalogUiState.Content(it) }
        }
    }
}

sealed class M25DetailUiState {
    data object Loading : M25DetailUiState()
    data class Content(val shop: M25PublicShopDetail) : M25DetailUiState()
    data object Empty : M25DetailUiState()
    data class Error(val message: String) : M25DetailUiState()
}

class M25DetailViewModel(
    shopId: String,
    private val repository: M25MarketplaceRepository = DataProvider.m25MarketplaceRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<M25DetailUiState>(M25DetailUiState.Loading)
    val uiState: StateFlow<M25DetailUiState> = _uiState
    init {
        viewModelScope.launch {
            repository.observeShopDetail(shopId).catch {
                _uiState.value = M25DetailUiState.Error(M25MarketplaceResilience.safeUserMessage(it))
            }.collect { _uiState.value = it?.let(M25DetailUiState::Content) ?: M25DetailUiState.Empty }
        }
    }
}

sealed class M25CartUiState {
    data object Loading : M25CartUiState()
    data class Content(val items: List<M25CartItem>) : M25CartUiState()
    data object Empty : M25CartUiState()
    data class Error(val message: String) : M25CartUiState()
}

class M25CartViewModel(private val repository: M25CartRepository = DataProvider.m25CartRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<M25CartUiState>(M25CartUiState.Loading)
    val uiState: StateFlow<M25CartUiState> = _uiState
    init { refresh() }
    fun refresh() {
        viewModelScope.launch {
            repository.observeCart().catch { _uiState.value = M25CartUiState.Error(M25MarketplaceResilience.safeUserMessage(it)) }
                .collect { _uiState.value = if (it.isEmpty()) M25CartUiState.Empty else M25CartUiState.Content(it) }
        }
    }
}

sealed class M25OrdersUiState {
    data object Loading : M25OrdersUiState()
    data class Content(val orders: List<M25OrderSummary>) : M25OrdersUiState()
    data object Empty : M25OrdersUiState()
    data class Error(val message: String) : M25OrdersUiState()
}

class M25OrdersViewModel(private val repository: M25OrderRepository = DataProvider.m25OrderRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<M25OrdersUiState>(M25OrdersUiState.Loading)
    val uiState: StateFlow<M25OrdersUiState> = _uiState
    init {
        viewModelScope.launch {
            repository.observeMyOrders().catch { _uiState.value = M25OrdersUiState.Error(M25MarketplaceResilience.safeUserMessage(it)) }
                .collect { _uiState.value = if (it.isEmpty()) M25OrdersUiState.Empty else M25OrdersUiState.Content(it) }
        }
    }
}

sealed class M25ManageUiState {
    data object Loading : M25ManageUiState()
    data class Content(val shops: List<M25Shop>) : M25ManageUiState()
    data object Empty : M25ManageUiState()
    data class Error(val message: String) : M25ManageUiState()
}

class M25ManageViewModel(private val repository: M25MarketplaceRepository = DataProvider.m25MarketplaceRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<M25ManageUiState>(M25ManageUiState.Loading)
    val uiState: StateFlow<M25ManageUiState> = _uiState
    init {
        viewModelScope.launch {
            repository.observeMyShops().catch { _uiState.value = M25ManageUiState.Error(M25MarketplaceResilience.safeUserMessage(it)) }
                .collect { _uiState.value = if (it.isEmpty()) M25ManageUiState.Empty else M25ManageUiState.Content(it) }
        }
    }
}

object M25ViewModelFactories {
    fun catalog(category: M25ShopCategory? = null) = factory { M25CatalogViewModel(category) }
    fun detail(shopId: String) = factory { M25DetailViewModel(shopId) }
    private fun factory(create: () -> ViewModel): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
    }
}
