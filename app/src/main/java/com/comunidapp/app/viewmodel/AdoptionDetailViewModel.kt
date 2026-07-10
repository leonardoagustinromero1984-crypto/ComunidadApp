package com.comunidapp.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.AdoptionMatch
import com.comunidapp.app.data.model.AdoptionPost
import com.comunidapp.app.data.model.AdoptionRequest
import com.comunidapp.app.data.model.AdoptionRequestStatus
import com.comunidapp.app.data.model.AdoptionStatus
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AdoptionRepository
import com.comunidapp.app.data.repository.AdoptionRequestRepository
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.PlatformRepository
import com.comunidapp.app.data.repository.UserRepository
import com.comunidapp.app.data.model.NotificationType
import com.comunidapp.app.notifications.NotificationDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AdoptionDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val adoptionRepository: AdoptionRepository = DataProvider.adoptionRepository,
    private val requestRepository: AdoptionRequestRepository = DataProvider.adoptionRequestRepository
) : ViewModel() {

    private val adoptionId: String = savedStateHandle["adoptionId"] ?: ""

    private val _post = MutableStateFlow<AdoptionPost?>(null)
    val post: StateFlow<AdoptionPost?> = _post.asStateFlow()

    private val _showRequestDialog = MutableStateFlow(false)
    val showRequestDialog: StateFlow<Boolean> = _showRequestDialog.asStateFlow()

    private val _requestState = MutableStateFlow<RequestUiState>(RequestUiState.Idle)
    val requestState: StateFlow<RequestUiState> = _requestState.asStateFlow()

    init {
        _post.value = adoptionRepository.getAdoptionPostById(adoptionId)
    }

    fun openRequestDialog() {
        _showRequestDialog.value = true
    }

    fun closeRequestDialog() {
        _showRequestDialog.value = false
        _requestState.value = RequestUiState.Idle
    }

    fun submitRequest(message: String, phone: String?) {
        val adoption = _post.value ?: return
        val user = AuthProvider.repository.getCurrentUser() ?: return
        if (message.isBlank()) {
            _requestState.value = RequestUiState.Error("Escribí un mensaje para el publicador")
            return
        }
        viewModelScope.launch {
            _requestState.value = RequestUiState.Loading
            requestRepository.submitRequest(
                AdoptionRequest(
                    id = "",
                    adoptionId = adoption.id,
                    applicantId = user.id,
                    applicantName = user.name,
                    message = message.trim(),
                    phone = phone?.trim()?.ifBlank { null }
                )
            ).onSuccess {
                _requestState.value = RequestUiState.Success
                val publisherId = adoption.publisherId
                if (!publisherId.isNullOrBlank()) {
                    NotificationDispatcher.notify(
                        userId = publisherId,
                        type = NotificationType.ADOPTION_REQUEST,
                        title = "Nueva solicitud de adopción",
                        body = "${user.name} postuló a ${adoption.name}",
                        relatedId = adoption.id,
                        relatedType = "adoption"
                    )
                }
            }.onFailure { error ->
                _requestState.value = RequestUiState.Error(error.message ?: "Error al enviar")
            }
        }
    }
}

sealed class RequestUiState {
    data object Idle : RequestUiState()
    data object Loading : RequestUiState()
    data object Success : RequestUiState()
    data class Error(val message: String) : RequestUiState()
}

class MyAdoptionsViewModel(
    private val adoptionRepository: AdoptionRepository = DataProvider.adoptionRepository,
    private val requestRepository: AdoptionRequestRepository = DataProvider.adoptionRequestRepository,
    private val platformRepository: PlatformRepository = DataProvider.platformRepository,
    private val userRepository: UserRepository = DataProvider.userRepository
) : ViewModel() {

    private val publisherId = AuthProvider.repository.getCurrentUser()?.id.orEmpty()

    private val _matches = MutableStateFlow<List<AdoptionMatch>>(emptyList())
    val matches: StateFlow<List<AdoptionMatch>> = _matches.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val myAdoptions: StateFlow<List<AdoptionPost>> =
        adoptionRepository.observeMyAdoptions(publisherId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val requests: StateFlow<List<AdoptionRequest>> =
        requestRepository.observeRequestsForPublisher(publisherId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            combine(myAdoptions, userRepository.observeUsers()) { adoptions, users ->
                adoptions.firstOrNull() to users.filter { it.id != publisherId }
            }.collect { (first, candidates) ->
                _matches.value = if (first == null) {
                    emptyList()
                } else {
                    platformRepository.computeAdoptionMatches(first.id, candidates)
                }
            }
        }
    }

    fun updateStatus(adoptionId: String, status: AdoptionStatus) {
        viewModelScope.launch {
            adoptionRepository.updateAdoptionStatus(adoptionId, status)
        }
    }

    fun respondToRequest(requestId: String, accept: Boolean) {
        viewModelScope.launch {
            requestRepository.updateRequestStatus(
                requestId,
                if (accept) AdoptionRequestStatus.ACCEPTED else AdoptionRequestStatus.REJECTED
            )
        }
    }

    fun scheduleInterview(requestId: String, dateText: String, notes: String) {
        if (dateText.isBlank()) {
            _message.value = "Indicá fecha y hora de la entrevista"
            return
        }
        viewModelScope.launch {
            requestRepository.scheduleInterview(requestId, dateText.trim(), notes.trim())
                .onSuccess { _message.value = "Entrevista agendada" }
                .onFailure { error ->
                    _message.value = error.message ?: "No se pudo agendar la entrevista"
                }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
