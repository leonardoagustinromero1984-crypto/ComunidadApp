package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.CheckM21EligibilityInput
import com.comunidapp.app.data.model.EditM21ReviewInput
import com.comunidapp.app.data.model.M21PublicReputationSummary
import com.comunidapp.app.data.model.M21PublicReview
import com.comunidapp.app.data.model.M21PublicVerification
import com.comunidapp.app.data.model.M21ReviewContextReference
import com.comunidapp.app.data.model.M21ReviewContextType
import com.comunidapp.app.data.model.M21ReviewDisputeReason
import com.comunidapp.app.data.model.M21ReviewEligibility
import com.comunidapp.app.data.model.M21ReviewTargetType
import com.comunidapp.app.data.model.M21ReputationBreakdown
import com.comunidapp.app.data.model.ReportM21ReviewInput
import com.comunidapp.app.data.model.SubmitM21DisputeInput
import com.comunidapp.app.data.model.SubmitM21ReviewInput
import com.comunidapp.app.data.model.SubmitM21ReviewResponseInput
import com.comunidapp.app.data.model.SubmitM21VerificationInput
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.remote.supabase.m21.M21ReputationErrorMapper
import com.comunidapp.app.data.repository.M21ReputationRepository
import com.comunidapp.app.domain.m21.M21ReputationResilience
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
                    _uiState.value = M21HubUiState.Error(M21ReputationResilience.safeUserMessage(e))
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
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            repository.observeMyReviews()
                .catch { e ->
                    _uiState.value = M21ReviewsUiState.Error(M21ReputationResilience.safeUserMessage(e))
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
                    targetType = M21ReviewTargetType.DONATION,
                    targetId = com.comunidapp.app.data.model.M21MockTargetIds.DONATION,
                    targetDisplayLabel = "Campaña demo M21",
                    rating = 5,
                    content = "Reseña de prueba desde la app.",
                    contextReference = M21ReviewContextReference(
                        contextType = M21ReviewContextType.DONATION_COMPLETED,
                        contextId = "mock_ctx_demo_${System.currentTimeMillis()}",
                        publicLabel = "Donación demo"
                    )
                )
            ).onSuccess { onDone() }
        }
    }
}

sealed class M21SubjectUiState {
    data object Loading : M21SubjectUiState()
    data class Content(
        val breakdown: M21ReputationBreakdown,
        val eligibility: M21ReviewEligibility?
    ) : M21SubjectUiState()
    data class NotEligible(val eligibility: M21ReviewEligibility) : M21SubjectUiState()
    data class Error(val message: String) : M21SubjectUiState()
}

class M21SubjectViewModel(
    private val targetType: M21ReviewTargetType,
    private val targetId: String,
    private val repository: M21ReputationRepository = DataProvider.m21ReputationRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<M21SubjectUiState>(M21SubjectUiState.Loading)
    val uiState: StateFlow<M21SubjectUiState> = _uiState

    init {
        viewModelScope.launch {
            repository.observeSubjectBreakdown(targetType, targetId)
                .catch { e ->
                    _uiState.value = M21SubjectUiState.Error(M21ReputationResilience.safeUserMessage(e))
                }
                .collect { breakdown ->
                    val eligibility = repository.checkEligibility(
                        CheckM21EligibilityInput(
                            targetType = targetType,
                            targetId = targetId,
                            targetDisplayLabel = breakdown.subject.displayLabel
                        )
                    ).getOrNull()
                    _uiState.value = when {
                        eligibility != null && !eligibility.eligible ->
                            M21SubjectUiState.NotEligible(eligibility)
                        else -> M21SubjectUiState.Content(breakdown, eligibility)
                    }
                }
        }
    }
}

sealed class M21ReviewDetailUiState {
    data object Loading : M21ReviewDetailUiState()
    data class Content(
        val review: M21PublicReview,
        val canEdit: Boolean,
        val canRespond: Boolean,
        val canDispute: Boolean,
        val canReport: Boolean
    ) : M21ReviewDetailUiState()
    data class Error(val message: String) : M21ReviewDetailUiState()
}

class M21ReviewDetailViewModel(
    private val reviewId: String,
    private val repository: M21ReputationRepository = DataProvider.m21ReputationRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<M21ReviewDetailUiState>(M21ReviewDetailUiState.Loading)
    val uiState: StateFlow<M21ReviewDetailUiState> = _uiState

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = M21ReviewDetailUiState.Loading
            repository.getReviewDetail(reviewId)
                .onSuccess { review ->
                    _uiState.value = M21ReviewDetailUiState.Content(
                        review = review,
                        canEdit = review.isOwnReview,
                        canRespond = !review.isOwnReview && !review.hasResponse,
                        canDispute = !review.isOwnReview,
                        canReport = true
                    )
                }
                .onFailure { e ->
                    _uiState.value = M21ReviewDetailUiState.Error(M21ReputationResilience.safeUserMessage(e))
                }
        }
    }

    fun editReview(content: String, rating: Int, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.editReview(EditM21ReviewInput(reviewId, rating = rating, content = content))
                .onSuccess { load(); onDone() }
        }
    }

    fun respond(content: String, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.submitReviewResponse(SubmitM21ReviewResponseInput(reviewId, content))
                .onSuccess { load(); onDone() }
        }
    }

    fun dispute(details: String, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.submitDispute(
                SubmitM21DisputeInput(reviewId, M21ReviewDisputeReason.FACTUAL_ERROR, details)
            ).onSuccess { load(); onDone() }
        }
    }

    fun report(reason: String, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.reportReview(ReportM21ReviewInput(reviewId, reason))
                .onSuccess { onDone() }
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
                    _uiState.value = M21VerificationsUiState.Error(M21ReputationResilience.safeUserMessage(e))
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

    fun subjectFactory(type: M21ReviewTargetType, id: String): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M21SubjectViewModel(type, id) as T
        }

    fun reviewDetailFactory(reviewId: String): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M21ReviewDetailViewModel(reviewId) as T
        }
}
