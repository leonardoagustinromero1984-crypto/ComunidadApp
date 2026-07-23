package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.FosterContributionStatus
import com.comunidapp.app.data.model.FosterEvolutionEntry
import com.comunidapp.app.data.model.FosterEvolutionVisibility
import com.comunidapp.app.data.model.FosterExpense
import com.comunidapp.app.data.model.FosterExpenseCategory
import com.comunidapp.app.data.model.FosterHealthStatus
import com.comunidapp.app.data.model.FosterHelpContribution
import com.comunidapp.app.data.model.FosterHelpRequest
import com.comunidapp.app.data.model.FosterHelpStatus
import com.comunidapp.app.data.model.FosterHelpType
import com.comunidapp.app.data.model.FosterPlacement
import com.comunidapp.app.data.model.FosterPlacementEndReason
import com.comunidapp.app.data.model.FosterPlacementStatus
import com.comunidapp.app.data.model.FosterUrgency
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.remote.supabase.m10.M10FosterErrorMapper
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.FosterEvolutionRepository
import com.comunidapp.app.data.repository.FosterExpenseRepository
import com.comunidapp.app.data.repository.FosterHelpRepository
import com.comunidapp.app.data.repository.FosterPlacementRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class FosterCarePanelUiState {
    data object Loading : FosterCarePanelUiState()
    data object Empty : FosterCarePanelUiState()
    data class Content(val placement: FosterPlacement) : FosterCarePanelUiState()
    data class Error(val message: String) : FosterCarePanelUiState()
}

class FosterPlacementManagementViewModel(
    private val placementId: String,
    private val placementRepository: FosterPlacementRepository = DataProvider.fosterPlacementRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<FosterCarePanelUiState>(FosterCarePanelUiState.Loading)
    val uiState: StateFlow<FosterCarePanelUiState> = _uiState.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        if (placementId.isBlank()) {
            _uiState.value = FosterCarePanelUiState.Error(
                M10FosterErrorMapper.userMessage("FOSTER_PLACEMENT_NOT_FOUND")
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = FosterCarePanelUiState.Loading
            placementRepository.getPlacementById(placementId)
                .onSuccess { p ->
                    _uiState.value = FosterCarePanelUiState.Content(p)
                }
                .onFailure {
                    _uiState.value = FosterCarePanelUiState.Error(
                        M10FosterErrorMapper.userMessage(M10FosterErrorMapper.codeOf(it))
                    )
                }
        }
    }

    companion object {
        fun factory(placementId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    FosterPlacementManagementViewModel(placementId) as T
            }
    }
}

sealed class FosterExpensesUiState {
    data object Loading : FosterExpensesUiState()
    data object Empty : FosterExpensesUiState()
    data class Content(val items: List<FosterExpense>) : FosterExpensesUiState()
    data class Error(val message: String) : FosterExpensesUiState()
}

class FosterExpensesViewModel(
    private val placementId: String,
    private val expenseRepository: FosterExpenseRepository = DataProvider.fosterExpenseRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<FosterExpensesUiState>(FosterExpensesUiState.Loading)
    val uiState: StateFlow<FosterExpensesUiState> = _uiState.asStateFlow()

    init {
        if (placementId.isBlank()) {
            _uiState.value = FosterExpensesUiState.Error(
                M10FosterErrorMapper.userMessage("FOSTER_PLACEMENT_NOT_FOUND")
            )
        } else {
            viewModelScope.launch {
                expenseRepository.observeExpenses(placementId).collect { list ->
                    _uiState.value = when {
                        list.isEmpty() -> FosterExpensesUiState.Empty
                        else -> FosterExpensesUiState.Content(list)
                    }
                }
            }
        }
    }

    companion object {
        fun factory(placementId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    FosterExpensesViewModel(placementId) as T
            }
    }
}

class FosterExpenseFormViewModel(
    private val placementId: String,
    private val expenseRepository: FosterExpenseRepository = DataProvider.fosterExpenseRepository
) : ViewModel() {
    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _saved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saved = _saved.asSharedFlow()

    fun submit(
        category: FosterExpenseCategory,
        description: String,
        amountMinor: Long,
        currency: String,
        receiptRef: String?
    ) {
        if (_submitting.value) return
        if (placementId.isBlank()) {
            _error.value = M10FosterErrorMapper.userMessage("FOSTER_PLACEMENT_NOT_FOUND")
            return
        }
        viewModelScope.launch {
            _submitting.value = true
            _error.value = null
            expenseRepository.addExpense(
                placementId = placementId,
                category = category,
                description = description,
                amountMinor = amountMinor,
                currency = currency,
                occurredAt = System.currentTimeMillis(),
                receiptRef = receiptRef
            ).onSuccess {
                _saved.tryEmit(Unit)
            }.onFailure {
                _error.value = M10FosterErrorMapper.userMessage(M10FosterErrorMapper.codeOf(it))
            }
            _submitting.value = false
        }
    }

    companion object {
        fun factory(placementId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    FosterExpenseFormViewModel(placementId) as T
            }
    }
}

sealed class FosterEvolutionUiState {
    data object Loading : FosterEvolutionUiState()
    data object Empty : FosterEvolutionUiState()
    data class Content(val items: List<FosterEvolutionEntry>) : FosterEvolutionUiState()
    data class Error(val message: String) : FosterEvolutionUiState()
}

class FosterEvolutionListViewModel(
    private val placementId: String,
    private val evolutionRepository: FosterEvolutionRepository = DataProvider.fosterEvolutionRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<FosterEvolutionUiState>(FosterEvolutionUiState.Loading)
    val uiState: StateFlow<FosterEvolutionUiState> = _uiState.asStateFlow()

    init {
        if (placementId.isBlank()) {
            _uiState.value = FosterEvolutionUiState.Error(
                M10FosterErrorMapper.userMessage("FOSTER_PLACEMENT_NOT_FOUND")
            )
        } else {
            viewModelScope.launch {
                evolutionRepository.observeEvolution(placementId).collect { list ->
                    _uiState.value = when {
                        list.isEmpty() -> FosterEvolutionUiState.Empty
                        else -> FosterEvolutionUiState.Content(list)
                    }
                }
            }
        }
    }

    companion object {
        fun factory(placementId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    FosterEvolutionListViewModel(placementId) as T
            }
    }
}

class FosterEvolutionFormViewModel(
    private val placementId: String,
    private val evolutionRepository: FosterEvolutionRepository = DataProvider.fosterEvolutionRepository
) : ViewModel() {
    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _saved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saved = _saved.asSharedFlow()

    fun submit(
        title: String,
        description: String,
        healthStatus: FosterHealthStatus,
        weightGrams: Int?,
        mediaRefs: List<String>,
        visibility: FosterEvolutionVisibility
    ) {
        if (_submitting.value) return
        if (placementId.isBlank()) {
            _error.value = M10FosterErrorMapper.userMessage("FOSTER_PLACEMENT_NOT_FOUND")
            return
        }
        viewModelScope.launch {
            _submitting.value = true
            _error.value = null
            evolutionRepository.addEvolution(
                placementId = placementId,
                title = title,
                description = description,
                healthStatus = healthStatus,
                weightGrams = weightGrams,
                occurredAt = System.currentTimeMillis(),
                mediaRefs = mediaRefs,
                visibility = visibility
            ).onSuccess { _saved.tryEmit(Unit) }
                .onFailure {
                    _error.value = M10FosterErrorMapper.userMessage(M10FosterErrorMapper.codeOf(it))
                }
            _submitting.value = false
        }
    }

    companion object {
        fun factory(placementId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    FosterEvolutionFormViewModel(placementId) as T
            }
    }
}

sealed class FosterHelpListUiState {
    data object Loading : FosterHelpListUiState()
    data object Empty : FosterHelpListUiState()
    data class Content(val items: List<FosterHelpRequest>) : FosterHelpListUiState()
    data class Error(val message: String) : FosterHelpListUiState()
}

class FosterHelpListViewModel(
    private val placementId: String,
    private val helpRepository: FosterHelpRepository = DataProvider.fosterHelpRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<FosterHelpListUiState>(FosterHelpListUiState.Loading)
    val uiState: StateFlow<FosterHelpListUiState> = _uiState.asStateFlow()

    init {
        if (placementId.isBlank()) {
            _uiState.value = FosterHelpListUiState.Error(
                M10FosterErrorMapper.userMessage("FOSTER_PLACEMENT_NOT_FOUND")
            )
        } else {
            viewModelScope.launch {
                helpRepository.observeHelpRequests(placementId).collect { list ->
                    _uiState.value = when {
                        list.isEmpty() -> FosterHelpListUiState.Empty
                        else -> FosterHelpListUiState.Content(list)
                    }
                }
            }
        }
    }

    companion object {
        fun factory(placementId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    FosterHelpListViewModel(placementId) as T
            }
    }
}

class FosterHelpFormViewModel(
    private val placementId: String,
    private val helpRepository: FosterHelpRepository = DataProvider.fosterHelpRepository
) : ViewModel() {
    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _saved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saved = _saved.asSharedFlow()

    fun submit(
        type: FosterHelpType,
        title: String,
        description: String,
        targetAmountMinor: Long?,
        currency: String?,
        quantityNeeded: Int?,
        urgency: FosterUrgency
    ) {
        if (_submitting.value) return
        if (placementId.isBlank()) {
            _error.value = M10FosterErrorMapper.userMessage("FOSTER_PLACEMENT_NOT_FOUND")
            return
        }
        viewModelScope.launch {
            _submitting.value = true
            _error.value = null
            helpRepository.createHelpRequest(
                placementId = placementId,
                type = type,
                title = title,
                description = description,
                targetAmountMinor = targetAmountMinor,
                currency = currency,
                quantityNeeded = quantityNeeded,
                urgency = urgency
            ).onSuccess { _saved.tryEmit(Unit) }
                .onFailure {
                    _error.value = M10FosterErrorMapper.userMessage(M10FosterErrorMapper.codeOf(it))
                }
            _submitting.value = false
        }
    }

    companion object {
        fun factory(placementId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    FosterHelpFormViewModel(placementId) as T
            }
    }
}

data class FosterHelpDetailContent(
    val request: FosterHelpRequest,
    val contributions: List<FosterHelpContribution>
)

sealed class FosterHelpDetailUiState {
    data object Loading : FosterHelpDetailUiState()
    data class Content(val data: FosterHelpDetailContent) : FosterHelpDetailUiState()
    data class Error(val message: String) : FosterHelpDetailUiState()
}

class FosterHelpDetailViewModel(
    private val helpRequestId: String,
    private val helpRepository: FosterHelpRepository = DataProvider.fosterHelpRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<FosterHelpDetailUiState>(FosterHelpDetailUiState.Loading)
    val uiState: StateFlow<FosterHelpDetailUiState> = _uiState.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        if (helpRequestId.isBlank()) {
            _uiState.value = FosterHelpDetailUiState.Error(
                M10FosterErrorMapper.userMessage("FOSTER_HELP_REQUEST_NOT_FOUND")
            )
        } else {
            viewModelScope.launch {
                val reqResult = helpRepository.getHelpRequest(helpRequestId)
                reqResult.onFailure {
                    _uiState.value = FosterHelpDetailUiState.Error(
                        M10FosterErrorMapper.userMessage(M10FosterErrorMapper.codeOf(it))
                    )
                    return@launch
                }
                val placementId = reqResult.getOrNull()?.placementId.orEmpty()
                combine(
                    helpRepository.observeHelpRequests(placementId),
                    helpRepository.observeContributions(helpRequestId)
                ) { requests, contribs ->
                    val hr = requests.find { it.id == helpRequestId }
                        ?: reqResult.getOrNull()
                    if (hr == null) FosterHelpDetailUiState.Error(
                        M10FosterErrorMapper.userMessage("FOSTER_HELP_REQUEST_NOT_FOUND")
                    ) else FosterHelpDetailUiState.Content(
                        FosterHelpDetailContent(hr, contribs)
                    )
                }.collect { _uiState.value = it }
            }
        }
    }

    fun changeStatus(status: FosterHelpStatus) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            helpRepository.changeHelpRequestStatus(helpRequestId, status)
                .onFailure {
                    _error.value = M10FosterErrorMapper.userMessage(M10FosterErrorMapper.codeOf(it))
                }
            _busy.value = false
        }
    }

    fun recordContribution(
        description: String,
        amountMinor: Long?,
        quantity: Int?,
        status: FosterContributionStatus = FosterContributionStatus.RECEIVED
    ) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            helpRepository.recordContribution(
                helpRequestId = helpRequestId,
                description = description,
                amountMinor = amountMinor,
                quantity = quantity,
                status = status
            ).onFailure {
                _error.value = M10FosterErrorMapper.userMessage(M10FosterErrorMapper.codeOf(it))
            }
            _busy.value = false
        }
    }

    companion object {
        fun factory(helpRequestId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    FosterHelpDetailViewModel(helpRequestId) as T
            }
    }
}

data class FosterCompleteSummary(
    val placement: FosterPlacement,
    val expenseCount: Int,
    val evolutionCount: Int,
    val openHelpCount: Int,
    val principalUserId: String?
)

sealed class FosterCompleteUiState {
    data object Loading : FosterCompleteUiState()
    data class Content(val summary: FosterCompleteSummary) : FosterCompleteUiState()
    data class Error(val message: String) : FosterCompleteUiState()
}

class FosterCompleteViewModel(
    private val placementId: String,
    private val placementRepository: FosterPlacementRepository = DataProvider.fosterPlacementRepository,
    private val expenseRepository: FosterExpenseRepository = DataProvider.fosterExpenseRepository,
    private val evolutionRepository: FosterEvolutionRepository = DataProvider.fosterEvolutionRepository,
    private val helpRepository: FosterHelpRepository = DataProvider.fosterHelpRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<FosterCompleteUiState>(FosterCompleteUiState.Loading)
    val uiState: StateFlow<FosterCompleteUiState> = _uiState.asStateFlow()
    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _completed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val completed = _completed.asSharedFlow()

    init {
        reload()
    }

    fun reload() {
        if (placementId.isBlank()) {
            _uiState.value = FosterCompleteUiState.Error(
                M10FosterErrorMapper.userMessage("FOSTER_PLACEMENT_NOT_FOUND")
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = FosterCompleteUiState.Loading
            val placement = placementRepository.getPlacementById(placementId).getOrElse {
                _uiState.value = FosterCompleteUiState.Error(
                    M10FosterErrorMapper.userMessage(M10FosterErrorMapper.codeOf(it))
                )
                return@launch
            }
            if (placement.status == FosterPlacementStatus.COMPLETED) {
                _uiState.value = FosterCompleteUiState.Error(
                    M10FosterErrorMapper.userMessage("FOSTER_PLACEMENT_ALREADY_COMPLETED")
                )
                return@launch
            }
            combine(
                expenseRepository.observeExpenses(placementId),
                evolutionRepository.observeEvolution(placementId),
                helpRepository.observeHelpRequests(placementId)
            ) { expenses, evolution, help ->
                FosterCompleteSummary(
                    placement = placement,
                    expenseCount = expenses.size,
                    evolutionCount = evolution.size,
                    openHelpCount = help.count { it.status.isEditable },
                    principalUserId = placement.requesterUserId
                )
            }.collect { summary ->
                _uiState.value = FosterCompleteUiState.Content(summary)
            }
        }
    }

    fun complete(reason: FosterPlacementEndReason, notes: String?) {
        if (_submitting.value) return
        viewModelScope.launch {
            _submitting.value = true
            _error.value = null
            placementRepository.completePlacement(placementId, reason, notes)
                .onSuccess { _completed.tryEmit(Unit) }
                .onFailure {
                    _error.value = M10FosterErrorMapper.userMessage(M10FosterErrorMapper.codeOf(it))
                }
            _submitting.value = false
        }
    }

    companion object {
        fun factory(placementId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    FosterCompleteViewModel(placementId) as T
            }
    }
}

class FosterHistoryViewModel(
    private val placementRepository: FosterPlacementRepository = DataProvider.fosterPlacementRepository,
    private val authRepository: AuthRepository = AuthProvider.repository
) : ViewModel() {
    val history: StateFlow<List<FosterPlacement>> =
        placementRepository.observePlacementHistory(
            authRepository.getCurrentUser()?.id.orEmpty()
        ).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                FosterHistoryViewModel() as T
        }
    }
}
