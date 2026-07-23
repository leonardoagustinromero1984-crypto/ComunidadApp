package com.comunidapp.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.AdoptionMatch
import com.comunidapp.app.data.model.AdoptionPost
import com.comunidapp.app.data.model.AdoptionRequest
import com.comunidapp.app.data.model.AdoptionRequestStatus
import com.comunidapp.app.data.model.AdoptionStatus
import com.comunidapp.app.data.model.NotificationType
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.remote.supabase.m09.M09AdoptionErrorMapper
import com.comunidapp.app.data.repository.AdoptionRepository
import com.comunidapp.app.data.repository.AdoptionRequestRepository
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.PlatformRepository
import com.comunidapp.app.data.repository.UserRepository
import com.comunidapp.app.notifications.NotificationDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class AdoptionDetailUiState {
    data object Loading : AdoptionDetailUiState()
    data object NotFound : AdoptionDetailUiState()
    data class Error(val message: String) : AdoptionDetailUiState()
    data class Content(
        val post: AdoptionPost,
        val isOwner: Boolean,
        val actionInFlight: Boolean = false,
        val actionMessage: String? = null
    ) : AdoptionDetailUiState()
}

class AdoptionDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val adoptionRepository: AdoptionRepository = DataProvider.adoptionRepository,
    private val requestRepository: AdoptionRequestRepository = DataProvider.adoptionRequestRepository,
    private val authRepository: AuthRepository = AuthProvider.repository
) : ViewModel() {

    private val adoptionId: String = savedStateHandle["adoptionId"] ?: ""

    private val _uiState = MutableStateFlow<AdoptionDetailUiState>(AdoptionDetailUiState.Loading)
    val uiState: StateFlow<AdoptionDetailUiState> = _uiState.asStateFlow()

    private val _post = MutableStateFlow<AdoptionPost?>(null)
    val post: StateFlow<AdoptionPost?> = _post.asStateFlow()

    private val _showRequestDialog = MutableStateFlow(false)
    val showRequestDialog: StateFlow<Boolean> = _showRequestDialog.asStateFlow()

    private val _requestState = MutableStateFlow<RequestUiState>(RequestUiState.Idle)
    val requestState: StateFlow<RequestUiState> = _requestState.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private var submitting = false

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            if (adoptionId.isBlank()) {
                _uiState.value = AdoptionDetailUiState.NotFound
                _post.value = null
                return@launch
            }
            _uiState.value = AdoptionDetailUiState.Loading
            adoptionRepository.getAdoptionById(adoptionId)
                .onSuccess { adoption ->
                    _post.value = adoption
                    val userId = authRepository.getCurrentUser()?.id
                    _uiState.value = AdoptionDetailUiState.Content(
                        post = adoption,
                        isOwner = userId != null &&
                            (adoption.publisherId == userId || adoption.shelterId == userId)
                    )
                }
                .onFailure { error ->
                    val code = M09AdoptionErrorMapper.codeOf(error)
                    _post.value = null
                    _uiState.value = when (code) {
                        "ADOPTION_NOT_FOUND" -> AdoptionDetailUiState.NotFound
                        else -> AdoptionDetailUiState.Error(M09AdoptionErrorMapper.userMessage(code))
                    }
                }
        }
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
        val user = authRepository.getCurrentUser() ?: return
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

    fun pause() = runOwnerAction { adoptionRepository.pauseAdoption(adoptionId) }
    fun resume() = runOwnerAction { adoptionRepository.resumeAdoption(adoptionId) }
    fun close() = runOwnerAction { adoptionRepository.closeAdoption(adoptionId) }
    fun markAdopted() = runOwnerAction { adoptionRepository.markAsAdopted(adoptionId) }

    private fun runOwnerAction(block: suspend () -> Result<AdoptionPost>) {
        if (submitting) return
        val current = _uiState.value as? AdoptionDetailUiState.Content ?: return
        if (!current.isOwner) {
            _events.tryEmit(M09AdoptionErrorMapper.userMessage("FORBIDDEN"))
            return
        }
        submitting = true
        viewModelScope.launch {
            _uiState.value = current.copy(actionInFlight = true, actionMessage = null)
            block()
                .onSuccess { post ->
                    _post.value = post
                    _uiState.value = current.copy(
                        post = post,
                        actionInFlight = false,
                        actionMessage = null
                    )
                    submitting = false
                }
                .onFailure { error ->
                    val msg = M09AdoptionErrorMapper.userMessage(
                        M09AdoptionErrorMapper.codeOf(error)
                    )
                    _uiState.value = current.copy(actionInFlight = false, actionMessage = msg)
                    _events.tryEmit(msg)
                    submitting = false
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

sealed class MyAdoptionsUiState {
    data object Loading : MyAdoptionsUiState()
    data object Empty : MyAdoptionsUiState()
    data class Content(val posts: List<AdoptionPost>) : MyAdoptionsUiState()
    data class Error(val message: String) : MyAdoptionsUiState()
}

class MyAdoptionsViewModel(
    private val adoptionRepository: AdoptionRepository = DataProvider.adoptionRepository,
    private val requestRepository: AdoptionRequestRepository = DataProvider.adoptionRequestRepository,
    private val platformRepository: PlatformRepository = DataProvider.platformRepository,
    private val userRepository: UserRepository = DataProvider.userRepository,
    private val authRepository: AuthRepository = AuthProvider.repository
) : ViewModel() {

    private val publisherId get() = authRepository.getCurrentUser()?.id.orEmpty()

    private val _matches = MutableStateFlow<List<AdoptionMatch>>(emptyList())
    val matches: StateFlow<List<AdoptionMatch>> = _matches.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var submitting = false

    val myAdoptions: StateFlow<List<AdoptionPost>> =
        adoptionRepository.observeMyAdoptions(publisherId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiState: StateFlow<MyAdoptionsUiState> = myAdoptions
        .map { list ->
            if (list.isEmpty()) MyAdoptionsUiState.Empty
            else MyAdoptionsUiState.Content(list)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MyAdoptionsUiState.Loading)

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
        if (submitting) return
        submitting = true
        viewModelScope.launch {
            val result = when (status) {
                AdoptionStatus.PAUSED -> adoptionRepository.pauseAdoption(adoptionId)
                AdoptionStatus.PUBLISHED -> adoptionRepository.resumeAdoption(adoptionId)
                AdoptionStatus.CLOSED -> adoptionRepository.closeAdoption(adoptionId)
                AdoptionStatus.ADOPTED -> adoptionRepository.markAsAdopted(adoptionId)
                AdoptionStatus.DRAFT -> adoptionRepository.updateAdoptionStatus(adoptionId, status)
            }
            result.onFailure { error ->
                _message.value = M09AdoptionErrorMapper.userMessage(
                    M09AdoptionErrorMapper.codeOf(error)
                )
            }
            submitting = false
        }
    }

    fun pause(id: String) = updateStatus(id, AdoptionStatus.PAUSED)
    fun resume(id: String) = updateStatus(id, AdoptionStatus.PUBLISHED)
    fun close(id: String) = updateStatus(id, AdoptionStatus.CLOSED)
    fun markAdopted(id: String) = updateStatus(id, AdoptionStatus.ADOPTED)

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
