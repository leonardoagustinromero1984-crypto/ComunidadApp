package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.CreateM18EventInput
import com.comunidapp.app.data.model.M18CommunityEvent
import com.comunidapp.app.data.model.M18EventCapacitySummary
import com.comunidapp.app.data.model.M18EventReference
import com.comunidapp.app.data.model.M18EventOperationsSummary
import com.comunidapp.app.data.model.M18EventParticipantItem
import com.comunidapp.app.data.model.M18EventSearchFilter
import com.comunidapp.app.data.model.M18EventStatus
import com.comunidapp.app.data.model.M18EventType
import com.comunidapp.app.data.model.M18MockOrganizations
import com.comunidapp.app.data.model.M18PublicEvent
import com.comunidapp.app.data.model.M18PublicRegistrationStats
import com.comunidapp.app.data.model.M18RegistrationStatus
import com.comunidapp.app.data.model.UpdateM18EventCapacityInput
import com.comunidapp.app.data.model.UpdateM18EventDetailsInput
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.remote.supabase.m18.M18EventErrorMapper
import com.comunidapp.app.data.repository.M18EventRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

sealed class M18EventsListUiState {
    data object Loading : M18EventsListUiState()
    data object Empty : M18EventsListUiState()
    data class Content(val items: List<M18PublicEvent>) : M18EventsListUiState()
    data class Error(val message: String) : M18EventsListUiState()
}

class M18EventsListViewModel(
    private val repository: M18EventRepository = DataProvider.m18EventRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<M18EventsListUiState>(M18EventsListUiState.Loading)
    val uiState: StateFlow<M18EventsListUiState> = _uiState.asStateFlow()
    private val _filter = MutableStateFlow(M18EventSearchFilter())
    val filter: StateFlow<M18EventSearchFilter> = _filter.asStateFlow()
    private var loadJob: Job? = null

    init { load() }

    fun setQuery(value: String) {
        _filter.value = _filter.value.copy(query = value)
        load()
    }

    fun setType(type: M18EventType?) {
        _filter.value = _filter.value.copy(type = type)
        load()
    }

    fun setWithOpenSpotsOnly(value: Boolean) {
        _filter.value = _filter.value.copy(withOpenSpotsOnly = value)
        load()
    }

    fun setCompletedOnly(value: Boolean) {
        _filter.value = _filter.value.copy(
            completedOnly = value,
            activeOnly = if (value) false else _filter.value.activeOnly
        )
        load()
    }

    fun setOrganization(organizationId: String?) {
        _filter.value = _filter.value.copy(organizationId = organizationId)
        load()
    }

    fun setLocationQuery(value: String) {
        _filter.value = _filter.value.copy(locationQuery = value)
        load()
    }

    fun clearFilters() {
        _filter.value = M18EventSearchFilter()
        load()
    }

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = M18EventsListUiState.Loading
            repository.searchPublicEvents(_filter.value)
                .onSuccess { list ->
                    _uiState.value = if (list.isEmpty()) M18EventsListUiState.Empty
                    else M18EventsListUiState.Content(list)
                }
                .onFailure {
                    _uiState.value = M18EventsListUiState.Error(
                        M18EventErrorMapper.userMessage(M18EventErrorMapper.codeOf(it))
                    )
                }
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M18EventsListViewModel() as T
        }
    }
}

class M18EventDetailViewModel(
    private val eventId: String,
    private val repository: M18EventRepository = DataProvider.m18EventRepository
) : ViewModel() {
    private val _event = MutableStateFlow<M18PublicEvent?>(null)
    val event: StateFlow<M18PublicEvent?> = _event.asStateFlow()
    private val _stats = MutableStateFlow<M18PublicRegistrationStats?>(null)
    val stats: StateFlow<M18PublicRegistrationStats?> = _stats.asStateFlow()
    private val _myRegistration = MutableStateFlow<M18RegistrationStatus?>(null)
    val myRegistration: StateFlow<M18RegistrationStatus?> = _myRegistration.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _participation = MutableStateFlow<M18EventParticipationUiState>(M18EventParticipationUiState.Loading)
    val participation: StateFlow<M18EventParticipationUiState> = _participation.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _participation.value = M18EventParticipationUiState.Loading
            repository.getPublicEventById(eventId)
                .onSuccess { ev ->
                    _event.value = ev
                    repository.observePublicRegistrationStats(eventId)
                        .onSuccess { _stats.value = it }
                    val reg = repository.getMyRegistration(eventId)?.status
                    _myRegistration.value = reg
                    _participation.value = resolveParticipation(ev, reg)
                }
                .onFailure {
                    val code = M18EventErrorMapper.codeOf(it)
                    _message.value = M18EventErrorMapper.userMessage(code)
                    _participation.value = when (code) {
                        "NOT_AUTHENTICATED" -> M18EventParticipationUiState.NotAuthenticated
                        "M18_EVENT_TERMINAL" -> M18EventParticipationUiState.EventClosed
                        else -> M18EventParticipationUiState.Error(
                            M18EventErrorMapper.userMessage(code)
                        )
                    }
                }
            _loading.value = false
        }
    }

    private fun resolveParticipation(
        event: M18PublicEvent,
        registration: M18RegistrationStatus?
    ): M18EventParticipationUiState = when {
        registration == M18RegistrationStatus.REGISTERED -> M18EventParticipationUiState.Registered
        registration == M18RegistrationStatus.WAITLISTED -> M18EventParticipationUiState.Waitlisted
        registration == M18RegistrationStatus.CANCELLED -> M18EventParticipationUiState.Cancelled
        registration == M18RegistrationStatus.CHECKED_IN ||
            registration == M18RegistrationStatus.ATTENDED ->
            M18EventParticipationUiState.Registered
        registration == M18RegistrationStatus.REJECTED -> M18EventParticipationUiState.EventClosed
        !event.isRegistrationOpen && event.isFull && !event.isWaitlistOpen ->
            M18EventParticipationUiState.EventFull
        event.status.isTerminal -> M18EventParticipationUiState.EventClosed
        event.isRegistrationOpen -> M18EventParticipationUiState.Available
        else -> M18EventParticipationUiState.EventClosed
    }

    fun register() {
        viewModelScope.launch {
            repository.registerForEvent(eventId)
                .onSuccess {
                    _message.value = when (it.status) {
                        M18RegistrationStatus.REGISTERED -> "Inscripción confirmada."
                        M18RegistrationStatus.WAITLISTED -> "Estás en lista de espera."
                        else -> "Inscripción registrada."
                    }
                    refresh()
                }
                .onFailure {
                    _message.value = M18EventErrorMapper.userMessage(M18EventErrorMapper.codeOf(it))
                }
        }
    }

    fun cancelRegistration() {
        viewModelScope.launch {
            repository.cancelRegistration(eventId)
                .onSuccess {
                    _message.value = "Inscripción cancelada."
                    refresh()
                }
                .onFailure {
                    _message.value = M18EventErrorMapper.userMessage(M18EventErrorMapper.codeOf(it))
                }
        }
    }

    fun scheduleReminder() {
        viewModelScope.launch {
            repository.scheduleReminder(eventId)
                .onSuccess {
                    _message.value = "Recordatorio programado (mock — requiere infra M06)."
                    refresh()
                }
                .onFailure {
                    _message.value = M18EventErrorMapper.userMessage(M18EventErrorMapper.codeOf(it))
                }
        }
    }

    fun consumeMessage() { _message.value = null }

    companion object {
        fun factory(eventId: String): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M18EventDetailViewModel(eventId) as T
        }
    }
}

sealed class M18EventManageUiState {
    data object Loading : M18EventManageUiState()
    data object PermissionDenied : M18EventManageUiState()
    data object NoEvents : M18EventManageUiState()
    data class Content(
        val organizationId: String,
        val events: List<M18CommunityEvent>,
        val summaryById: Map<String, M18EventCapacitySummary>
    ) : M18EventManageUiState()
    data class Error(val message: String) : M18EventManageUiState()
}

class M18EventManageViewModel(
    private val repository: M18EventRepository = DataProvider.m18EventRepository
) : ViewModel() {
    private val _selectedOrg = MutableStateFlow(M18MockOrganizations.MANAGE_ORGANIZATION_IDS.first())
    val selectedOrg: StateFlow<String> = _selectedOrg.asStateFlow()
    private val _uiState = MutableStateFlow<M18EventManageUiState>(M18EventManageUiState.Loading)
    val uiState: StateFlow<M18EventManageUiState> = _uiState.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private var observeJob: Job? = null

    init { selectOrganization(_selectedOrg.value) }

    fun selectOrganization(orgId: String) {
        _selectedOrg.value = orgId
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            if (!repository.canManageOrganization(orgId)) {
                _uiState.value = M18EventManageUiState.PermissionDenied
                return@launch
            }
            repository.observeEventsForOrganization(orgId).collect { events ->
                val summaries = events.associate { e ->
                    e.id to (repository.observeCapacitySummary(e.id).getOrNull()
                        ?: com.comunidapp.app.data.model.M18EventCapacitySummary(
                            e.maxCapacity, 0, 0, e.maxCapacity, false, e.waitlistEnabled
                        ))
                }
                _uiState.value = when {
                    events.isEmpty() -> M18EventManageUiState.NoEvents
                    else -> M18EventManageUiState.Content(orgId, events, summaries)
                }
            }
        }
    }

    fun publish(eventId: String) = mutate { repository.publishEvent(eventId) }
    fun pause(eventId: String) = mutate { repository.pauseEvent(eventId) }
    fun complete(eventId: String) = mutate { repository.completeEvent(eventId) }
    fun cancel(eventId: String) = mutate { repository.cancelEvent(eventId) }

    fun checkIn(registrationId: String) {
        viewModelScope.launch {
            repository.checkInRegistration(registrationId)
                .onSuccess { _message.value = "Check-in registrado." }
                .onFailure {
                    _message.value = M18EventErrorMapper.userMessage(M18EventErrorMapper.codeOf(it))
                }
        }
    }

    private fun mutate(block: suspend () -> Result<M18CommunityEvent>) {
        viewModelScope.launch {
            block().onFailure {
                _message.value = M18EventErrorMapper.userMessage(M18EventErrorMapper.codeOf(it))
            }
        }
    }

    fun consumeMessage() { _message.value = null }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M18EventManageViewModel() as T
        }
    }
}

sealed class M18EventParticipationUiState {
    data object Loading : M18EventParticipationUiState()
    data object Available : M18EventParticipationUiState()
    data object Registered : M18EventParticipationUiState()
    data object Waitlisted : M18EventParticipationUiState()
    data object Cancelled : M18EventParticipationUiState()
    data object EventFull : M18EventParticipationUiState()
    data object EventClosed : M18EventParticipationUiState()
    data object NotAuthenticated : M18EventParticipationUiState()
    data class Error(val message: String) : M18EventParticipationUiState()
}

sealed class M18EventOperationsUiState {
    data object Loading : M18EventOperationsUiState()
    data object PermissionDenied : M18EventOperationsUiState()
    data class Content(
        val summary: M18EventOperationsSummary,
        val participants: List<M18EventParticipantItem>
    ) : M18EventOperationsUiState()
    data class Error(val message: String) : M18EventOperationsUiState()
}

class M18EventOperationsViewModel(
    private val eventId: String,
    private val repository: M18EventRepository = DataProvider.m18EventRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<M18EventOperationsUiState>(M18EventOperationsUiState.Loading)
    val uiState: StateFlow<M18EventOperationsUiState> = _uiState.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = M18EventOperationsUiState.Loading
            val summary = repository.observeOperationsSummary(eventId)
            val participants = repository.listParticipantItems(eventId)
            when {
                summary.isFailure && M18EventErrorMapper.codeOf(summary.exceptionOrNull()!!) ==
                    "M18_PERMISSION_DENIED" ->
                    _uiState.value = M18EventOperationsUiState.PermissionDenied
                summary.isSuccess && participants.isSuccess ->
                    _uiState.value = M18EventOperationsUiState.Content(
                        summary.getOrThrow(),
                        participants.getOrThrow()
                    )
                else -> _uiState.value = M18EventOperationsUiState.Error(
                    M18EventErrorMapper.userMessage(
                        M18EventErrorMapper.codeOf(
                            summary.exceptionOrNull() ?: participants.exceptionOrNull()!!
                        )
                    )
                )
            }
        }
    }

    fun checkIn(registrationId: String) {
        viewModelScope.launch {
            repository.checkInRegistration(registrationId)
                .onSuccess { refresh() }
                .onFailure {
                    _message.value = M18EventErrorMapper.userMessage(M18EventErrorMapper.codeOf(it))
                }
        }
    }

    fun markAttendance(registrationId: String) {
        viewModelScope.launch {
            repository.markAttendance(registrationId)
                .onSuccess { refresh() }
                .onFailure {
                    _message.value = M18EventErrorMapper.userMessage(M18EventErrorMapper.codeOf(it))
                }
        }
    }

    fun markNoShow(registrationId: String) {
        viewModelScope.launch {
            repository.markNoShow(registrationId)
                .onSuccess { refresh() }
                .onFailure {
                    _message.value = M18EventErrorMapper.userMessage(M18EventErrorMapper.codeOf(it))
                }
        }
    }

    fun promoteWaitlist() {
        viewModelScope.launch {
            repository.promoteNextWaitlisted(eventId)
                .onSuccess { refresh() }
                .onFailure {
                    _message.value = M18EventErrorMapper.userMessage(M18EventErrorMapper.codeOf(it))
                }
        }
    }

    fun consumeMessage() { _message.value = null }

    companion object {
        fun factory(eventId: String): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M18EventOperationsViewModel(eventId) as T
        }
    }
}

data class M18EventEditDraft(
    val organizationId: String = M18MockOrganizations.ORG_NORTE,
    val title: String = "",
    val description: String = "",
    val eventType: M18EventType = M18EventType.COMMUNITY_GATHERING,
    val maxCapacity: Int = 20,
    val waitlistEnabled: Boolean = true,
    val venueName: String = "",
    val publicLocationText: String = "",
    val petPublicName: String = "",
    val startsAtMillis: Long = System.currentTimeMillis() + 7 * 86_400_000L,
    val durationHours: Int = 3
)

sealed class M18EventEditUiState {
    data object DraftEditing : M18EventEditUiState()
    data object Saving : M18EventEditUiState()
    data class Error(val message: String) : M18EventEditUiState()
    data class Saved(val eventId: String) : M18EventEditUiState()
}

class M18EventEditViewModel(
    private val existingEventId: String?,
    private val repository: M18EventRepository = DataProvider.m18EventRepository
) : ViewModel() {
    private val _draft = MutableStateFlow(M18EventEditDraft())
    val draft: StateFlow<M18EventEditDraft> = _draft.asStateFlow()
    private val _uiState = MutableStateFlow<M18EventEditUiState>(M18EventEditUiState.DraftEditing)
    val uiState: StateFlow<M18EventEditUiState> = _uiState.asStateFlow()

    init {
        existingEventId?.let { id ->
            viewModelScope.launch {
                repository.refreshEvent(id).onSuccess { e ->
                    val durationHours = ((e.endsAt - e.startsAt) / 3_600_000L).toInt().coerceAtLeast(1)
                    _draft.value = M18EventEditDraft(
                        organizationId = e.organizationId,
                        title = e.title,
                        description = e.description,
                        eventType = e.eventType,
                        maxCapacity = e.maxCapacity,
                        waitlistEnabled = e.waitlistEnabled,
                        venueName = e.venueName.orEmpty(),
                        publicLocationText = e.reference.publicLocationText.orEmpty(),
                        petPublicName = e.reference.petPublicName.orEmpty(),
                        startsAtMillis = e.startsAt,
                        durationHours = durationHours
                    )
                }
            }
        }
    }

    fun updateDraft(transform: (M18EventEditDraft) -> M18EventEditDraft) {
        _draft.value = transform(_draft.value)
    }

    fun save() {
        viewModelScope.launch {
            _uiState.value = M18EventEditUiState.Saving
            val d = _draft.value
            val endsAt = d.startsAtMillis + d.durationHours * 3_600_000L
            val ref = M18EventReference(
                petPublicName = d.petPublicName.takeIf { it.isNotBlank() },
                publicLocationText = d.publicLocationText.takeIf { it.isNotBlank() }
            )
            val result = if (existingEventId == null) {
                repository.createEvent(
                    CreateM18EventInput(
                        organizationId = d.organizationId,
                        title = d.title,
                        description = d.description,
                        eventType = d.eventType,
                        maxCapacity = d.maxCapacity,
                        waitlistEnabled = d.waitlistEnabled,
                        venueName = d.venueName.takeIf { it.isNotBlank() },
                        reference = ref,
                        startsAt = d.startsAtMillis,
                        endsAt = endsAt,
                        checkInOpensAt = d.startsAtMillis - 3_600_000L,
                        checkInClosesAt = endsAt
                    )
                )
            } else {
                repository.updateEventDetails(
                    UpdateM18EventDetailsInput(
                        eventId = existingEventId,
                        title = d.title,
                        description = d.description,
                        eventType = d.eventType,
                        venueName = d.venueName.takeIf { it.isNotBlank() },
                        reference = ref,
                        startsAt = d.startsAtMillis,
                        endsAt = endsAt
                    )
                ).fold(
                    onSuccess = { updated ->
                        repository.updateEventCapacity(
                            UpdateM18EventCapacityInput(existingEventId, d.maxCapacity, d.waitlistEnabled)
                        ).map { updated }
                    },
                    onFailure = { Result.failure(it) }
                )
            }
            result.fold(
                onSuccess = { _uiState.value = M18EventEditUiState.Saved(it.id) },
                onFailure = {
                    _uiState.value = M18EventEditUiState.Error(
                        M18EventErrorMapper.userMessage(M18EventErrorMapper.codeOf(it))
                    )
                }
            )
        }
    }

    companion object {
        fun factory(eventId: String?): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M18EventEditViewModel(eventId) as T
        }
    }
}

fun m18EventTypeLabel(type: M18EventType): String = when (type) {
    M18EventType.ADOPTION_FAIR -> "Feria de adopciones"
    M18EventType.VOLUNTEER_DAY -> "Jornada de voluntariado"
    M18EventType.TRAINING_WORKSHOP -> "Taller / capacitación"
    M18EventType.COMMUNITY_GATHERING -> "Encuentro comunitario"
    M18EventType.FREE_FUNDRAISER -> "Recaudación gratuita"
    M18EventType.AWARENESS_WALK -> "Caminata de concientización"
}

fun m18EventStatusLabel(status: M18EventStatus): String = when (status) {
    M18EventStatus.DRAFT -> "Borrador"
    M18EventStatus.PUBLISHED -> "Publicado"
    M18EventStatus.PAUSED -> "Pausado"
    M18EventStatus.COMPLETED -> "Completado"
    M18EventStatus.CANCELLED -> "Cancelado"
}

fun m18RegistrationStatusLabel(status: M18RegistrationStatus): String = when (status) {
    M18RegistrationStatus.REGISTERED -> "Inscripto"
    M18RegistrationStatus.WAITLISTED -> "Lista de espera"
    M18RegistrationStatus.CANCELLED -> "Cancelado"
    M18RegistrationStatus.CHECKED_IN -> "Check-in realizado"
    M18RegistrationStatus.ATTENDED -> "Asistió"
    M18RegistrationStatus.NO_SHOW -> "No asistió"
    M18RegistrationStatus.REJECTED -> "Rechazado"
}
