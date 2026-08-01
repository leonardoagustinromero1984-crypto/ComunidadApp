package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.AddM15EvolutionInput
import com.comunidapp.app.data.model.AddM15ExpenseInput
import com.comunidapp.app.data.model.AddM15HelpRequestInput
import com.comunidapp.app.data.model.M15DischargeInput
import com.comunidapp.app.data.model.M15DischargeOutcome
import com.comunidapp.app.data.model.M15DischargeReason
import com.comunidapp.app.data.model.M15EvolutionEventType
import com.comunidapp.app.data.model.M15ExpenseCategory
import com.comunidapp.app.data.model.M15FosterPlacement
import com.comunidapp.app.data.model.M15HelpRequestType
import com.comunidapp.app.data.model.M15HelpPriority
import com.comunidapp.app.data.model.M15PlacementEvolution
import com.comunidapp.app.data.model.M15PlacementExpense
import com.comunidapp.app.data.model.M15PlacementHelpRequest
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.remote.supabase.m15.M15ErrorMapper
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.M15FosterPlacementRepository
import com.comunidapp.app.data.repository.M15PlacementDischargeRepository
import com.comunidapp.app.data.repository.M15PlacementEvolutionRepository
import com.comunidapp.app.data.repository.M15PlacementExpenseRepository
import com.comunidapp.app.data.repository.M15PlacementHelpRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class M15PlacementsListViewModel(
    private val placementRepository: M15FosterPlacementRepository = DataProvider.m15FosterPlacementRepository,
    private val actorUserId: () -> String? = { AuthProvider.repository.getCurrentUser()?.id }
) : ViewModel() {
    private val _placements = MutableStateFlow<List<M15FosterPlacement>>(emptyList())
    val placements: StateFlow<List<M15FosterPlacement>> = _placements.asStateFlow()

    init {
        viewModelScope.launch {
            val userId = actorUserId() ?: return@launch
            placementRepository.observeActivePlacementsForUser(userId).collect {
                _placements.value = it
            }
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M15PlacementsListViewModel() as T
        }
    }
}

class M15PlacementDetailViewModel(
    private val placementId: String,
    private val placementRepository: M15FosterPlacementRepository = DataProvider.m15FosterPlacementRepository
) : ViewModel() {
    private val _placement = MutableStateFlow<M15FosterPlacement?>(null)
    val placement: StateFlow<M15FosterPlacement?> = _placement.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            placementRepository.getPlacementById(placementId)
                .onSuccess { _placement.value = it }
                .onFailure { e ->
                    _message.value = M15ErrorMapper.userMessage(M15ErrorMapper.codeOf(e))
                }
        }
    }

    companion object {
        fun factory(placementId: String): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M15PlacementDetailViewModel(placementId) as T
        }
    }
}

class M15EvolutionListViewModel(
    private val placementId: String,
    private val repository: M15PlacementEvolutionRepository = DataProvider.m15EvolutionRepository
) : ViewModel() {
    private val _items = MutableStateFlow<List<M15PlacementEvolution>>(emptyList())
    val items: StateFlow<List<M15PlacementEvolution>> = _items.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeEvolution(placementId).collect { _items.value = it }
        }
    }

    companion object {
        fun factory(placementId: String): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M15EvolutionListViewModel(placementId) as T
        }
    }
}

class M15EvolutionFormViewModel(
    private val placementId: String,
    private val repository: M15PlacementEvolutionRepository = DataProvider.m15EvolutionRepository
) : ViewModel() {
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    fun submit(summary: String, eventType: M15EvolutionEventType, healthAlert: Boolean) {
        viewModelScope.launch {
            repository.addEvolution(
                AddM15EvolutionInput(
                    placementId = placementId,
                    eventType = eventType,
                    summary = summary,
                    healthAlert = healthAlert
                )
            ).onSuccess { _saved.value = true }
                .onFailure { e ->
                    _message.value = M15ErrorMapper.userMessage(M15ErrorMapper.codeOf(e))
                }
        }
    }

    companion object {
        fun factory(placementId: String): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M15EvolutionFormViewModel(placementId) as T
        }
    }
}

class M15DischargeViewModel(
    private val placementId: String,
    private val repository: M15PlacementDischargeRepository = DataProvider.m15DischargeRepository
) : ViewModel() {
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _completed = MutableStateFlow(false)
    val completed: StateFlow<Boolean> = _completed.asStateFlow()

    fun discharge(reason: M15DischargeReason, outcome: M15DischargeOutcome, note: String?) {
        viewModelScope.launch {
            repository.discharge(
                M15DischargeInput(
                    placementId = placementId,
                    reason = reason,
                    outcome = outcome,
                    privateNote = note
                )
            ).onSuccess { _completed.value = true }
                .onFailure { e ->
                    _message.value = M15ErrorMapper.userMessage(M15ErrorMapper.codeOf(e))
                }
        }
    }

    companion object {
        fun factory(placementId: String): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M15DischargeViewModel(placementId) as T
        }
    }
}

class M15ExpensesViewModel(
    private val placementId: String,
    private val repository: M15PlacementExpenseRepository = DataProvider.m15ExpenseRepository
) : ViewModel() {
    private val _items = MutableStateFlow<List<M15PlacementExpense>>(emptyList())
    val items: StateFlow<List<M15PlacementExpense>> = _items.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeExpenses(placementId).collect { _items.value = it }
        }
    }

    companion object {
        fun factory(placementId: String): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M15ExpensesViewModel(placementId) as T
        }
    }
}

class M15ExpenseFormViewModel(
    private val placementId: String,
    private val repository: M15PlacementExpenseRepository = DataProvider.m15ExpenseRepository
) : ViewModel() {
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    fun submit(description: String, amountMinor: Long, category: M15ExpenseCategory) {
        viewModelScope.launch {
            repository.addExpense(
                AddM15ExpenseInput(
                    placementId = placementId,
                    category = category,
                    amountMinor = amountMinor,
                    currency = "ARS",
                    occurredAt = System.currentTimeMillis(),
                    description = description
                )
            ).onSuccess { _saved.value = true }
                .onFailure { e ->
                    _message.value = M15ErrorMapper.userMessage(M15ErrorMapper.codeOf(e))
                }
        }
    }

    companion object {
        fun factory(placementId: String): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M15ExpenseFormViewModel(placementId) as T
        }
    }
}

class M15HelpListViewModel(
    private val placementId: String,
    private val repository: M15PlacementHelpRepository = DataProvider.m15HelpRepository
) : ViewModel() {
    private val _items = MutableStateFlow<List<M15PlacementHelpRequest>>(emptyList())
    val items: StateFlow<List<M15PlacementHelpRequest>> = _items.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeHelpRequests(placementId).collect { _items.value = it }
        }
    }

    companion object {
        fun factory(placementId: String): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M15HelpListViewModel(placementId) as T
        }
    }
}

class M15HelpFormViewModel(
    private val placementId: String,
    private val repository: M15PlacementHelpRepository = DataProvider.m15HelpRepository
) : ViewModel() {
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    fun submit(title: String, description: String, type: M15HelpRequestType) {
        viewModelScope.launch {
            repository.createHelpRequest(
                AddM15HelpRequestInput(
                    placementId = placementId,
                    type = type,
                    title = title,
                    description = description,
                    priority = M15HelpPriority.NORMAL
                )
            ).onSuccess { _saved.value = true }
                .onFailure { e ->
                    _message.value = M15ErrorMapper.userMessage(M15ErrorMapper.codeOf(e))
                }
        }
    }

    companion object {
        fun factory(placementId: String): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M15HelpFormViewModel(placementId) as T
        }
    }
}
