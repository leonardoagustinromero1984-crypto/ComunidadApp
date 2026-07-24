package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.M13MatchCandidate
import com.comunidapp.app.data.model.M13MatchDecisionType
import com.comunidapp.app.data.model.M13Sighting
import com.comunidapp.app.data.model.M13SightingPublic
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.remote.supabase.m13.M13ErrorMapper
import com.comunidapp.app.data.repository.CreateM13SightingInput
import com.comunidapp.app.data.repository.M13MatchRepository
import com.comunidapp.app.data.repository.M13SightingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

sealed class M13SightingListUiState {
    data object Loading : M13SightingListUiState()
    data object Empty : M13SightingListUiState()
    data class Content(val items: List<M13SightingPublic>) : M13SightingListUiState()
    data class Error(val message: String) : M13SightingListUiState()
}

class M13SightingListViewModel(
    private val repository: M13SightingRepository = DataProvider.m13SightingRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<M13SightingListUiState>(M13SightingListUiState.Loading)
    val uiState: StateFlow<M13SightingListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observePublicSightings()
                .catch { e ->
                    _uiState.value = M13SightingListUiState.Error(M13ErrorMapper.userMessage(M13ErrorMapper.codeOf(e)))
                }
                .collect { list ->
                    _uiState.value = if (list.isEmpty()) {
                        M13SightingListUiState.Empty
                    } else {
                        M13SightingListUiState.Content(list)
                    }
                }
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M13SightingListViewModel() as T
        }
    }
}

class M13SightingCreateViewModel(
    private val sightingRepository: M13SightingRepository = DataProvider.m13SightingRepository,
    private val matchRepository: M13MatchRepository = DataProvider.m13MatchRepository
) : ViewModel() {
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _createdId = MutableStateFlow<String?>(null)
    val createdId: StateFlow<String?> = _createdId.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun create(
        caseId: String?,
        species: PetSpecies,
        primaryColor: String,
        zoneText: String,
        description: String,
        breedText: String? = null,
        sex: PetSex? = null,
        size: PetSize? = null,
        latitudeApprox: Double? = null,
        longitudeApprox: Double? = null,
        mediaRefs: List<String> = emptyList()
    ) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val result = sightingRepository.createSighting(
                CreateM13SightingInput(
                    lostFoundCaseId = caseId,
                    species = species,
                    breedText = breedText,
                    primaryColor = primaryColor,
                    sex = sex,
                    size = size,
                    observedAt = System.currentTimeMillis(),
                    zoneText = zoneText,
                    latitudeApprox = latitudeApprox,
                    longitudeApprox = longitudeApprox,
                    description = description,
                    mediaRefs = mediaRefs
                )
            )
            result.onSuccess { sighting ->
                matchRepository.recalculateForSighting(sighting.id)
                _createdId.value = sighting.id
                _message.value = "Avistamiento registrado"
            }.onFailure { e ->
                _message.value = M13ErrorMapper.userMessage(M13ErrorMapper.codeOf(e))
            }
            _busy.value = false
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M13SightingCreateViewModel() as T
        }
    }
}

sealed class M13SightingDetailUiState {
    data object Loading : M13SightingDetailUiState()
    data class Public(val item: M13SightingPublic) : M13SightingDetailUiState()
    data class Owner(val item: M13Sighting) : M13SightingDetailUiState()
    data class Error(val message: String) : M13SightingDetailUiState()
}

class M13SightingDetailViewModel(
    private val sightingId: String,
    private val repository: M13SightingRepository = DataProvider.m13SightingRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<M13SightingDetailUiState>(M13SightingDetailUiState.Loading)
    val uiState: StateFlow<M13SightingDetailUiState> = _uiState.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = M13SightingDetailUiState.Loading
            val result = repository.getSighting(sightingId, forPublic = false)
            result.onSuccess { value ->
                _uiState.value = when (value) {
                    is M13Sighting -> M13SightingDetailUiState.Owner(value)
                    is M13SightingPublic -> M13SightingDetailUiState.Public(value)
                    else -> M13SightingDetailUiState.Error("Estado desconocido")
                }
            }.onFailure { e ->
                _uiState.value = M13SightingDetailUiState.Error(
                    M13ErrorMapper.userMessage(M13ErrorMapper.codeOf(e))
                )
            }
        }
    }

    fun withdraw() {
        viewModelScope.launch {
            repository.withdrawSighting(sightingId)
                .onSuccess {
                    _message.value = "Avistamiento retirado"
                    refresh()
                }
                .onFailure { e ->
                    _message.value = M13ErrorMapper.userMessage(M13ErrorMapper.codeOf(e))
                }
        }
    }

    companion object {
        fun factory(sightingId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    M13SightingDetailViewModel(sightingId) as T
            }
    }
}

sealed class M13CaseMatchesUiState {
    data object Loading : M13CaseMatchesUiState()
    data object Empty : M13CaseMatchesUiState()
    data class Content(val items: List<M13MatchCandidate>) : M13CaseMatchesUiState()
    data class Error(val message: String) : M13CaseMatchesUiState()
}

class M13CaseMatchesViewModel(
    private val caseId: String,
    private val matchRepository: M13MatchRepository = DataProvider.m13MatchRepository,
    private val sightingRepository: M13SightingRepository = DataProvider.m13SightingRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<M13CaseMatchesUiState>(M13CaseMatchesUiState.Loading)
    val uiState: StateFlow<M13CaseMatchesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Recalcular candidatos activos vinculados o visibles para el caso.
            sightingRepository.observePublicSightings().collect { /* keep warm */ }
        }
        viewModelScope.launch {
            matchRepository.observeMatchesForCase(caseId)
                .catch { e ->
                    _uiState.value = M13CaseMatchesUiState.Error(
                        M13ErrorMapper.userMessage(M13ErrorMapper.codeOf(e))
                    )
                }
                .collect { list ->
                    _uiState.value = if (list.isEmpty()) {
                        M13CaseMatchesUiState.Empty
                    } else {
                        M13CaseMatchesUiState.Content(list)
                    }
                }
        }
    }

    companion object {
        fun factory(caseId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    M13CaseMatchesViewModel(caseId) as T
            }
    }
}

class M13MatchDetailViewModel(
    private val candidateId: String,
    private val matchRepository: M13MatchRepository = DataProvider.m13MatchRepository
) : ViewModel() {
    private val _candidate = MutableStateFlow<M13MatchCandidate?>(null)
    val candidate: StateFlow<M13MatchCandidate?> = _candidate.asStateFlow()
    private val _decisions = MutableStateFlow<List<com.comunidapp.app.data.model.M13MatchDecision>>(emptyList())
    val decisions: StateFlow<List<com.comunidapp.app.data.model.M13MatchDecision>> = _decisions.asStateFlow()
    private val _history =
        MutableStateFlow<List<com.comunidapp.app.data.model.M13MatchStatusHistoryEntry>>(emptyList())
    val history: StateFlow<List<com.comunidapp.app.data.model.M13MatchStatusHistoryEntry>> =
        _history.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init {
        viewModelScope.launch {
            matchRepository.observeMatch(candidateId).collect { _candidate.value = it }
        }
        viewModelScope.launch {
            matchRepository.observeDecisions(candidateId).collect { _decisions.value = it }
        }
        viewModelScope.launch {
            matchRepository.observeStatusHistory(candidateId).collect { _history.value = it }
        }
    }

    fun openReview() = runAction { matchRepository.openReview(candidateId) }

    fun decide(decision: M13MatchDecisionType, reasonCode: String) = runAction {
        matchRepository.decide(candidateId, decision, reasonCode)
            .onSuccess { _message.value = "Decisión registrada: ${decision.name}" }
    }

    fun withdraw() = runAction {
        matchRepository.withdrawMatch(candidateId)
            .onSuccess { _message.value = "Coincidencia retirada" }
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun runAction(block: suspend () -> Result<*>) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            block().onFailure { e ->
                _message.value = M13ErrorMapper.userMessage(M13ErrorMapper.codeOf(e))
            }
            _busy.value = false
        }
    }

    companion object {
        fun factory(candidateId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    M13MatchDetailViewModel(candidateId) as T
            }
    }
}
