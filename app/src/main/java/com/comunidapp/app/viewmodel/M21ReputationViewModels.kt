package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.M21PublicReputationSummary
import com.comunidapp.app.data.model.M21PublicReview
import com.comunidapp.app.data.model.M21PublicVerification
import com.comunidapp.app.data.model.M21ReviewTargetType
import com.comunidapp.app.data.model.SubmitM21ReviewInput
import com.comunidapp.app.data.model.SubmitM21VerificationInput
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.remote.supabase.m21.M21ReputationErrorMapper
import com.comunidapp.app.data.repository.M21ReputationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

sealed class M21HubUiState {
    data object Loading : M21HubUiState()
    data class Content(val summary: M21PublicReputationSummary) : M21HubUiState()
    data class Error(val message: String) : M21HubUiState()
}

class M21HubViewModel(
    private val repository: M21ReputationRepository = DataProvider.m21ReputationRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<M21HubUiState>(M21HubUiState.Loading)
    val uiState: StateFlow<M21HubUiState> = _uiState

    init {
        viewModelScope.launch {
            repository.observeMySummary()
                .catch { e ->
                    _uiState.value = M21HubUiState.Error(
                        M21ReputationErrorMapper.userMessage(M21ReputationErrorMapper.codeOf(e) ?: "M21_PERMISSION_DENIED")
                    )
                }
                .collect { summary ->
                    _uiState.value = M21HubUiState.Content(summary)
                }
        }
    }
}

sealed class M21ReviewsUiState {
    data object Loading : M21ReviewsUiState()
    data class Content(val items: List<M21PublicReview>) : M21ReviewsUiState()
    data class Error(val message: String) : M21ReviewsUiState()
}

class M21ReviewsViewModel(
    private val repository: M21ReputationRepository = DataProvider.m21ReputationRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<M21ReviewsUiState>(M21ReviewsUiState.Loading)
    val uiState: StateFlow<M21ReviewsUiState> = _uiState

    init {
        viewModelScope.launch {
            repository.observeMyReviews()
                .catch { e ->
                    _uiState.value = M21ReviewsUiState.Error(
                        M21ReputationErrorMapper.userMessage(M21ReputationErrorMapper.codeOf(e) ?: "M21_PERMISSION_DENIED")
                    )
                }
                .collect { items ->
                    _uiState.value = M21ReviewsUiState.Content(items)
                }
        }
    }

    fun submitDemoReview(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.submitReview(
                SubmitM21ReviewInput(
                    targetType = M21ReviewTargetType.SERVICE,
                    targetId = "mock_service_m21_demo",
                    targetDisplayLabel = "Servicio demo M21",
                    rating = 5,
                    content = "Reseña de prueba desde la app."
                )
            ).onSuccess { onDone() }
        }
    }
}

sealed class M21VerificationsUiState {
    data object Loading : M21VerificationsUiState()
    data class Content(val items: List<M21PublicVerification>) : M21VerificationsUiState()
    data class Error(val message: String) : M21VerificationsUiState()
}

class M21VerificationsViewModel(
    private val repository: M21ReputationRepository = DataProvider.m21ReputationRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<M21VerificationsUiState>(M21VerificationsUiState.Loading)
    val uiState: StateFlow<M21VerificationsUiState> = _uiState

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = M21VerificationsUiState.Loading
            repository.getMyVerifications()
                .onSuccess { _uiState.value = M21VerificationsUiState.Content(it) }
                .onFailure { e ->
                    _uiState.value = M21VerificationsUiState.Error(
                        M21ReputationErrorMapper.userMessage(M21ReputationErrorMapper.codeOf(e) ?: "M21_PERMISSION_DENIED")
                    )
                }
        }
    }

    fun submitIdentityVerification() {
        viewModelScope.launch {
            repository.submitVerification(
                SubmitM21VerificationInput(
                    verificationType = com.comunidapp.app.data.model.M21VerificationType.IDENTITY,
                    displayLabel = "Verificación de identidad"
                )
            ).onSuccess { refresh() }
        }
    }
}

object M21ViewModelFactories {
    fun hubFactory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = M21HubViewModel() as T
    }

    fun reviewsFactory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = M21ReviewsViewModel() as T
    }

    fun verificationsFactory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = M21VerificationsViewModel() as T
    }
}
