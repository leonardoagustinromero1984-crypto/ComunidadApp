package com.comunidapp.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.FosterAvailabilityStatus
import com.comunidapp.app.data.model.FosterHomeProfile
import com.comunidapp.app.data.model.FosterHomePublicListing
import com.comunidapp.app.data.model.FosterHomeRequest
import com.comunidapp.app.data.model.FosterHomeRequestStatus
import com.comunidapp.app.data.model.FosterHomeStatus
import com.comunidapp.app.data.model.FosterPlacement
import com.comunidapp.app.data.model.FosterUrgency
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.remote.supabase.m10.M10FosterErrorMapper
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.CreateFosterHomeInput
import com.comunidapp.app.data.repository.FosterHomeRepository
import com.comunidapp.app.data.repository.FosterPlacementRepository
import com.comunidapp.app.data.repository.FosterRequestRepository
import com.comunidapp.app.data.repository.PetRepository
import com.comunidapp.app.data.repository.SubmitFosterRequestInput
import com.comunidapp.app.data.repository.UpdateFosterHomeInput
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class FosterListUiState {
    data object Loading : FosterListUiState()
    data object Empty : FosterListUiState()
    data class Content(val homes: List<FosterHomePublicListing>) : FosterListUiState()
    data class Error(val message: String) : FosterListUiState()
}

class FosterHomesListViewModel(
    private val homeRepository: FosterHomeRepository = DataProvider.fosterHomeRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<FosterListUiState>(FosterListUiState.Loading)
    val uiState: StateFlow<FosterListUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = FosterListUiState.Loading
            homeRepository.observeAvailableFosterHomes().collect { list ->
                _uiState.value = when {
                    list.isEmpty() -> FosterListUiState.Empty
                    else -> FosterListUiState.Content(list)
                }
            }
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                FosterHomesListViewModel() as T
        }
    }
}

sealed class MyFosterHomeUiState {
    data object Loading : MyFosterHomeUiState()
    data object Empty : MyFosterHomeUiState()
    data class Content(val home: FosterHomeProfile) : MyFosterHomeUiState()
    data class Error(val message: String) : MyFosterHomeUiState()
}

class MyFosterHomeViewModel(
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val homeRepository: FosterHomeRepository = DataProvider.fosterHomeRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<MyFosterHomeUiState>(MyFosterHomeUiState.Loading)
    val uiState: StateFlow<MyFosterHomeUiState> = _uiState.asStateFlow()
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()
    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()

    init {
        val uid = authRepository.getCurrentUser()?.id.orEmpty()
        if (uid.isBlank()) {
            _uiState.value = MyFosterHomeUiState.Error(M10FosterErrorMapper.userMessage("NOT_AUTHENTICATED"))
        } else {
            viewModelScope.launch {
                homeRepository.observeMyFosterHome(uid).collect { home ->
                    _uiState.value = when {
                        home == null -> MyFosterHomeUiState.Empty
                        else -> MyFosterHomeUiState.Content(home)
                    }
                }
            }
        }
    }

    fun activate(homeId: String) = setStatus(homeId, FosterHomeStatus.ACTIVE)
    fun pause(homeId: String) = setStatus(homeId, FosterHomeStatus.PAUSED)

    private fun setStatus(homeId: String, status: FosterHomeStatus) {
        if (_submitting.value) return
        viewModelScope.launch {
            _submitting.value = true
            _actionError.value = null
            homeRepository.setHomeStatus(homeId, status)
                .onFailure { _actionError.value = M10FosterErrorMapper.userMessage(M10FosterErrorMapper.codeOf(it)) }
            _submitting.value = false
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MyFosterHomeViewModel() as T
        }
    }
}

data class FosterHomeFormState(
    val displayName: String = "",
    val description: String = "",
    val zoneText: String = "",
    val publicLocationText: String = "",
    val privateAddressText: String = "",
    val capacity: String = "1",
    val speciesDog: Boolean = true,
    val speciesCat: Boolean = false,
    val sizeS: Boolean = true,
    val sizeM: Boolean = true,
    val sizeL: Boolean = false,
    val acceptsSpecialNeeds: Boolean = false,
    val acceptsEmergencies: Boolean = false,
    val activate: Boolean = false,
    val submitting: Boolean = false,
    val error: String? = null,
    val editingHomeId: String? = null
)

class FosterHomeFormViewModel(
    private val homeRepository: FosterHomeRepository = DataProvider.fosterHomeRepository,
    private val authRepository: AuthRepository = AuthProvider.repository
) : ViewModel() {
    private val _form = MutableStateFlow(FosterHomeFormState())
    val form: StateFlow<FosterHomeFormState> = _form.asStateFlow()
    private val _saved = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val saved = _saved.asSharedFlow()

    fun loadForEdit(homeId: String) {
        viewModelScope.launch {
            homeRepository.getFosterHomeById(homeId).onSuccess { home ->
                _form.value = FosterHomeFormState(
                    displayName = home.displayName,
                    description = home.description.orEmpty(),
                    zoneText = home.zoneText,
                    publicLocationText = home.publicLocationText.orEmpty(),
                    privateAddressText = home.privateAddressText.orEmpty(),
                    capacity = home.totalCapacity.toString(),
                    speciesDog = "DOG" in home.acceptedSpecies,
                    speciesCat = "CAT" in home.acceptedSpecies,
                    sizeS = "SMALL" in home.acceptedSizes,
                    sizeM = "MEDIUM" in home.acceptedSizes,
                    sizeL = "LARGE" in home.acceptedSizes,
                    acceptsSpecialNeeds = home.acceptsSpecialNeeds,
                    acceptsEmergencies = home.acceptsEmergencies,
                    editingHomeId = home.id
                )
            }.onFailure {
                _form.value = _form.value.copy(
                    error = M10FosterErrorMapper.userMessage(M10FosterErrorMapper.codeOf(it))
                )
            }
        }
    }

    fun update(block: (FosterHomeFormState) -> FosterHomeFormState) {
        _form.value = block(_form.value).copy(error = null)
    }

    fun submit() {
        val s = _form.value
        if (s.submitting) return
        val capacity = s.capacity.toIntOrNull() ?: 0
        viewModelScope.launch {
            _form.value = s.copy(submitting = true, error = null)
            val species = buildSet {
                if (s.speciesDog) add("DOG")
                if (s.speciesCat) add("CAT")
            }
            val sizes = buildSet {
                if (s.sizeS) add("SMALL")
                if (s.sizeM) add("MEDIUM")
                if (s.sizeL) add("LARGE")
            }
            val result = if (s.editingHomeId != null) {
                homeRepository.updateFosterHome(
                    UpdateFosterHomeInput(
                        homeId = s.editingHomeId,
                        displayName = s.displayName,
                        description = s.description,
                        totalCapacity = capacity,
                        acceptedSpecies = species,
                        acceptedSizes = sizes,
                        acceptsSpecialNeeds = s.acceptsSpecialNeeds,
                        acceptsEmergencies = s.acceptsEmergencies,
                        zoneText = s.zoneText,
                        publicLocationText = s.publicLocationText.ifBlank { null },
                        privateAddressText = s.privateAddressText.ifBlank { null }
                    )
                )
            } else {
                homeRepository.createFosterHome(
                    CreateFosterHomeInput(
                        displayName = s.displayName,
                        description = s.description,
                        totalCapacity = capacity,
                        acceptedSpecies = species,
                        acceptedSizes = sizes,
                        acceptsSpecialNeeds = s.acceptsSpecialNeeds,
                        acceptsEmergencies = s.acceptsEmergencies,
                        zoneText = s.zoneText,
                        publicLocationText = s.publicLocationText.ifBlank { null },
                        privateAddressText = s.privateAddressText.ifBlank { null },
                        activate = s.activate
                    )
                )
            }
            result.onSuccess {
                _form.value = _form.value.copy(submitting = false)
                _saved.tryEmit(it.id)
            }.onFailure {
                _form.value = _form.value.copy(
                    submitting = false,
                    error = M10FosterErrorMapper.userMessage(M10FosterErrorMapper.codeOf(it))
                )
            }
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                FosterHomeFormViewModel() as T
        }
    }
}

sealed class FosterDetailUiState {
    data object Loading : FosterDetailUiState()
    data class Content(val home: FosterHomePublicListing, val canRequest: Boolean) : FosterDetailUiState()
    data class Error(val message: String) : FosterDetailUiState()
}

class FosterHomeDetailViewModel(
    private val homeId: String,
    private val homeRepository: FosterHomeRepository = DataProvider.fosterHomeRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<FosterDetailUiState>(FosterDetailUiState.Loading)
    val uiState: StateFlow<FosterDetailUiState> = _uiState.asStateFlow()

    init {
        if (homeId.isBlank()) {
            _uiState.value = FosterDetailUiState.Error(M10FosterErrorMapper.userMessage("FOSTER_HOME_NOT_FOUND"))
        } else {
            viewModelScope.launch {
                homeRepository.getPublicFosterHomeById(homeId)
                    .onSuccess {
                        _uiState.value = FosterDetailUiState.Content(
                            home = it,
                            canRequest = it.freeSlots > 0 &&
                                it.availabilityStatus != FosterAvailabilityStatus.FULL &&
                                it.availabilityStatus != FosterAvailabilityStatus.UNAVAILABLE
                        )
                    }
                    .onFailure {
                        _uiState.value = FosterDetailUiState.Error(
                            M10FosterErrorMapper.userMessage(M10FosterErrorMapper.codeOf(it))
                        )
                    }
            }
        }
    }

    companion object {
        fun factory(homeId: String): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                FosterHomeDetailViewModel(homeId) as T
        }
    }
}

data class FosterRequestFormState(
    val pets: List<Pet> = emptyList(),
    val selectedPetId: String? = null,
    val message: String = "",
    val urgency: FosterUrgency = FosterUrgency.NORMAL,
    val specialNeeds: String = "",
    val submitting: Boolean = false,
    val error: String? = null,
    val submitted: Boolean = false
)

class FosterRequestFormViewModel(
    private val fosterHomeId: String,
    private val requestRepository: FosterRequestRepository = DataProvider.fosterRequestRepository,
    private val petRepository: PetRepository = DataProvider.petRepository,
    private val authRepository: AuthRepository = AuthProvider.repository
) : ViewModel() {
    private val _form = MutableStateFlow(FosterRequestFormState())
    val form: StateFlow<FosterRequestFormState> = _form.asStateFlow()

    init {
        viewModelScope.launch {
            val uid = authRepository.getCurrentUser()?.id
            val pets = if (uid == null) emptyList()
            else runCatching { petRepository.getPetsByOwner(uid) }.getOrElse { emptyList() }
                .filter { !it.status.equals("DECEASED", true) && !it.status.equals("ARCHIVED", true) }
            _form.value = _form.value.copy(pets = pets, selectedPetId = pets.firstOrNull()?.id)
        }
    }

    fun update(block: (FosterRequestFormState) -> FosterRequestFormState) {
        _form.value = block(_form.value).copy(error = null)
    }

    fun submit() {
        val s = _form.value
        if (s.submitting || s.submitted) return
        val petId = s.selectedPetId
        if (petId.isNullOrBlank()) {
            _form.value = s.copy(error = M10FosterErrorMapper.userMessage("PET_NOT_FOUND"))
            return
        }
        viewModelScope.launch {
            _form.value = s.copy(submitting = true, error = null)
            requestRepository.submitRequest(
                SubmitFosterRequestInput(
                    fosterHomeId = fosterHomeId,
                    petId = petId,
                    message = s.message,
                    urgency = s.urgency,
                    specialNeeds = s.specialNeeds.ifBlank { null }
                )
            ).onSuccess {
                _form.value = _form.value.copy(submitting = false, submitted = true)
            }.onFailure {
                _form.value = _form.value.copy(
                    submitting = false,
                    error = M10FosterErrorMapper.userMessage(M10FosterErrorMapper.codeOf(it))
                )
            }
        }
    }

    companion object {
        fun factory(fosterHomeId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    FosterRequestFormViewModel(fosterHomeId) as T
            }
    }
}

class FosterRequestsListViewModel(
    private val received: Boolean,
    private val requestRepository: FosterRequestRepository = DataProvider.fosterRequestRepository,
    private val placementRepository: FosterPlacementRepository = DataProvider.fosterPlacementRepository,
    private val authRepository: AuthRepository = AuthProvider.repository
) : ViewModel() {
    private val _requests = MutableStateFlow<List<FosterHomeRequest>>(emptyList())
    val requests: StateFlow<List<FosterHomeRequest>> = _requests.asStateFlow()
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        val uid = authRepository.getCurrentUser()?.id.orEmpty()
        viewModelScope.launch {
            _loading.value = true
            val flow = if (received) requestRepository.observeReceivedRequests(uid)
            else requestRepository.observeSentRequests(uid)
            flow.collect {
                _requests.value = it
                _loading.value = false
            }
        }
    }

    fun markUnderReview(id: String) = mutate { requestRepository.markUnderReview(id) }
    fun accept(id: String) = mutate { requestRepository.acceptRequest(id) }
    fun reject(id: String, reason: String?) = mutate { requestRepository.rejectRequest(id, reason) }
    fun cancel(id: String) = mutate { requestRepository.cancelRequest(id) }
    fun startPlacement(requestId: String) = mutate {
        placementRepository.startPlacement(requestId, null)
            .map { FosterHomeRequest(
                id = requestId,
                fosterHomeId = it.fosterHomeId,
                petId = it.petId,
                message = "",
                status = FosterHomeRequestStatus.ACCEPTED,
                createdAt = it.startedAt
            ) }
    }

    private fun mutate(block: suspend () -> Result<*>) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            block().onSuccess {
                _message.value = "Listo"
            }.onFailure {
                _error.value = M10FosterErrorMapper.userMessage(M10FosterErrorMapper.codeOf(it))
            }
            _busy.value = false
        }
    }

    fun clearMessage() { _message.value = null }

    companion object {
        fun factory(received: Boolean): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    FosterRequestsListViewModel(received) as T
            }
    }
}

class FosterRequestDetailViewModel(
    private val requestId: String,
    private val requestRepository: FosterRequestRepository = DataProvider.fosterRequestRepository,
    private val placementRepository: FosterPlacementRepository = DataProvider.fosterPlacementRepository
) : ViewModel() {
    private val _request = MutableStateFlow<FosterHomeRequest?>(null)
    val request: StateFlow<FosterHomeRequest?> = _request.asStateFlow()
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _placementStarted = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val placementStarted = _placementStarted.asSharedFlow()

    init {
        reload()
    }

    fun reload() {
        if (requestId.isBlank()) {
            _loading.value = false
            _error.value = M10FosterErrorMapper.userMessage("FOSTER_REQUEST_NOT_FOUND")
            return
        }
        viewModelScope.launch {
            _loading.value = true
            requestRepository.getRequestById(requestId)
                .onSuccess {
                    _request.value = it
                    _loading.value = false
                    _error.value = null
                }
                .onFailure {
                    _loading.value = false
                    _error.value = M10FosterErrorMapper.userMessage(M10FosterErrorMapper.codeOf(it))
                }
        }
    }

    fun startPlacement() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            placementRepository.startPlacement(requestId, null)
                .onSuccess { _placementStarted.tryEmit(it.id) }
                .onFailure {
                    _error.value = M10FosterErrorMapper.userMessage(M10FosterErrorMapper.codeOf(it))
                }
            _busy.value = false
        }
    }

    companion object {
        fun factory(requestId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    FosterRequestDetailViewModel(requestId) as T
            }
    }
}

class FosterPlacementsViewModel(
    private val placementRepository: FosterPlacementRepository = DataProvider.fosterPlacementRepository,
    private val authRepository: AuthRepository = AuthProvider.repository
) : ViewModel() {
    val placements: StateFlow<List<FosterPlacement>> =
        placementRepository.observeActivePlacementsForUser(
            authRepository.getCurrentUser()?.id.orEmpty()
        ).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                FosterPlacementsViewModel() as T
        }
    }
}

class FosterPlacementDetailViewModel(
    private val placementId: String,
    private val placementRepository: FosterPlacementRepository = DataProvider.fosterPlacementRepository
) : ViewModel() {
    private val _placement = MutableStateFlow<FosterPlacement?>(null)
    val placement: StateFlow<FosterPlacement?> = _placement.asStateFlow()
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        if (placementId.isBlank()) {
            _loading.value = false
            _error.value = M10FosterErrorMapper.userMessage("FOSTER_PLACEMENT_NOT_FOUND")
        } else {
            viewModelScope.launch {
                placementRepository.getPlacementById(placementId)
                    .onSuccess {
                        _placement.value = it
                        _loading.value = false
                    }
                    .onFailure {
                        _loading.value = false
                        _error.value = M10FosterErrorMapper.userMessage(M10FosterErrorMapper.codeOf(it))
                    }
            }
        }
    }

    companion object {
        fun factory(placementId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    FosterPlacementDetailViewModel(placementId) as T
            }
    }
}
