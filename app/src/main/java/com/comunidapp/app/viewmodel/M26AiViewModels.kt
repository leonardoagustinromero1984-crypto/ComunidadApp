package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.M26AssistanceTopic
import com.comunidapp.app.data.model.M26PublicAssistanceSession
import com.comunidapp.app.data.model.M26PublicDuplicateCandidate
import com.comunidapp.app.data.model.M26PublicRecommendation
import com.comunidapp.app.data.model.M26PublicAiResultSummary
import com.comunidapp.app.data.model.M26PublicReviewQueueItem
import com.comunidapp.app.data.model.M26ReviewDecision
import com.comunidapp.app.data.model.ReviewM26AiResultInput
import com.comunidapp.app.data.model.M26PublicVisualMatch
import com.comunidapp.app.data.model.M26RecommendationKind
import com.comunidapp.app.data.model.StartM26AssistanceInput
import com.comunidapp.app.data.model.SubmitM26RecommendationInput
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.M26AiRepository
import com.comunidapp.app.domain.m26.M26AiResilience
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

sealed class M26HubUiState {
    data object Loading : M26HubUiState()
    data class Content(val matchCount: Int, val duplicateCount: Int, val recommendationCount: Int, val jobCount: Int) : M26HubUiState()
    data object Empty : M26HubUiState()
    data class Error(val message: String) : M26HubUiState()
}

class M26HubViewModel(private val repository: M26AiRepository = DataProvider.m26AiRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<M26HubUiState>(M26HubUiState.Loading)
    val uiState: StateFlow<M26HubUiState> = _uiState

    init {
        viewModelScope.launch {
            combine(
                repository.observeVisualMatches(),
                repository.observeDuplicateCandidates(),
                repository.observeEligibleRecommendations(),
                repository.observeMyJobs()
            ) { matches, duplicates, recommendations, jobs ->
                listOf(matches.size, duplicates.size, recommendations.size, jobs.size)
            }.catch { _uiState.value = M26HubUiState.Error(M26AiResilience.safeUserMessage(it)) }
                .collect { counts ->
                    val (matches, duplicates, recommendations, jobs) = counts
                    _uiState.value = if (matches == 0 && duplicates == 0 && recommendations == 0 && jobs == 0) {
                        M26HubUiState.Empty
                    } else {
                        M26HubUiState.Content(matches, duplicates, recommendations, jobs)
                    }
                }
        }
    }
}

sealed class M26VisualMatchingUiState {
    data object Loading : M26VisualMatchingUiState()
    data class Content(val items: List<M26PublicVisualMatch>) : M26VisualMatchingUiState()
    data object Empty : M26VisualMatchingUiState()
    data class Error(val message: String) : M26VisualMatchingUiState()
}

class M26VisualMatchingViewModel(private val repository: M26AiRepository = DataProvider.m26AiRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<M26VisualMatchingUiState>(M26VisualMatchingUiState.Loading)
    val uiState: StateFlow<M26VisualMatchingUiState> = _uiState

    init {
        viewModelScope.launch {
            repository.observeVisualMatches().catch {
                _uiState.value = M26VisualMatchingUiState.Error(M26AiResilience.safeUserMessage(it))
            }.collect {
                _uiState.value = if (it.isEmpty()) M26VisualMatchingUiState.Empty else M26VisualMatchingUiState.Content(it)
            }
        }
    }
}

sealed class M26DuplicatesUiState {
    data object Loading : M26DuplicatesUiState()
    data class Content(val items: List<M26PublicDuplicateCandidate>) : M26DuplicatesUiState()
    data object Empty : M26DuplicatesUiState()
    data class Error(val message: String) : M26DuplicatesUiState()
}

class M26DuplicatesViewModel(private val repository: M26AiRepository = DataProvider.m26AiRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<M26DuplicatesUiState>(M26DuplicatesUiState.Loading)
    val uiState: StateFlow<M26DuplicatesUiState> = _uiState

    init {
        viewModelScope.launch {
            repository.observeDuplicateCandidates().catch {
                _uiState.value = M26DuplicatesUiState.Error(M26AiResilience.safeUserMessage(it))
            }.collect {
                _uiState.value = if (it.isEmpty()) M26DuplicatesUiState.Empty else M26DuplicatesUiState.Content(it)
            }
        }
    }
}

sealed class M26AssistanceUiState {
    data object Loading : M26AssistanceUiState()
    data class Content(val sessions: List<M26PublicAssistanceSession>) : M26AssistanceUiState()
    data object Empty : M26AssistanceUiState()
    data class Error(val message: String) : M26AssistanceUiState()
}

class M26AssistanceViewModel(private val repository: M26AiRepository = DataProvider.m26AiRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<M26AssistanceUiState>(M26AssistanceUiState.Loading)
    val uiState: StateFlow<M26AssistanceUiState> = _uiState

    init {
        viewModelScope.launch {
            repository.observeAssistanceSessions().catch {
                _uiState.value = M26AssistanceUiState.Error(M26AiResilience.safeUserMessage(it))
            }.collect {
                _uiState.value = if (it.isEmpty()) M26AssistanceUiState.Empty else M26AssistanceUiState.Content(it)
            }
        }
    }

    fun startStubSession() {
        viewModelScope.launch {
            repository.startAssistanceSession(
                StartM26AssistanceInput(M26AssistanceTopic.GENERAL, "Consulta general sobre funciones de LeoVer.")
            ).exceptionOrNull()?.let {
                _uiState.value = M26AssistanceUiState.Error(M26AiResilience.safeUserMessage(it))
            }
        }
    }
}

sealed class M26RecommendationsUiState {
    data object Loading : M26RecommendationsUiState()
    data class Content(val items: List<M26PublicRecommendation>) : M26RecommendationsUiState()
    data object Empty : M26RecommendationsUiState()
    data class Error(val message: String) : M26RecommendationsUiState()
}

class M26RecommendationsViewModel(private val repository: M26AiRepository = DataProvider.m26AiRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<M26RecommendationsUiState>(M26RecommendationsUiState.Loading)
    val uiState: StateFlow<M26RecommendationsUiState> = _uiState

    init {
        viewModelScope.launch {
            repository.observeEligibleRecommendations().catch {
                _uiState.value = M26RecommendationsUiState.Error(M26AiResilience.safeUserMessage(it))
            }.collect {
                _uiState.value = if (it.isEmpty()) M26RecommendationsUiState.Empty else M26RecommendationsUiState.Content(it)
            }
        }
    }

    fun submitSample() {
        viewModelScope.launch {
            repository.submitRecommendation(
                SubmitM26RecommendationInput(
                    M26RecommendationKind.CONTENT,
                    "Guía de bienestar animal",
                    "Contenido sugerido para revisión humana antes de mostrarse."
                )
            ).exceptionOrNull()?.let {
                _uiState.value = M26RecommendationsUiState.Error(M26AiResilience.safeUserMessage(it))
            }
        }
    }
}

object M26ViewModelFactories {
    private fun factory(create: () -> ViewModel): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
    }
}

sealed class M26HistoryUiState {
    data object Loading : M26HistoryUiState()
    data class Content(val items: List<M26PublicAiResultSummary>) : M26HistoryUiState()
    data object Empty : M26HistoryUiState()
    data class Error(val message: String) : M26HistoryUiState()
}

class M26HistoryViewModel(private val repository: M26AiRepository = DataProvider.m26AiRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<M26HistoryUiState>(M26HistoryUiState.Loading)
    val uiState: StateFlow<M26HistoryUiState> = _uiState

    init {
        viewModelScope.launch {
            repository.observeMyResults().catch {
                _uiState.value = M26HistoryUiState.Error(M26AiResilience.safeUserMessage(it))
            }.collect {
                _uiState.value = if (it.isEmpty()) M26HistoryUiState.Empty else M26HistoryUiState.Content(it)
            }
        }
    }
}

sealed class M26ReviewQueueUiState {
    data object Loading : M26ReviewQueueUiState()
    data class Content(val items: List<M26PublicReviewQueueItem>) : M26ReviewQueueUiState()
    data object Empty : M26ReviewQueueUiState()
    data class Error(val message: String) : M26ReviewQueueUiState()
}

class M26ReviewQueueViewModel(private val repository: M26AiRepository = DataProvider.m26AiRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<M26ReviewQueueUiState>(M26ReviewQueueUiState.Loading)
    val uiState: StateFlow<M26ReviewQueueUiState> = _uiState

    init {
        viewModelScope.launch {
            repository.observeReviewQueue().catch {
                _uiState.value = M26ReviewQueueUiState.Error(M26AiResilience.safeUserMessage(it))
            }.collect {
                _uiState.value = if (it.isEmpty()) M26ReviewQueueUiState.Empty else M26ReviewQueueUiState.Content(it)
            }
        }
    }

    fun approve(resultId: String) = review(resultId, M26ReviewDecision.APPROVED)

    fun reject(resultId: String) = review(resultId, M26ReviewDecision.REJECTED)

    private fun review(resultId: String, decision: M26ReviewDecision) {
        viewModelScope.launch {
            repository.reviewResult(ReviewM26AiResultInput(resultId, decision, null)).exceptionOrNull()?.let {
                _uiState.value = M26ReviewQueueUiState.Error(M26AiResilience.safeUserMessage(it))
            }
        }
    }
}
