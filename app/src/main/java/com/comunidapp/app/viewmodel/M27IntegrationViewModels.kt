package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.M27Environment
import com.comunidapp.app.data.model.M27PublicApiKey
import com.comunidapp.app.data.model.M27PublicContract
import com.comunidapp.app.data.model.M27PublicOAuthApp
import com.comunidapp.app.data.model.M27PublicRateLimit
import com.comunidapp.app.data.model.M27PublicWebhook
import com.comunidapp.app.data.model.RegisterM27OAuthAppInput
import com.comunidapp.app.data.model.RegisterM27WebhookInput
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.M27IntegrationErrors
import com.comunidapp.app.data.repository.M27IntegrationException
import com.comunidapp.app.data.repository.M27IntegrationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

private fun safeMessage(error: Throwable): String = when (error) {
    is M27IntegrationException -> M27IntegrationErrors.userMessage(error.code)
    else -> M27IntegrationErrors.userMessage("UNKNOWN")
}

sealed class M27HubUiState {
    data object Loading : M27HubUiState()
    data class Content(
        val webhookCount: Int,
        val oauthCount: Int,
        val keyCount: Int,
        val contractCount: Int
    ) : M27HubUiState()
    data object Empty : M27HubUiState()
    data class Error(val message: String) : M27HubUiState()
}

class M27HubViewModel(private val repository: M27IntegrationRepository = DataProvider.m27IntegrationRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<M27HubUiState>(M27HubUiState.Loading)
    val uiState: StateFlow<M27HubUiState> = _uiState

    init {
        viewModelScope.launch {
            combine(
                repository.observeWebhooks(),
                repository.observeOAuthApps(),
                repository.observeApiKeys(),
                repository.observePublishedContracts()
            ) { webhooks, oauth, keys, contracts ->
                listOf(webhooks.size, oauth.size, keys.size, contracts.size)
            }.catch { _uiState.value = M27HubUiState.Error(safeMessage(it)) }
                .collect { counts ->
                    val (w, o, k, c) = counts
                    _uiState.value = if (w == 0 && o == 0 && k == 0 && c == 0) M27HubUiState.Empty
                    else M27HubUiState.Content(w, o, k, c)
                }
        }
    }
}

sealed class M27ListUiState<out T> {
    data object Loading : M27ListUiState<Nothing>()
    data class Content<T>(val items: List<T>) : M27ListUiState<T>()
    data object Empty : M27ListUiState<Nothing>()
    data class Error(val message: String) : M27ListUiState<Nothing>()
}

class M27WebhooksViewModel(private val repository: M27IntegrationRepository = DataProvider.m27IntegrationRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<M27ListUiState<M27PublicWebhook>>(M27ListUiState.Loading)
    val uiState: StateFlow<M27ListUiState<M27PublicWebhook>> = _uiState

    init {
        viewModelScope.launch {
            repository.observeWebhooks().catch {
                _uiState.value = M27ListUiState.Error(safeMessage(it))
            }.collect {
                _uiState.value = if (it.isEmpty()) M27ListUiState.Empty else M27ListUiState.Content(it)
            }
        }
    }

    fun registerDemoWebhook() {
        viewModelScope.launch {
            repository.registerWebhook(
                RegisterM27WebhookInput("Webhook demo", "https://demo.example.com/hook", M27Environment.SANDBOX)
            ).onFailure { _uiState.value = M27ListUiState.Error(safeMessage(it)) }
        }
    }
}

class M27OAuthViewModel(private val repository: M27IntegrationRepository = DataProvider.m27IntegrationRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<M27ListUiState<M27PublicOAuthApp>>(M27ListUiState.Loading)
    val uiState: StateFlow<M27ListUiState<M27PublicOAuthApp>> = _uiState

    init {
        viewModelScope.launch {
            repository.observeOAuthApps().catch {
                _uiState.value = M27ListUiState.Error(safeMessage(it))
            }.collect {
                _uiState.value = if (it.isEmpty()) M27ListUiState.Empty else M27ListUiState.Content(it)
            }
        }
    }

    fun registerDemoApp() {
        viewModelScope.launch {
            repository.registerOAuthApp(
                RegisterM27OAuthAppInput("App demo", "https://demo.example.com/cb", listOf("adoptions_read"), M27Environment.SANDBOX)
            ).onFailure { _uiState.value = M27ListUiState.Error(safeMessage(it)) }
        }
    }
}

class M27ApiKeysViewModel(private val repository: M27IntegrationRepository = DataProvider.m27IntegrationRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<M27ListUiState<M27PublicApiKey>>(M27ListUiState.Loading)
    val uiState: StateFlow<M27ListUiState<M27PublicApiKey>> = _uiState

    init {
        viewModelScope.launch {
            repository.observeApiKeys().catch {
                _uiState.value = M27ListUiState.Error(safeMessage(it))
            }.collect {
                _uiState.value = if (it.isEmpty()) M27ListUiState.Empty else M27ListUiState.Content(it)
            }
        }
    }
}

class M27ContractsViewModel(private val repository: M27IntegrationRepository = DataProvider.m27IntegrationRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<M27ListUiState<M27PublicContract>>(M27ListUiState.Loading)
    val uiState: StateFlow<M27ListUiState<M27PublicContract>> = _uiState

    init {
        viewModelScope.launch {
            repository.observePublishedContracts().catch {
                _uiState.value = M27ListUiState.Error(safeMessage(it))
            }.collect {
                _uiState.value = if (it.isEmpty()) M27ListUiState.Empty else M27ListUiState.Content(it)
            }
        }
    }
}

class M27RateLimitsViewModel(private val repository: M27IntegrationRepository = DataProvider.m27IntegrationRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<M27ListUiState<M27PublicRateLimit>>(M27ListUiState.Loading)
    val uiState: StateFlow<M27ListUiState<M27PublicRateLimit>> = _uiState

    init {
        viewModelScope.launch {
            repository.observeRateLimits().catch {
                _uiState.value = M27ListUiState.Error(safeMessage(it))
            }.collect {
                _uiState.value = if (it.isEmpty()) M27ListUiState.Empty else M27ListUiState.Content(it)
            }
        }
    }
}
