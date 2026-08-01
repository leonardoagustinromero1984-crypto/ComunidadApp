package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.CreateM15FosterHomeInput
import com.comunidapp.app.data.model.M15FosterHome
import com.comunidapp.app.data.model.M15FosterHomePublicListing
import com.comunidapp.app.data.model.M15FosterRequest
import com.comunidapp.app.data.model.SubmitM15FosterRequestInput
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.remote.supabase.m15.M15ErrorMapper
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.M15FosterHomeRepository
import com.comunidapp.app.data.repository.M15FosterRequestRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

sealed class M15FosterHubUiState {
    data object Loading : M15FosterHubUiState()
    data class Content(
        val myHome: M15FosterHome?,
        val availableCount: Int
    ) : M15FosterHubUiState()
    data class Error(val message: String) : M15FosterHubUiState()
}

class M15FosterHubViewModel(
    private val homeRepository: M15FosterHomeRepository = DataProvider.m15FosterHomeRepository,
    private val actorUserId: () -> String? = { AuthProvider.repository.getCurrentUser()?.id }
) : ViewModel() {
    private val _uiState = MutableStateFlow<M15FosterHubUiState>(M15FosterHubUiState.Loading)
    val uiState: StateFlow<M15FosterHubUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val userId = actorUserId()
            if (userId.isNullOrBlank()) {
                _uiState.value = M15FosterHubUiState.Error(
                    M15ErrorMapper.userMessage("NOT_AUTHENTICATED")
                )
                return@launch
            }
            combine(
                homeRepository.observeAvailableHomes().catch { emit(emptyList()) },
                homeRepository.observeMyHome(userId).catch { emit(null) }
            ) { available, home ->
                M15FosterHubUiState.Content(myHome = home, availableCount = available.size)
            }.collect { _uiState.value = it }
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M15FosterHubViewModel() as T
        }
    }
}

sealed class M15FosterHomesListUiState {
    data object Loading : M15FosterHomesListUiState()
    data object Empty : M15FosterHomesListUiState()
    data class Content(val items: List<M15FosterHomePublicListing>) : M15FosterHomesListUiState()
    data class Error(val message: String) : M15FosterHomesListUiState()
}

class M15FosterHomesListViewModel(
    private val repository: M15FosterHomeRepository = DataProvider.m15FosterHomeRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<M15FosterHomesListUiState>(M15FosterHomesListUiState.Loading)
    val uiState: StateFlow<M15FosterHomesListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeAvailableHomes()
                .catch { e ->
                    _uiState.value = M15FosterHomesListUiState.Error(
                        M15ErrorMapper.userMessage(M15ErrorMapper.codeOf(e))
                    )
                }
                .collect { list ->
                    _uiState.value = if (list.isEmpty()) {
                        M15FosterHomesListUiState.Empty
                    } else {
                        M15FosterHomesListUiState.Content(list)
                    }
                }
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M15FosterHomesListViewModel() as T
        }
    }
}

class M15FosterHomeDetailViewModel(
    private val homeId: String,
    private val repository: M15FosterHomeRepository = DataProvider.m15FosterHomeRepository
) : ViewModel() {
    private val _listing = MutableStateFlow<M15FosterHomePublicListing?>(null)
    val listing: StateFlow<M15FosterHomePublicListing?> = _listing.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getPublicHomeById(homeId)
                .onSuccess { _listing.value = it }
                .onFailure { e ->
                    _message.value = M15ErrorMapper.userMessage(M15ErrorMapper.codeOf(e))
                }
        }
    }

    companion object {
        fun factory(homeId: String): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M15FosterHomeDetailViewModel(homeId) as T
        }
    }
}

class M15MyFosterHomeViewModel(
    private val homeRepository: M15FosterHomeRepository = DataProvider.m15FosterHomeRepository,
    private val actorUserId: () -> String? = { AuthProvider.repository.getCurrentUser()?.id }
) : ViewModel() {
    private val _home = MutableStateFlow<M15FosterHome?>(null)
    val home: StateFlow<M15FosterHome?> = _home.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init {
        viewModelScope.launch {
            val userId = actorUserId() ?: return@launch
            homeRepository.observeMyHome(userId).collect { _home.value = it }
        }
    }

    fun createDraft(displayName: String, zoneText: String, capacity: Int) {
        viewModelScope.launch {
            _busy.value = true
            homeRepository.createHome(
                CreateM15FosterHomeInput(
                    displayName = displayName,
                    totalCapacity = capacity,
                    acceptedSpecies = setOf("DOG", "CAT"),
                    acceptedSizes = setOf("SMALL", "MEDIUM", "LARGE"),
                    zoneText = zoneText,
                    activate = false
                )
            ).onSuccess { _home.value = it }
                .onFailure { e ->
                    _message.value = M15ErrorMapper.userMessage(M15ErrorMapper.codeOf(e))
                }
            _busy.value = false
        }
    }

    fun activate() {
        val id = _home.value?.id ?: return
        viewModelScope.launch {
            _busy.value = true
            homeRepository.activateHome(id)
                .onSuccess { _home.value = it }
                .onFailure { e ->
                    _message.value = M15ErrorMapper.userMessage(M15ErrorMapper.codeOf(e))
                }
            _busy.value = false
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M15MyFosterHomeViewModel() as T
        }
    }
}

class M15FosterRequestFormViewModel(
    private val homeId: String,
    private val requestRepository: M15FosterRequestRepository = DataProvider.m15FosterRequestRepository
) : ViewModel() {
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _submitted = MutableStateFlow(false)
    val submitted: StateFlow<Boolean> = _submitted.asStateFlow()

    fun submit(petId: String, message: String) {
        viewModelScope.launch {
            _busy.value = true
            requestRepository.submitRequest(
                SubmitM15FosterRequestInput(
                    fosterHomeId = homeId,
                    petId = petId,
                    message = message
                )
            ).onSuccess { _submitted.value = true }
                .onFailure { e ->
                    _message.value = M15ErrorMapper.userMessage(M15ErrorMapper.codeOf(e))
                }
            _busy.value = false
        }
    }

    companion object {
        fun factory(homeId: String): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M15FosterRequestFormViewModel(homeId) as T
        }
    }
}

class M15FosterRequestsViewModel(
    private val requestRepository: M15FosterRequestRepository = DataProvider.m15FosterRequestRepository,
    private val actorUserId: () -> String? = { AuthProvider.repository.getCurrentUser()?.id }
) : ViewModel() {
    private val _received = MutableStateFlow<List<M15FosterRequest>>(emptyList())
    val received: StateFlow<List<M15FosterRequest>> = _received.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            val userId = actorUserId() ?: return@launch
            requestRepository.observeReceivedRequests(userId).collect { _received.value = it }
        }
    }

    fun accept(requestId: String) {
        viewModelScope.launch {
            requestRepository.acceptRequest(requestId)
                .onFailure { e ->
                    _message.value = M15ErrorMapper.userMessage(M15ErrorMapper.codeOf(e))
                }
        }
    }

    fun reject(requestId: String) {
        viewModelScope.launch {
            requestRepository.rejectRequest(requestId, null)
                .onFailure { e ->
                    _message.value = M15ErrorMapper.userMessage(M15ErrorMapper.codeOf(e))
                }
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M15FosterRequestsViewModel() as T
        }
    }
}
