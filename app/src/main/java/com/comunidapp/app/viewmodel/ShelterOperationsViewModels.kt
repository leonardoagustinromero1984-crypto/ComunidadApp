package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.ShelterIntakeType
import com.comunidapp.app.data.model.ShelterPetEndReason
import com.comunidapp.app.data.model.ShelterPetPlacement
import com.comunidapp.app.data.model.ShelterPetPlacementStatus
import com.comunidapp.app.data.model.ShelterProfile
import com.comunidapp.app.data.model.ShelterPublicListing
import com.comunidapp.app.data.model.ShelterStatus
import com.comunidapp.app.data.model.ShelterVolunteerAssignment
import com.comunidapp.app.data.model.ShelterVolunteerRole
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.remote.supabase.m11.M11ShelterErrorMapper
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.CreateShelterProfileInput
import com.comunidapp.app.data.repository.ShelterPetRepository
import com.comunidapp.app.data.repository.ShelterProfileRepository
import com.comunidapp.app.data.repository.ShelterVolunteerRepository
import com.comunidapp.app.data.repository.UpdateShelterProfileInput
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class ShelterListUiState {
    data object Loading : ShelterListUiState()
    data object Empty : ShelterListUiState()
    data class Content(val items: List<ShelterPublicListing>) : ShelterListUiState()
    data class Error(val message: String) : ShelterListUiState()
}

class ShelterPublicListViewModel(
    private val repo: ShelterProfileRepository = DataProvider.shelterProfileRepository
) : ViewModel() {
    private val _ui = MutableStateFlow<ShelterListUiState>(ShelterListUiState.Loading)
    val uiState: StateFlow<ShelterListUiState> = _ui.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _ui.value = ShelterListUiState.Loading
            repo.observePublicShelters().collect { list ->
                _ui.value = when {
                    list.isEmpty() -> ShelterListUiState.Empty
                    else -> ShelterListUiState.Content(list)
                }
            }
        }
    }

    companion object {
        fun factory() = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ShelterPublicListViewModel() as T
        }
    }
}

sealed class MySheltersUiState {
    data object Loading : MySheltersUiState()
    data object Empty : MySheltersUiState()
    data class Content(val items: List<ShelterProfile>) : MySheltersUiState()
    data class Error(val message: String) : MySheltersUiState()
}

class MySheltersViewModel(
    private val auth: AuthRepository = AuthProvider.repository,
    private val repo: ShelterProfileRepository = DataProvider.shelterProfileRepository
) : ViewModel() {
    private val _ui = MutableStateFlow<MySheltersUiState>(MySheltersUiState.Loading)
    val uiState: StateFlow<MySheltersUiState> = _ui.asStateFlow()

    init {
        val uid = auth.getCurrentUser()?.id.orEmpty()
        if (uid.isBlank()) {
            _ui.value = MySheltersUiState.Error(M11ShelterErrorMapper.userMessage("NOT_AUTHENTICATED"))
        } else {
            viewModelScope.launch {
                repo.observeMyShelters(uid).collect { list ->
                    _ui.value = when {
                        list.isEmpty() -> MySheltersUiState.Empty
                        else -> MySheltersUiState.Content(list)
                    }
                }
            }
        }
    }

    companion object {
        fun factory() = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = MySheltersViewModel() as T
        }
    }
}

class ShelterFormViewModel(
    private val editId: String? = null,
    private val repo: ShelterProfileRepository = DataProvider.shelterProfileRepository
) : ViewModel() {
    private val _submitting = MutableStateFlow(false)
    val submitting = _submitting.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _saved = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val saved = _saved.asSharedFlow()
    private val _existing = MutableStateFlow<ShelterProfile?>(null)
    val existing = _existing.asStateFlow()

    init {
        if (!editId.isNullOrBlank()) {
            viewModelScope.launch {
                repo.getShelterById(editId).onSuccess { _existing.value = it }
                    .onFailure {
                        _error.value = M11ShelterErrorMapper.userMessage(M11ShelterErrorMapper.codeOf(it))
                    }
            }
        }
    }

    fun create(input: CreateShelterProfileInput) {
        if (_submitting.value) return
        viewModelScope.launch {
            _submitting.value = true
            _error.value = null
            repo.createShelter(input)
                .onSuccess { _saved.tryEmit(it.id) }
                .onFailure {
                    _error.value = M11ShelterErrorMapper.userMessage(M11ShelterErrorMapper.codeOf(it))
                }
            _submitting.value = false
        }
    }

    fun update(input: UpdateShelterProfileInput) {
        if (_submitting.value) return
        viewModelScope.launch {
            _submitting.value = true
            _error.value = null
            repo.updateShelter(input)
                .onSuccess { _saved.tryEmit(it.id) }
                .onFailure {
                    _error.value = M11ShelterErrorMapper.userMessage(M11ShelterErrorMapper.codeOf(it))
                }
            _submitting.value = false
        }
    }

    companion object {
        fun factory(editId: String? = null) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ShelterFormViewModel(editId) as T
        }
    }
}

sealed class ShelterDetailUiState {
    data object Loading : ShelterDetailUiState()
    data class Content(val shelter: ShelterProfile) : ShelterDetailUiState()
    data class Error(val message: String) : ShelterDetailUiState()
}

class ShelterOpsDetailViewModel(
    private val shelterId: String,
    private val repo: ShelterProfileRepository = DataProvider.shelterProfileRepository
) : ViewModel() {
    private val _ui = MutableStateFlow<ShelterDetailUiState>(ShelterDetailUiState.Loading)
    val uiState = _ui.asStateFlow()

    init { reload() }

    fun reload() {
        if (shelterId.isBlank()) {
            _ui.value = ShelterDetailUiState.Error(M11ShelterErrorMapper.userMessage("SHELTER_NOT_FOUND"))
            return
        }
        viewModelScope.launch {
            _ui.value = ShelterDetailUiState.Loading
            repo.getShelterById(shelterId)
                .onSuccess { _ui.value = ShelterDetailUiState.Content(it) }
                .onFailure {
                    _ui.value = ShelterDetailUiState.Error(
                        M11ShelterErrorMapper.userMessage(M11ShelterErrorMapper.codeOf(it))
                    )
                }
        }
    }

    companion object {
        fun factory(id: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ShelterOpsDetailViewModel(id) as T
        }
    }
}

data class ShelterDashboardData(
    val shelter: ShelterProfile,
    val pets: List<ShelterPetPlacement>,
    val volunteers: List<ShelterVolunteerAssignment>
)

sealed class ShelterDashboardUiState {
    data object Loading : ShelterDashboardUiState()
    data class Content(val data: ShelterDashboardData) : ShelterDashboardUiState()
    data class Error(val message: String) : ShelterDashboardUiState()
}

class ShelterDashboardViewModel(
    private val shelterId: String,
    private val profiles: ShelterProfileRepository = DataProvider.shelterProfileRepository,
    private val pets: ShelterPetRepository = DataProvider.shelterPetRepository,
    private val volunteers: ShelterVolunteerRepository = DataProvider.shelterVolunteerRepository
) : ViewModel() {
    private val _ui = MutableStateFlow<ShelterDashboardUiState>(ShelterDashboardUiState.Loading)
    val uiState = _ui.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    init {
        if (shelterId.isBlank()) {
            _ui.value = ShelterDashboardUiState.Error(M11ShelterErrorMapper.userMessage("SHELTER_NOT_FOUND"))
        } else {
            viewModelScope.launch {
                val shelter = profiles.getShelterById(shelterId).getOrElse {
                    _ui.value = ShelterDashboardUiState.Error(
                        M11ShelterErrorMapper.userMessage(M11ShelterErrorMapper.codeOf(it))
                    )
                    return@launch
                }
                kotlinx.coroutines.flow.combine(
                    pets.observeShelterPets(shelterId),
                    volunteers.observeVolunteers(shelterId)
                ) { p, v ->
                    ShelterDashboardData(
                        shelter = profiles.getShelterById(shelterId).getOrDefault(shelter),
                        pets = p,
                        volunteers = v
                    )
                }.collect { _ui.value = ShelterDashboardUiState.Content(it) }
            }
        }
    }

    fun changeStatus(status: ShelterStatus) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            profiles.changeStatus(shelterId, status)
                .onFailure {
                    _error.value = M11ShelterErrorMapper.userMessage(M11ShelterErrorMapper.codeOf(it))
                }
            _busy.value = false
        }
    }

    companion object {
        fun factory(id: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ShelterDashboardViewModel(id) as T
        }
    }
}

class ShelterPetsViewModel(
    private val shelterId: String,
    private val repo: ShelterPetRepository = DataProvider.shelterPetRepository
) : ViewModel() {
    val pets: StateFlow<List<ShelterPetPlacement>> =
        repo.observeShelterPets(shelterId)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun changeStatus(id: String, status: ShelterPetPlacementStatus) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            repo.changePlacementStatus(id, status)
                .onFailure {
                    _error.value = M11ShelterErrorMapper.userMessage(M11ShelterErrorMapper.codeOf(it))
                }
            _busy.value = false
        }
    }

    fun release(id: String, reason: ShelterPetEndReason) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            repo.releasePet(id, reason, null)
                .onFailure {
                    _error.value = M11ShelterErrorMapper.userMessage(M11ShelterErrorMapper.codeOf(it))
                }
            _busy.value = false
        }
    }

    companion object {
        fun factory(id: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ShelterPetsViewModel(id) as T
        }
    }
}

class ShelterIntakeViewModel(
    private val shelterId: String,
    private val repo: ShelterPetRepository = DataProvider.shelterPetRepository
) : ViewModel() {
    private val _submitting = MutableStateFlow(false)
    val submitting = _submitting.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _saved = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val saved = _saved.asSharedFlow()

    fun admit(petId: String, type: ShelterIntakeType, notes: String?, reserveOnly: Boolean) {
        if (_submitting.value) return
        if (shelterId.isBlank()) {
            _error.value = M11ShelterErrorMapper.userMessage("SHELTER_NOT_FOUND")
            return
        }
        viewModelScope.launch {
            _submitting.value = true
            _error.value = null
            val result = if (reserveOnly) {
                repo.reserveCapacity(shelterId, petId, type, notes)
            } else {
                repo.admitPet(shelterId, petId, type, notes, null)
            }
            result.onSuccess { _saved.tryEmit(it.id) }
                .onFailure {
                    _error.value = M11ShelterErrorMapper.userMessage(M11ShelterErrorMapper.codeOf(it))
                }
            _submitting.value = false
        }
    }

    companion object {
        fun factory(id: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ShelterIntakeViewModel(id) as T
        }
    }
}

class ShelterPetDetailViewModel(
    private val placementId: String,
    private val repo: ShelterPetRepository = DataProvider.shelterPetRepository
) : ViewModel() {
    private val _placement = MutableStateFlow<ShelterPetPlacement?>(null)
    val placement = _placement.asStateFlow()
    private val _loading = MutableStateFlow(true)
    val loading = _loading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    init {
        if (placementId.isBlank()) {
            _loading.value = false
            _error.value = M11ShelterErrorMapper.userMessage("SHELTER_PET_NOT_FOUND")
        } else {
            viewModelScope.launch {
                repo.getPetPlacement(placementId)
                    .onSuccess {
                        _placement.value = it
                        _loading.value = false
                    }
                    .onFailure {
                        _loading.value = false
                        _error.value = M11ShelterErrorMapper.userMessage(M11ShelterErrorMapper.codeOf(it))
                    }
            }
        }
    }

    companion object {
        fun factory(id: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ShelterPetDetailViewModel(id) as T
        }
    }
}

class ShelterVolunteersViewModel(
    private val shelterId: String,
    private val repo: ShelterVolunteerRepository = DataProvider.shelterVolunteerRepository
) : ViewModel() {
    val volunteers = repo.observeVolunteers(shelterId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun accept(id: String) = mutate { repo.acceptAssignment(id) }
    fun pause(id: String) = mutate { repo.pauseAssignment(id) }
    fun end(id: String) = mutate { repo.endAssignment(id) }

    private fun mutate(block: suspend () -> Result<*>) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            block().onFailure {
                _error.value = M11ShelterErrorMapper.userMessage(M11ShelterErrorMapper.codeOf(it))
            }
            _busy.value = false
        }
    }

    companion object {
        fun factory(id: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ShelterVolunteersViewModel(id) as T
        }
    }
}

class ShelterVolunteerInviteViewModel(
    private val shelterId: String,
    private val repo: ShelterVolunteerRepository = DataProvider.shelterVolunteerRepository
) : ViewModel() {
    private val _submitting = MutableStateFlow(false)
    val submitting = _submitting.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _saved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saved = _saved.asSharedFlow()

    fun invite(userId: String, role: ShelterVolunteerRole, notes: String?) {
        if (_submitting.value) return
        viewModelScope.launch {
            _submitting.value = true
            repo.inviteVolunteer(shelterId, userId, role, notes)
                .onSuccess { _saved.tryEmit(Unit) }
                .onFailure {
                    _error.value = M11ShelterErrorMapper.userMessage(M11ShelterErrorMapper.codeOf(it))
                }
            _submitting.value = false
        }
    }

    companion object {
        fun factory(id: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ShelterVolunteerInviteViewModel(id) as T
        }
    }
}
