package com.comunidapp.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.AdoptionDocumentType
import com.comunidapp.app.data.model.AdoptionInterviewType
import com.comunidapp.app.data.model.AdoptionProcessSnapshot
import com.comunidapp.app.data.model.AdoptionWelfareStatus
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.remote.supabase.m09.M09AdoptionErrorMapper
import com.comunidapp.app.data.repository.AdoptionAgreementRepository
import com.comunidapp.app.data.repository.AdoptionCompletionRepository
import com.comunidapp.app.data.repository.AdoptionDocumentRepository
import com.comunidapp.app.data.repository.AdoptionFollowUpRepository
import com.comunidapp.app.data.repository.AdoptionInterviewRepository
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class AdoptionProcessUiState {
    data object Loading : AdoptionProcessUiState()
    data object NotFound : AdoptionProcessUiState()
    data class Error(val message: String) : AdoptionProcessUiState()
    data class Content(val snapshot: AdoptionProcessSnapshot) : AdoptionProcessUiState()
}

class AdoptionProcessViewModel(
    savedStateHandle: SavedStateHandle,
    private val completionRepository: AdoptionCompletionRepository =
        DataProvider.adoptionCompletionRepository,
    private val authRepository: AuthRepository = AuthProvider.repository
) : ViewModel() {
    private val adoptionId: String = savedStateHandle["adoptionId"] ?: ""
    private val _uiState = MutableStateFlow<AdoptionProcessUiState>(AdoptionProcessUiState.Loading)
    val uiState: StateFlow<AdoptionProcessUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            if (adoptionId.isBlank()) {
                _uiState.value = AdoptionProcessUiState.NotFound
                return@launch
            }
            _uiState.value = AdoptionProcessUiState.Loading
            completionRepository.getProcessSnapshot(adoptionId)
                .onSuccess { _uiState.value = AdoptionProcessUiState.Content(it) }
                .onFailure { e ->
                    val code = M09AdoptionErrorMapper.codeOf(e)
                    _uiState.value = when (code) {
                        "ADOPTION_NOT_FOUND" -> AdoptionProcessUiState.NotFound
                        else -> AdoptionProcessUiState.Error(M09AdoptionErrorMapper.userMessage(code))
                    }
                }
        }
    }
}

class AdoptionInterviewsViewModel(
    savedStateHandle: SavedStateHandle,
    private val interviewRepository: AdoptionInterviewRepository =
        DataProvider.adoptionInterviewRepository,
    private val completionRepository: AdoptionCompletionRepository =
        DataProvider.adoptionCompletionRepository
) : ViewModel() {
    private val adoptionId: String = savedStateHandle["adoptionId"] ?: ""
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _applicationId = MutableStateFlow<String?>(null)
    val applicationId: StateFlow<String?> = _applicationId.asStateFlow()
    private var submitting = false

    val interviews = interviewRepository.observeInterviews(adoptionId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            completionRepository.getProcessSnapshot(adoptionId).onSuccess {
                _applicationId.value = it.acceptedApplication?.id
            }
        }
    }

    fun schedule(
        scheduledAt: Long,
        type: AdoptionInterviewType,
        locationOrLink: String?,
        notes: String?
    ) {
        val applicationId = _applicationId.value ?: run {
            _message.value = M09AdoptionErrorMapper.userMessage("INTERVIEW_NOT_ALLOWED")
            return
        }
        if (submitting) return
        submitting = true
        viewModelScope.launch {
            interviewRepository.scheduleInterview(
                adoptionId, applicationId, scheduledAt, type, locationOrLink, notes
            ).onFailure { e ->
                _message.value = M09AdoptionErrorMapper.userMessage(M09AdoptionErrorMapper.codeOf(e))
            }.onSuccess { _message.value = "Entrevista agendada" }
            submitting = false
        }
    }

    fun confirm(id: String) = mutate { interviewRepository.confirmInterview(id) }
    fun complete(id: String, outcome: String?) =
        mutate { interviewRepository.completeInterview(id, outcome) }
    fun cancel(id: String) = mutate { interviewRepository.cancelInterview(id) }

    private fun mutate(block: suspend () -> Result<*>) {
        if (submitting) return
        submitting = true
        viewModelScope.launch {
            block().onFailure { e ->
                _message.value = M09AdoptionErrorMapper.userMessage(M09AdoptionErrorMapper.codeOf(e))
            }
            submitting = false
        }
    }

    fun clearMessage() { _message.value = null }
}

class AdoptionInterviewDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val interviewRepository: AdoptionInterviewRepository =
        DataProvider.adoptionInterviewRepository
) : ViewModel() {
    private val interviewId: String = savedStateHandle["interviewId"] ?: ""
    private val _ui = MutableStateFlow<AdoptionProcessUiState>(AdoptionProcessUiState.Loading)
    // reuse process states lightly via Content wrapper is awkward — keep simple map
    val interview = MutableStateFlow<com.comunidapp.app.data.model.AdoptionInterview?>(null)
    val error = MutableStateFlow<String?>(null)
    val notFound = MutableStateFlow(false)
    private var submitting = false

    init { load() }

    fun load() {
        viewModelScope.launch {
            if (interviewId.isBlank()) {
                notFound.value = true
                return@launch
            }
            interviewRepository.getInterviewById(interviewId)
                .onSuccess { interview.value = it; notFound.value = false; error.value = null }
                .onFailure { e ->
                    val code = M09AdoptionErrorMapper.codeOf(e)
                    if (code == "INTERVIEW_NOT_FOUND") notFound.value = true
                    else error.value = M09AdoptionErrorMapper.userMessage(code)
                }
        }
    }

    fun confirm() = act { interviewRepository.confirmInterview(interviewId) }
    fun complete(outcome: String?) = act { interviewRepository.completeInterview(interviewId, outcome) }
    fun cancel() = act { interviewRepository.cancelInterview(interviewId) }

    private fun act(block: suspend () -> Result<com.comunidapp.app.data.model.AdoptionInterview>) {
        if (submitting) return
        submitting = true
        viewModelScope.launch {
            block().onSuccess { interview.value = it }
                .onFailure { e ->
                    error.value = M09AdoptionErrorMapper.userMessage(M09AdoptionErrorMapper.codeOf(e))
                }
            submitting = false
        }
    }
}

class AdoptionDocumentsViewModel(
    savedStateHandle: SavedStateHandle,
    private val documentRepository: AdoptionDocumentRepository =
        DataProvider.adoptionDocumentRepository,
    private val completionRepository: AdoptionCompletionRepository =
        DataProvider.adoptionCompletionRepository
) : ViewModel() {
    private val adoptionId: String = savedStateHandle["adoptionId"] ?: ""
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val _applicationId = MutableStateFlow<String?>(null)
    val applicationId: StateFlow<String?> = _applicationId.asStateFlow()
    private var submitting = false
    val documents = documentRepository.observeDocuments(adoptionId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            completionRepository.getProcessSnapshot(adoptionId).onSuccess {
                _applicationId.value = it.acceptedApplication?.id
            }
        }
    }

    fun request(type: AdoptionDocumentType, required: Boolean = true) {
        val applicationId = _applicationId.value ?: return
        if (submitting) return
        submitting = true
        viewModelScope.launch {
            documentRepository.requestDocument(adoptionId, applicationId, type, required)
                .onFailure { e ->
                    _message.value = M09AdoptionErrorMapper.userMessage(M09AdoptionErrorMapper.codeOf(e))
                }
            submitting = false
        }
    }

    fun submit(requirementId: String, storagePath: String) {
        if (submitting) return
        submitting = true
        viewModelScope.launch {
            documentRepository.submitDocumentReference(requirementId, storagePath)
                .onFailure { e ->
                    _message.value = M09AdoptionErrorMapper.userMessage(M09AdoptionErrorMapper.codeOf(e))
                }
            submitting = false
        }
    }

    fun review(requirementId: String, approve: Boolean, reason: String?) {
        if (submitting) return
        submitting = true
        viewModelScope.launch {
            documentRepository.reviewDocument(requirementId, approve, reason)
                .onFailure { e ->
                    _message.value = M09AdoptionErrorMapper.userMessage(M09AdoptionErrorMapper.codeOf(e))
                }
            submitting = false
        }
    }
}

class AdoptionAgreementViewModel(
    savedStateHandle: SavedStateHandle,
    private val agreementRepository: AdoptionAgreementRepository =
        DataProvider.adoptionAgreementRepository,
    private val completionRepository: AdoptionCompletionRepository =
        DataProvider.adoptionCompletionRepository
) : ViewModel() {
    private val adoptionId: String = savedStateHandle["adoptionId"] ?: ""
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val _applicationId = MutableStateFlow<String?>(null)
    val applicationId: StateFlow<String?> = _applicationId.asStateFlow()
    private var submitting = false
    val agreement = agreementRepository.observeAgreement(adoptionId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            completionRepository.getProcessSnapshot(adoptionId).onSuccess {
                _applicationId.value = it.acceptedApplication?.id
            }
        }
    }

    fun create(version: String, terms: String) {
        val applicationId = _applicationId.value ?: return
        if (submitting) return
        submitting = true
        viewModelScope.launch {
            agreementRepository.createAgreement(adoptionId, applicationId, version, terms)
                .onFailure { e ->
                    _message.value = M09AdoptionErrorMapper.userMessage(M09AdoptionErrorMapper.codeOf(e))
                }
            submitting = false
        }
    }

    fun accept(agreementId: String) {
        if (submitting) return
        submitting = true
        viewModelScope.launch {
            agreementRepository.acceptAgreement(agreementId)
                .onFailure { e ->
                    _message.value = M09AdoptionErrorMapper.userMessage(M09AdoptionErrorMapper.codeOf(e))
                }
            submitting = false
        }
    }
}

class AdoptionFinalizeViewModel(
    savedStateHandle: SavedStateHandle,
    private val completionRepository: AdoptionCompletionRepository =
        DataProvider.adoptionCompletionRepository
) : ViewModel() {
    private val adoptionId: String = savedStateHandle["adoptionId"] ?: ""
    private val _uiState = MutableStateFlow<AdoptionProcessUiState>(AdoptionProcessUiState.Loading)
    val uiState = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val events: SharedFlow<String> = _events.asSharedFlow()
    private var submitting = false

    init { load() }

    fun load() {
        viewModelScope.launch {
            if (adoptionId.isBlank()) {
                _uiState.value = AdoptionProcessUiState.NotFound
                return@launch
            }
            _uiState.value = AdoptionProcessUiState.Loading
            completionRepository.getProcessSnapshot(adoptionId)
                .onSuccess { _uiState.value = AdoptionProcessUiState.Content(it) }
                .onFailure { e ->
                    val code = M09AdoptionErrorMapper.codeOf(e)
                    _uiState.value = when (code) {
                        "ADOPTION_NOT_FOUND" -> AdoptionProcessUiState.NotFound
                        else -> AdoptionProcessUiState.Error(M09AdoptionErrorMapper.userMessage(code))
                    }
                }
        }
    }

    fun finalizeAdoption() {
        if (submitting) return
        submitting = true
        viewModelScope.launch {
            completionRepository.finalizeAdoption(adoptionId)
                .onSuccess {
                    _events.tryEmit("Adopción finalizada")
                    load()
                }
                .onFailure { e ->
                    _events.tryEmit(M09AdoptionErrorMapper.userMessage(M09AdoptionErrorMapper.codeOf(e)))
                }
            submitting = false
        }
    }
}

class AdoptionFollowUpViewModel(
    savedStateHandle: SavedStateHandle,
    private val followUpRepository: AdoptionFollowUpRepository =
        DataProvider.adoptionFollowUpRepository
) : ViewModel() {
    private val adoptionId: String = savedStateHandle["adoptionId"] ?: ""
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private var submitting = false

    val plan = followUpRepository.observePlan(adoptionId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val checks = followUpRepository.observeChecks(adoptionId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun complete(checkId: String, notes: String?, welfare: AdoptionWelfareStatus) {
        if (submitting) return
        submitting = true
        viewModelScope.launch {
            followUpRepository.completeCheck(checkId, notes, welfare, null)
                .onFailure { e ->
                    _message.value = M09AdoptionErrorMapper.userMessage(M09AdoptionErrorMapper.codeOf(e))
                }
            submitting = false
        }
    }
}

class AdoptionFollowUpCheckDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val followUpRepository: AdoptionFollowUpRepository =
        DataProvider.adoptionFollowUpRepository
) : ViewModel() {
    private val checkId: String = savedStateHandle["checkId"] ?: ""
    val check = MutableStateFlow<com.comunidapp.app.data.model.AdoptionFollowUpCheck?>(null)
    val error = MutableStateFlow<String?>(null)
    val notFound = MutableStateFlow(false)
    private var submitting = false

    init { load() }

    fun load() {
        viewModelScope.launch {
            if (checkId.isBlank()) {
                notFound.value = true
                return@launch
            }
            followUpRepository.getCheckById(checkId)
                .onSuccess { check.value = it; notFound.value = false }
                .onFailure { e ->
                    val code = M09AdoptionErrorMapper.codeOf(e)
                    if (code == "FOLLOWUP_NOT_FOUND") notFound.value = true
                    else error.value = M09AdoptionErrorMapper.userMessage(code)
                }
        }
    }

    fun complete(notes: String?, welfare: AdoptionWelfareStatus) {
        if (submitting) return
        submitting = true
        viewModelScope.launch {
            followUpRepository.completeCheck(checkId, notes, welfare, null)
                .onSuccess { check.value = it }
                .onFailure { e ->
                    error.value = M09AdoptionErrorMapper.userMessage(M09AdoptionErrorMapper.codeOf(e))
                }
            submitting = false
        }
    }
}
