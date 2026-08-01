package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.CreateM16ShelterProfileInput
import com.comunidapp.app.data.model.M16MockOrganizations
import com.comunidapp.app.data.model.M16OpeningHours
import com.comunidapp.app.data.model.M16OpeningPeriod
import com.comunidapp.app.data.model.M16PublicContactChannel
import com.comunidapp.app.data.model.M16PublicContactChannelType
import com.comunidapp.app.data.model.M16PublicShelter
import com.comunidapp.app.data.model.M16ShelterCapacity
import com.comunidapp.app.data.model.M16ShelterNeed
import com.comunidapp.app.data.model.M16ShelterOperationalStatus
import com.comunidapp.app.data.model.M16ShelterProfile
import com.comunidapp.app.data.model.M16ShelterPublicationStatus
import com.comunidapp.app.data.model.M16ShelterSearchFilter
import com.comunidapp.app.data.model.M16ShelterService
import com.comunidapp.app.data.model.M16ShelterVerificationFilter
import com.comunidapp.app.data.model.UpdateM16ShelterPublicInput
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.remote.supabase.m16.M16ShelterErrorMapper
import com.comunidapp.app.data.repository.M16ShelterRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

sealed class M16SheltersListUiState {
    data object Loading : M16SheltersListUiState()
    data object Empty : M16SheltersListUiState()
    data class Content(val items: List<M16PublicShelter>) : M16SheltersListUiState()
    data class Error(val message: String) : M16SheltersListUiState()
}

class M16SheltersListViewModel(
    private val repository: M16ShelterRepository = DataProvider.m16ShelterRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<M16SheltersListUiState>(M16SheltersListUiState.Loading)
    val uiState: StateFlow<M16SheltersListUiState> = _uiState.asStateFlow()

    private val _filter = MutableStateFlow(M16ShelterSearchFilter())
    val filter: StateFlow<M16ShelterSearchFilter> = _filter.asStateFlow()

    init {
        load()
    }

    fun setQuery(value: String) {
        _filter.value = _filter.value.copy(query = value)
        load()
    }

    fun setOperationalStatus(status: M16ShelterOperationalStatus?) {
        _filter.value = _filter.value.copy(operationalStatus = status)
        load()
    }

    fun setVerificationFilter(filterValue: M16ShelterVerificationFilter) {
        _filter.value = _filter.value.copy(verificationFilter = filterValue)
        load()
    }

    fun setService(service: M16ShelterService?) {
        _filter.value = _filter.value.copy(service = service)
        load()
    }

    fun setSpecies(species: String?) {
        _filter.value = _filter.value.copy(
            species = species?.trim()?.takeIf { it.isNotEmpty() }
        )
        load()
    }

    fun clearFilters() {
        _filter.value = M16ShelterSearchFilter()
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = M16SheltersListUiState.Loading
            repository.searchPublic(_filter.value)
                .onSuccess { list ->
                    _uiState.value = when {
                        list.isEmpty() -> M16SheltersListUiState.Empty
                        else -> M16SheltersListUiState.Content(list)
                    }
                }
                .onFailure {
                    _uiState.value = M16SheltersListUiState.Error(
                        M16ShelterErrorMapper.userMessage(M16ShelterErrorMapper.codeOf(it))
                    )
                }
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M16SheltersListViewModel() as T
        }
    }
}

class M16ShelterDetailViewModel(
    private val shelterId: String,
    private val repository: M16ShelterRepository = DataProvider.m16ShelterRepository
) : ViewModel() {
    private val _shelter = MutableStateFlow<M16PublicShelter?>(null)
    val shelter: StateFlow<M16PublicShelter?> = _shelter.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        if (shelterId.isBlank()) {
            _message.value = M16ShelterErrorMapper.userMessage("M16_SHELTER_NOT_FOUND")
        } else {
            viewModelScope.launch {
                repository.getPublicById(shelterId)
                    .onSuccess { _shelter.value = it }
                    .onFailure {
                        _message.value = M16ShelterErrorMapper.userMessage(
                            M16ShelterErrorMapper.codeOf(it)
                        )
                    }
            }
        }
    }

    companion object {
        fun factory(shelterId: String): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M16ShelterDetailViewModel(shelterId) as T
        }
    }
}

sealed class M16ShelterManageUiState {
    data object Loading : M16ShelterManageUiState()
    data class Error(val message: String) : M16ShelterManageUiState()
    data object PermissionDenied : M16ShelterManageUiState()
    data class NoProfile(
        val organizationId: String,
        val saving: Boolean = false
    ) : M16ShelterManageUiState()

    data class ProfileContent(
        val profile: M16ShelterProfile,
        val saving: Boolean = false
    ) : M16ShelterManageUiState()
}

data class M16ShelterManageDraft(
    val displayName: String = "",
    val description: String = "",
    val publicZoneText: String = "",
    val totalCapacity: String = "0",
    val currentOccupancy: String = "0",
    val services: Set<M16ShelterService> = emptySet(),
    val needsText: String = "",
    val contacts: List<M16PublicContactChannel> = emptyList(),
    val openingHours: M16OpeningHours = M16OpeningHours()
)

class M16ShelterManageViewModel(
    private val repository: M16ShelterRepository = DataProvider.m16ShelterRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<M16ShelterManageUiState>(M16ShelterManageUiState.Loading)
    val uiState: StateFlow<M16ShelterManageUiState> = _uiState.asStateFlow()

    private val _organizationId = MutableStateFlow(M16MockOrganizations.ORG_NORTE)
    val organizationId: StateFlow<String> = _organizationId.asStateFlow()

    private val _draft = MutableStateFlow(M16ShelterManageDraft())
    val draft: StateFlow<M16ShelterManageDraft> = _draft.asStateFlow()

    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()

    private var observeJob: Job? = null

    init {
        refreshOrganization()
    }

    fun selectOrganization(orgId: String) {
        _organizationId.value = orgId
        _feedback.value = null
        _draft.value = M16ShelterManageDraft()
        refreshOrganization()
    }

    fun clearFeedback() {
        _feedback.value = null
    }

    fun updateDraft(transform: (M16ShelterManageDraft) -> M16ShelterManageDraft) {
        _draft.value = transform(_draft.value)
    }

    fun refreshOrganization() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            _uiState.value = M16ShelterManageUiState.Loading
            val orgId = _organizationId.value
            if (!repository.isOrganizationEligible(orgId)) {
                _uiState.value = M16ShelterManageUiState.Error(
                    M16ShelterErrorMapper.userMessage("M16_ORGANIZATION_NOT_ELIGIBLE")
                )
                return@launch
            }
            if (!repository.canManageOrganization(orgId)) {
                _uiState.value = M16ShelterManageUiState.PermissionDenied
                return@launch
            }
            repository.observeProfileByOrganization(orgId).collect { profile ->
                if (profile == null) {
                    _uiState.value = M16ShelterManageUiState.NoProfile(orgId)
                    if (_draft.value.displayName.isBlank()) {
                        _draft.value = M16ShelterManageDraft(
                            displayName = "Refugio demo oeste",
                            publicZoneText = "Zona oeste · GBA",
                            totalCapacity = "15"
                        )
                    }
                } else {
                    _uiState.value = M16ShelterManageUiState.ProfileContent(profile)
                    _draft.value = profile.toDraft()
                }
            }
        }
    }

    fun createProfile() {
        val orgId = _organizationId.value
        val d = _draft.value
        viewModelScope.launch {
            setNoProfileSaving(true)
            repository.createProfile(
                CreateM16ShelterProfileInput(
                    organizationId = orgId,
                    displayName = d.displayName,
                    description = d.description.takeIf { it.isNotBlank() },
                    publicZoneText = d.publicZoneText,
                    totalCapacity = d.totalCapacity.toIntOrNull() ?: 0,
                    services = d.services
                )
            ).onSuccess {
                _feedback.value = "Perfil creado o recuperado (idempotente)."
                setNoProfileSaving(false)
            }.onFailure {
                _feedback.value = M16ShelterErrorMapper.userMessage(M16ShelterErrorMapper.codeOf(it))
                setNoProfileSaving(false)
            }
        }
    }

    fun savePublicData() {
        val state = _uiState.value as? M16ShelterManageUiState.ProfileContent ?: return
        val d = _draft.value
        viewModelScope.launch {
            setProfileSaving(true)
            repository.updatePublicData(
                UpdateM16ShelterPublicInput(
                    shelterId = state.profile.id,
                    displayName = d.displayName,
                    description = d.description.takeIf { it.isNotBlank() },
                    publicZoneText = d.publicZoneText
                )
            ).onSuccess {
                _feedback.value = "Datos públicos guardados."
                setProfileSaving(false)
            }.onFailure {
                _feedback.value = M16ShelterErrorMapper.userMessage(M16ShelterErrorMapper.codeOf(it))
                setProfileSaving(false)
            }
        }
    }

    fun saveCapacity() {
        val state = _uiState.value as? M16ShelterManageUiState.ProfileContent ?: return
        val d = _draft.value
        val total = d.totalCapacity.toIntOrNull()
        val occupancy = d.currentOccupancy.toIntOrNull()
        if (total == null || occupancy == null) {
            _feedback.value = M16ShelterErrorMapper.userMessage("M16_INVALID_CAPACITY")
            return
        }
        viewModelScope.launch {
            setProfileSaving(true)
            repository.updateCapacity(
                state.profile.id,
                M16ShelterCapacity(totalCapacity = total, currentOccupancy = occupancy)
            ).onSuccess {
                _feedback.value = "Capacidad actualizada."
                setProfileSaving(false)
            }.onFailure {
                _feedback.value = M16ShelterErrorMapper.userMessage(M16ShelterErrorMapper.codeOf(it))
                setProfileSaving(false)
            }
        }
    }

    fun saveOpeningHours() {
        val state = _uiState.value as? M16ShelterManageUiState.ProfileContent ?: return
        viewModelScope.launch {
            setProfileSaving(true)
            repository.updateOpeningHours(state.profile.id, _draft.value.openingHours)
                .onSuccess {
                    _feedback.value = "Horarios guardados."
                    setProfileSaving(false)
                }
                .onFailure {
                    _feedback.value = M16ShelterErrorMapper.userMessage(
                        M16ShelterErrorMapper.codeOf(it)
                    )
                    setProfileSaving(false)
                }
        }
    }

    fun saveContacts() {
        val state = _uiState.value as? M16ShelterManageUiState.ProfileContent ?: return
        viewModelScope.launch {
            setProfileSaving(true)
            repository.updatePublicContacts(state.profile.id, _draft.value.contacts)
                .onSuccess {
                    _feedback.value = "Contactos públicos guardados."
                    setProfileSaving(false)
                }
                .onFailure {
                    _feedback.value = M16ShelterErrorMapper.userMessage(
                        M16ShelterErrorMapper.codeOf(it)
                    )
                    setProfileSaving(false)
                }
        }
    }

    fun saveServices() {
        val state = _uiState.value as? M16ShelterManageUiState.ProfileContent ?: return
        viewModelScope.launch {
            setProfileSaving(true)
            repository.updateServices(state.profile.id, _draft.value.services)
                .onSuccess {
                    _feedback.value = "Servicios actualizados."
                    setProfileSaving(false)
                }
                .onFailure {
                    _feedback.value = M16ShelterErrorMapper.userMessage(
                        M16ShelterErrorMapper.codeOf(it)
                    )
                    setProfileSaving(false)
                }
        }
    }

    fun saveNeeds() {
        val state = _uiState.value as? M16ShelterManageUiState.ProfileContent ?: return
        val needs = parseNeeds(_draft.value.needsText)
        viewModelScope.launch {
            setProfileSaving(true)
            repository.updateNeeds(state.profile.id, needs)
                .onSuccess {
                    _feedback.value = "Necesidades actualizadas."
                    setProfileSaving(false)
                }
                .onFailure {
                    _feedback.value = M16ShelterErrorMapper.userMessage(
                        M16ShelterErrorMapper.codeOf(it)
                    )
                    setProfileSaving(false)
                }
        }
    }

    fun publish() = updatePublication(M16ShelterPublicationStatus.PUBLISHED)
    fun unpublish() = updatePublication(M16ShelterPublicationStatus.UNPUBLISHED)
    fun pause() = updateOperational(M16ShelterOperationalStatus.PAUSED)
    fun activate() = updateOperational(M16ShelterOperationalStatus.ACTIVE)

    fun closePermanently() {
        val state = _uiState.value as? M16ShelterManageUiState.ProfileContent ?: return
        viewModelScope.launch {
            setProfileSaving(true)
            repository.updateOperationalStatus(
                state.profile.id,
                M16ShelterOperationalStatus.PERMANENTLY_CLOSED
            ).onSuccess {
                _feedback.value = "Refugio cerrado permanentemente."
                setProfileSaving(false)
            }.onFailure {
                _feedback.value = M16ShelterErrorMapper.userMessage(M16ShelterErrorMapper.codeOf(it))
                setProfileSaving(false)
            }
        }
    }

    fun requestVerification() {
        val state = _uiState.value as? M16ShelterManageUiState.ProfileContent ?: return
        viewModelScope.launch {
            setProfileSaving(true)
            repository.requestVerification(state.profile.id)
                .onSuccess {
                    _feedback.value =
                        "Verificación solicitada (PENDING). La aprobación final depende de M04."
                    setProfileSaving(false)
                }
                .onFailure {
                    _feedback.value = M16ShelterErrorMapper.userMessage(
                        M16ShelterErrorMapper.codeOf(it)
                    )
                    setProfileSaving(false)
                }
        }
    }

    private fun updateOperational(status: M16ShelterOperationalStatus) {
        val state = _uiState.value as? M16ShelterManageUiState.ProfileContent ?: return
        viewModelScope.launch {
            setProfileSaving(true)
            repository.updateOperationalStatus(state.profile.id, status)
                .onSuccess {
                    _feedback.value = "Estado operativo actualizado."
                    setProfileSaving(false)
                }
                .onFailure {
                    _feedback.value = M16ShelterErrorMapper.userMessage(
                        M16ShelterErrorMapper.codeOf(it)
                    )
                    setProfileSaving(false)
                }
        }
    }

    private fun updatePublication(status: M16ShelterPublicationStatus) {
        val state = _uiState.value as? M16ShelterManageUiState.ProfileContent ?: return
        viewModelScope.launch {
            setProfileSaving(true)
            repository.updatePublicationStatus(state.profile.id, status)
                .onSuccess {
                    _feedback.value = "Estado de publicación actualizado."
                    setProfileSaving(false)
                }
                .onFailure {
                    _feedback.value = M16ShelterErrorMapper.userMessage(
                        M16ShelterErrorMapper.codeOf(it)
                    )
                    setProfileSaving(false)
                }
        }
    }

    private fun setNoProfileSaving(saving: Boolean) {
        val current = _uiState.value
        if (current is M16ShelterManageUiState.NoProfile) {
            _uiState.value = current.copy(saving = saving)
        }
    }

    private fun setProfileSaving(saving: Boolean) {
        val current = _uiState.value
        if (current is M16ShelterManageUiState.ProfileContent) {
            _uiState.value = current.copy(saving = saving)
        }
    }

    private fun M16ShelterProfile.toDraft(): M16ShelterManageDraft = M16ShelterManageDraft(
        displayName = displayName,
        description = description.orEmpty(),
        publicZoneText = publicZoneText,
        totalCapacity = capacity.totalCapacity.toString(),
        currentOccupancy = capacity.currentOccupancy.toString(),
        services = services,
        needsText = needs.joinToString("\n") { "${it.category}|${it.description}" },
        contacts = publicContacts,
        openingHours = openingHours
    )

    private fun parseNeeds(raw: String): List<M16ShelterNeed> =
        raw.lines()
            .mapNotNull { line ->
                val parts = line.split("|", limit = 2)
                if (parts.size == 2 && parts[0].isNotBlank()) {
                    M16ShelterNeed(parts[0].trim(), parts[1].trim())
                } else null
            }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M16ShelterManageViewModel() as T
        }
    }
}

fun m16DayLabel(dayOfWeek: Int): String = when (dayOfWeek) {
    1 -> "Lunes"
    2 -> "Martes"
    3 -> "Miércoles"
    4 -> "Jueves"
    5 -> "Viernes"
    6 -> "Sábado"
    7 -> "Domingo"
    else -> "Día $dayOfWeek"
}

fun m16ContactTypeLabel(type: M16PublicContactChannelType): String = when (type) {
    M16PublicContactChannelType.INSTITUTIONAL_EMAIL -> "Email institucional"
    M16PublicContactChannelType.INSTITUTIONAL_PHONE -> "Teléfono institucional"
    M16PublicContactChannelType.WEBSITE -> "Sitio web"
    M16PublicContactChannelType.SOCIAL -> "Red social"
    M16PublicContactChannelType.MESSAGING -> "Mensajería"
}
