package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.ShelterEmergency
import com.comunidapp.app.data.model.ShelterEmergencyCategory
import com.comunidapp.app.data.model.ShelterEmergencyPublicListing
import com.comunidapp.app.data.model.ShelterEmergencySeverity
import com.comunidapp.app.data.model.ShelterEmergencyStatus
import com.comunidapp.app.data.model.ShelterEmergencyVisibility
import com.comunidapp.app.data.model.ShelterEvent
import com.comunidapp.app.data.model.ShelterEventPublicListing
import com.comunidapp.app.data.model.ShelterEventRegistration
import com.comunidapp.app.data.model.ShelterEventStatus
import com.comunidapp.app.data.model.ShelterEventType
import com.comunidapp.app.data.model.ShelterEventVisibility
import com.comunidapp.app.data.model.ShelterOperationalSummary
import com.comunidapp.app.data.model.ShelterReportExport
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.remote.supabase.m11.M11ShelterErrorMapper
import com.comunidapp.app.data.repository.CreateShelterEmergencyInput
import com.comunidapp.app.data.repository.CreateShelterEventInput
import com.comunidapp.app.data.repository.ShelterEmergencyRepository
import com.comunidapp.app.data.repository.ShelterEventRepository
import com.comunidapp.app.data.repository.ShelterReportRepository
import com.comunidapp.app.data.repository.UpdateShelterEmergencyInput
import com.comunidapp.app.data.repository.UpdateShelterEventInput
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class ShelterPublicEmergenciesUiState {
    data object Loading : ShelterPublicEmergenciesUiState()
    data object Empty : ShelterPublicEmergenciesUiState()
    data class Content(val items: List<ShelterEmergencyPublicListing>) : ShelterPublicEmergenciesUiState()
    data class Error(val message: String) : ShelterPublicEmergenciesUiState()
}

class ShelterPublicEmergenciesViewModel(
    private val repo: ShelterEmergencyRepository = DataProvider.shelterEmergencyRepository
) : ViewModel() {
    private val _ui = MutableStateFlow<ShelterPublicEmergenciesUiState>(ShelterPublicEmergenciesUiState.Loading)
    val uiState: StateFlow<ShelterPublicEmergenciesUiState> = _ui.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _ui.value = ShelterPublicEmergenciesUiState.Loading
            repo.observePublicEmergencies().collect { list ->
                _ui.value = when {
                    list.isEmpty() -> ShelterPublicEmergenciesUiState.Empty
                    else -> ShelterPublicEmergenciesUiState.Content(list)
                }
            }
        }
    }

    companion object {
        fun factory() = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ShelterPublicEmergenciesViewModel() as T
        }
    }
}

class ShelterEmergenciesViewModel(
    private val shelterId: String,
    private val repo: ShelterEmergencyRepository = DataProvider.shelterEmergencyRepository
) : ViewModel() {
    val emergencies: StateFlow<List<ShelterEmergency>> =
        repo.observeShelterEmergencies(shelterId)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    companion object {
        fun factory(shelterId: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ShelterEmergenciesViewModel(shelterId) as T
        }
    }
}

sealed class ShelterEmergencyDetailUiState {
    data object Loading : ShelterEmergencyDetailUiState()
    data class Content(val emergency: ShelterEmergency) : ShelterEmergencyDetailUiState()
    data class Error(val message: String) : ShelterEmergencyDetailUiState()
}

class ShelterEmergencyDetailViewModel(
    private val emergencyId: String,
    private val repo: ShelterEmergencyRepository = DataProvider.shelterEmergencyRepository
) : ViewModel() {
    private val _ui = MutableStateFlow<ShelterEmergencyDetailUiState>(ShelterEmergencyDetailUiState.Loading)
    val uiState = _ui.asStateFlow()
    private val _emergency = MutableStateFlow<ShelterEmergency?>(null)
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _resolved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val resolved = _resolved.asSharedFlow()

    init {
        if (emergencyId.isBlank()) {
            _ui.value = ShelterEmergencyDetailUiState.Error(
                M11ShelterErrorMapper.userMessage("SHELTER_EMERGENCY_NOT_FOUND")
            )
        } else {
            refresh()
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            repo.getEmergency(emergencyId)
                .onSuccess {
                    _emergency.value = it
                    _ui.value = ShelterEmergencyDetailUiState.Content(it)
                }
                .onFailure {
                    _ui.value = ShelterEmergencyDetailUiState.Error(
                        M11ShelterErrorMapper.userMessage(M11ShelterErrorMapper.codeOf(it))
                    )
                }
        }
    }

    fun changeStatus(status: ShelterEmergencyStatus) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            repo.changeEmergencyStatus(emergencyId, status)
                .onSuccess {
                    _emergency.value = it
                    _ui.value = ShelterEmergencyDetailUiState.Content(it)
                }
                .onFailure {
                    _error.value = M11ShelterErrorMapper.userMessage(M11ShelterErrorMapper.codeOf(it))
                }
            _busy.value = false
        }
    }

    fun resolve(notes: String) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            repo.resolveEmergency(emergencyId, notes)
                .onSuccess {
                    _emergency.value = it
                    _ui.value = ShelterEmergencyDetailUiState.Content(it)
                    _resolved.tryEmit(Unit)
                }
                .onFailure {
                    _error.value = M11ShelterErrorMapper.userMessage(M11ShelterErrorMapper.codeOf(it))
                }
            _busy.value = false
        }
    }

    companion object {
        fun factory(emergencyId: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ShelterEmergencyDetailViewModel(emergencyId) as T
        }
    }
}

class ShelterEmergencyFormViewModel(
    private val shelterId: String,
    private val emergencyId: String? = null,
    private val repo: ShelterEmergencyRepository = DataProvider.shelterEmergencyRepository
) : ViewModel() {
    private val _submitting = MutableStateFlow(false)
    val submitting = _submitting.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _saved = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val saved = _saved.asSharedFlow()
    private val _existing = MutableStateFlow<ShelterEmergency?>(null)
    val existing = _existing.asStateFlow()

    init {
        if (!emergencyId.isNullOrBlank()) {
            viewModelScope.launch {
                repo.getEmergency(emergencyId).onSuccess { _existing.value = it }
                    .onFailure {
                        _error.value = M11ShelterErrorMapper.userMessage(M11ShelterErrorMapper.codeOf(it))
                    }
            }
        }
    }

    fun create(
        title: String,
        description: String,
        category: ShelterEmergencyCategory,
        severity: ShelterEmergencySeverity,
        visibility: ShelterEmergencyVisibility,
        startsAt: Long,
        expiresAt: Long?,
        petId: String?,
        evidenceRef: String?,
        activate: Boolean
    ) {
        if (_submitting.value) return
        viewModelScope.launch {
            _submitting.value = true
            _error.value = null
            repo.createEmergency(
                CreateShelterEmergencyInput(
                    shelterProfileId = shelterId,
                    petId = petId?.trim()?.takeIf { it.isNotEmpty() },
                    title = title,
                    description = description,
                    category = category,
                    severity = severity,
                    visibility = visibility,
                    startsAt = startsAt,
                    expiresAt = expiresAt,
                    evidenceRef = evidenceRef?.trim()?.takeIf { it.isNotEmpty() },
                    activate = activate
                )
            ).onSuccess { _saved.tryEmit(it.id) }
                .onFailure {
                    _error.value = M11ShelterErrorMapper.userMessage(M11ShelterErrorMapper.codeOf(it))
                }
            _submitting.value = false
        }
    }

    fun update(
        title: String,
        description: String,
        category: ShelterEmergencyCategory,
        severity: ShelterEmergencySeverity,
        visibility: ShelterEmergencyVisibility,
        startsAt: Long,
        expiresAt: Long?,
        petId: String?,
        evidenceRef: String?
    ) {
        val id = emergencyId ?: return
        if (_submitting.value) return
        viewModelScope.launch {
            _submitting.value = true
            _error.value = null
            repo.updateEmergency(
                UpdateShelterEmergencyInput(
                    emergencyId = id,
                    title = title,
                    description = description,
                    category = category,
                    severity = severity,
                    visibility = visibility,
                    startsAt = startsAt,
                    expiresAt = expiresAt,
                    evidenceRef = evidenceRef?.trim()?.takeIf { it.isNotEmpty() },
                    petId = petId?.trim()?.takeIf { it.isNotEmpty() }
                )
            ).onSuccess { _saved.tryEmit(it.id) }
                .onFailure {
                    _error.value = M11ShelterErrorMapper.userMessage(M11ShelterErrorMapper.codeOf(it))
                }
            _submitting.value = false
        }
    }

    companion object {
        fun factory(shelterId: String, emergencyId: String? = null) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ShelterEmergencyFormViewModel(shelterId, emergencyId) as T
        }
    }
}

sealed class ShelterPublicEventsUiState {
    data object Loading : ShelterPublicEventsUiState()
    data object Empty : ShelterPublicEventsUiState()
    data class Content(val items: List<ShelterEventPublicListing>) : ShelterPublicEventsUiState()
    data class Error(val message: String) : ShelterPublicEventsUiState()
}

class ShelterPublicEventsViewModel(
    private val repo: ShelterEventRepository = DataProvider.shelterEventRepository
) : ViewModel() {
    private val _ui = MutableStateFlow<ShelterPublicEventsUiState>(ShelterPublicEventsUiState.Loading)
    val uiState: StateFlow<ShelterPublicEventsUiState> = _ui.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _ui.value = ShelterPublicEventsUiState.Loading
            repo.observePublicEvents().collect { list ->
                _ui.value = when {
                    list.isEmpty() -> ShelterPublicEventsUiState.Empty
                    else -> ShelterPublicEventsUiState.Content(list)
                }
            }
        }
    }

    companion object {
        fun factory() = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ShelterPublicEventsViewModel() as T
        }
    }
}

class ShelterEventsViewModel(
    private val shelterId: String,
    private val repo: ShelterEventRepository = DataProvider.shelterEventRepository
) : ViewModel() {
    val events: StateFlow<List<ShelterEvent>> =
        repo.observeShelterEvents(shelterId)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    companion object {
        fun factory(shelterId: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ShelterEventsViewModel(shelterId) as T
        }
    }
}

sealed class ShelterEventDetailUiState {
    data object Loading : ShelterEventDetailUiState()
    data class Content(val event: ShelterEvent) : ShelterEventDetailUiState()
    data class Error(val message: String) : ShelterEventDetailUiState()
}

class ShelterEventDetailViewModel(
    private val eventId: String,
    private val repo: ShelterEventRepository = DataProvider.shelterEventRepository
) : ViewModel() {
    private val _ui = MutableStateFlow<ShelterEventDetailUiState>(ShelterEventDetailUiState.Loading)
    val uiState = _ui.asStateFlow()
    private val _event = MutableStateFlow<ShelterEvent?>(null)
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _registered = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val registered = _registered.asSharedFlow()

    init {
        if (eventId.isBlank()) {
            _ui.value = ShelterEventDetailUiState.Error(
                M11ShelterErrorMapper.userMessage("SHELTER_EVENT_NOT_FOUND")
            )
        } else {
            refresh()
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            repo.getEvent(eventId)
                .onSuccess {
                    _event.value = it
                    _ui.value = ShelterEventDetailUiState.Content(it)
                }
                .onFailure {
                    _ui.value = ShelterEventDetailUiState.Error(
                        M11ShelterErrorMapper.userMessage(M11ShelterErrorMapper.codeOf(it))
                    )
                }
        }
    }

    fun register(notes: String? = null) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            repo.register(eventId, notes)
                .onSuccess {
                    refresh()
                    _registered.tryEmit(Unit)
                }
                .onFailure {
                    _error.value = M11ShelterErrorMapper.userMessage(M11ShelterErrorMapper.codeOf(it))
                }
            _busy.value = false
        }
    }

    fun changeStatus(status: ShelterEventStatus) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            repo.changeEventStatus(eventId, status)
                .onSuccess {
                    _event.value = it
                    _ui.value = ShelterEventDetailUiState.Content(it)
                }
                .onFailure {
                    _error.value = M11ShelterErrorMapper.userMessage(M11ShelterErrorMapper.codeOf(it))
                }
            _busy.value = false
        }
    }

    companion object {
        fun factory(eventId: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ShelterEventDetailViewModel(eventId) as T
        }
    }
}

class ShelterEventFormViewModel(
    private val shelterId: String,
    private val eventId: String? = null,
    private val repo: ShelterEventRepository = DataProvider.shelterEventRepository
) : ViewModel() {
    private val _submitting = MutableStateFlow(false)
    val submitting = _submitting.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _saved = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val saved = _saved.asSharedFlow()
    private val _existing = MutableStateFlow<ShelterEvent?>(null)
    val existing = _existing.asStateFlow()

    init {
        if (!eventId.isNullOrBlank()) {
            viewModelScope.launch {
                repo.getEvent(eventId).onSuccess { _existing.value = it }
                    .onFailure {
                        _error.value = M11ShelterErrorMapper.userMessage(M11ShelterErrorMapper.codeOf(it))
                    }
            }
        }
    }

    fun create(
        title: String,
        description: String,
        eventType: ShelterEventType,
        visibility: ShelterEventVisibility,
        startsAt: Long,
        endsAt: Long,
        capacity: Int?,
        publicLocationText: String?,
        privateLocationText: String?,
        coverAssetRef: String?,
        publish: Boolean
    ) {
        if (_submitting.value) return
        viewModelScope.launch {
            _submitting.value = true
            _error.value = null
            repo.createEvent(
                CreateShelterEventInput(
                    shelterProfileId = shelterId,
                    title = title,
                    description = description,
                    eventType = eventType,
                    visibility = visibility,
                    startsAt = startsAt,
                    endsAt = endsAt,
                    capacity = capacity,
                    publicLocationText = publicLocationText,
                    privateLocationText = privateLocationText,
                    coverAssetRef = coverAssetRef,
                    publish = publish
                )
            ).onSuccess { _saved.tryEmit(it.id) }
                .onFailure {
                    _error.value = M11ShelterErrorMapper.userMessage(M11ShelterErrorMapper.codeOf(it))
                }
            _submitting.value = false
        }
    }

    fun update(
        title: String,
        description: String,
        eventType: ShelterEventType,
        visibility: ShelterEventVisibility,
        startsAt: Long,
        endsAt: Long,
        capacity: Int?,
        publicLocationText: String?,
        privateLocationText: String?,
        coverAssetRef: String?
    ) {
        val id = eventId ?: return
        if (_submitting.value) return
        viewModelScope.launch {
            _submitting.value = true
            _error.value = null
            repo.updateEvent(
                UpdateShelterEventInput(
                    eventId = id,
                    title = title,
                    description = description,
                    eventType = eventType,
                    visibility = visibility,
                    startsAt = startsAt,
                    endsAt = endsAt,
                    capacity = capacity,
                    publicLocationText = publicLocationText,
                    privateLocationText = privateLocationText,
                    coverAssetRef = coverAssetRef
                )
            ).onSuccess { _saved.tryEmit(it.id) }
                .onFailure {
                    _error.value = M11ShelterErrorMapper.userMessage(M11ShelterErrorMapper.codeOf(it))
                }
            _submitting.value = false
        }
    }

    companion object {
        fun factory(shelterId: String, eventId: String? = null) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ShelterEventFormViewModel(shelterId, eventId) as T
        }
    }
}

class ShelterEventRegistrationsViewModel(
    private val eventId: String,
    private val repo: ShelterEventRepository = DataProvider.shelterEventRepository
) : ViewModel() {
    val registrations: StateFlow<List<ShelterEventRegistration>> =
        repo.observeRegistrations(eventId)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun markAttendance(registrationId: String, attended: Boolean) = mutate {
        repo.markAttendance(registrationId, attended)
    }

    fun cancel(registrationId: String) = mutate { repo.cancelRegistration(registrationId) }

    private fun mutate(block: suspend () -> Result<*>) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            block().onFailure {
                _error.value = M11ShelterErrorMapper.userMessage(M11ShelterErrorMapper.codeOf(it))
            }
            _busy.value = false
        }
    }

    companion object {
        fun factory(eventId: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ShelterEventRegistrationsViewModel(eventId) as T
        }
    }
}

sealed class ShelterReportsUiState {
    data object Loading : ShelterReportsUiState()
    data class Content(val summary: ShelterOperationalSummary) : ShelterReportsUiState()
    data class Error(val message: String) : ShelterReportsUiState()
}

class ShelterReportsViewModel(
    private val shelterId: String,
    private val repo: ShelterReportRepository = DataProvider.shelterReportRepository
) : ViewModel() {
    private val _ui = MutableStateFlow<ShelterReportsUiState>(ShelterReportsUiState.Loading)
    val uiState = _ui.asStateFlow()
    private val _exporting = MutableStateFlow(false)
    val exporting = _exporting.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _exported = MutableSharedFlow<ShelterReportExport>(extraBufferCapacity = 1)
    val exported = _exported.asSharedFlow()

    fun loadMetrics(from: Long, to: Long) {
        if (from > to) {
            _ui.value = ShelterReportsUiState.Error(
                M11ShelterErrorMapper.userMessage("SHELTER_REPORT_INVALID_RANGE")
            )
            return
        }
        viewModelScope.launch {
            _ui.value = ShelterReportsUiState.Loading
            _error.value = null
            repo.getOperationalSummary(shelterId, from, to)
                .onSuccess { _ui.value = ShelterReportsUiState.Content(it) }
                .onFailure {
                    _ui.value = ShelterReportsUiState.Error(
                        M11ShelterErrorMapper.userMessage(M11ShelterErrorMapper.codeOf(it))
                    )
                }
        }
    }

    fun exportCsv(from: Long, to: Long) {
        if (_exporting.value) return
        if (from > to) {
            _error.value = M11ShelterErrorMapper.userMessage("SHELTER_REPORT_INVALID_RANGE")
            return
        }
        viewModelScope.launch {
            _exporting.value = true
            _error.value = null
            repo.exportOperationalCsv(shelterId, from, to)
                .onSuccess { _exported.tryEmit(it) }
                .onFailure {
                    _error.value = M11ShelterErrorMapper.userMessage(M11ShelterErrorMapper.codeOf(it))
                }
            _exporting.value = false
        }
    }

    companion object {
        fun factory(shelterId: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ShelterReportsViewModel(shelterId) as T
        }
    }
}
