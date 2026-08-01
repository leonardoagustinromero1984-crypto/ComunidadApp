package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.CreateM17CampaignInput
import com.comunidapp.app.data.model.M17CampaignFinancialSummary
import com.comunidapp.app.data.model.M17CampaignReference
import com.comunidapp.app.data.model.M17CampaignSearchFilter
import com.comunidapp.app.data.model.M17CampaignStatus
import com.comunidapp.app.data.model.M17CampaignType
import com.comunidapp.app.data.model.M17DonationCampaign
import com.comunidapp.app.data.model.M17MockOrganizations
import com.comunidapp.app.data.model.M17PublicCampaign
import com.comunidapp.app.data.model.M17PublicContribution
import com.comunidapp.app.data.model.RegisterM17MockContributionInput
import com.comunidapp.app.data.model.M17DonorVisibility
import com.comunidapp.app.data.model.UpdateM17CampaignDetailsInput
import com.comunidapp.app.data.model.UpdateM17CampaignGoalInput
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.remote.supabase.m17.M17DonationErrorMapper
import com.comunidapp.app.data.repository.M17DonationRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

sealed class M17CampaignsListUiState {
    data object Loading : M17CampaignsListUiState()
    data object Empty : M17CampaignsListUiState()
    data class Content(val items: List<M17PublicCampaign>) : M17CampaignsListUiState()
    data class Error(val message: String) : M17CampaignsListUiState()
}

class M17CampaignsListViewModel(
    private val repository: M17DonationRepository = DataProvider.m17DonationRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<M17CampaignsListUiState>(M17CampaignsListUiState.Loading)
    val uiState: StateFlow<M17CampaignsListUiState> = _uiState.asStateFlow()
    private val _filter = MutableStateFlow(M17CampaignSearchFilter())
    val filter: StateFlow<M17CampaignSearchFilter> = _filter.asStateFlow()
    private var loadJob: Job? = null

    init { load() }

    fun setQuery(value: String) {
        _filter.value = _filter.value.copy(query = value)
        load()
    }

    fun setType(type: M17CampaignType?) {
        _filter.value = _filter.value.copy(type = type)
        load()
    }

    fun setWithPetOnly(value: Boolean) {
        _filter.value = _filter.value.copy(withPetOnly = value)
        load()
    }

    fun setNearGoalOnly(value: Boolean) {
        _filter.value = _filter.value.copy(nearGoalOnly = value)
        load()
    }

    fun setActiveOnly(value: Boolean) {
        _filter.value = _filter.value.copy(activeOnly = value, completedOnly = !value && _filter.value.completedOnly)
        load()
    }

    fun setCompletedOnly(value: Boolean) {
        _filter.value = _filter.value.copy(completedOnly = value, activeOnly = if (value) false else _filter.value.activeOnly)
        load()
    }

    fun clearFilters() {
        _filter.value = M17CampaignSearchFilter()
        load()
    }

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = M17CampaignsListUiState.Loading
            repository.searchPublicCampaigns(_filter.value)
                .onSuccess { list ->
                    _uiState.value = if (list.isEmpty()) M17CampaignsListUiState.Empty
                    else M17CampaignsListUiState.Content(list)
                }
                .onFailure {
                    _uiState.value = M17CampaignsListUiState.Error(
                        M17DonationErrorMapper.userMessage(M17DonationErrorMapper.codeOf(it))
                    )
                }
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M17CampaignsListViewModel() as T
        }
    }
}

class M17CampaignDetailViewModel(
    private val campaignId: String,
    private val repository: M17DonationRepository = DataProvider.m17DonationRepository
) : ViewModel() {
    private val _campaign = MutableStateFlow<M17PublicCampaign?>(null)
    val campaign: StateFlow<M17PublicCampaign?> = _campaign.asStateFlow()
    private val _contributions = MutableStateFlow<List<M17PublicContribution>>(emptyList())
    val contributions: StateFlow<List<M17PublicContribution>> = _contributions.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            repository.getPublicCampaignById(campaignId)
                .onSuccess { _campaign.value = it }
                .onFailure {
                    _message.value = M17DonationErrorMapper.userMessage(M17DonationErrorMapper.codeOf(it))
                }
            repository.observePublicContributions(campaignId)
                .onSuccess { _contributions.value = it }
            _loading.value = false
        }
    }

    fun registerMockContribution(amountMinor: Long) {
        viewModelScope.launch {
            val currency = _campaign.value?.currency ?: "ARS"
            repository.registerMockContribution(
                RegisterM17MockContributionInput(
                    campaignId = campaignId,
                    amountMinor = amountMinor,
                    currency = currency,
                    visibility = M17DonorVisibility.ANONYMOUS,
                    status = com.comunidapp.app.data.model.M17ContributionStatus.CONFIRMED
                )
            ).onSuccess {
                _message.value = "Contribución de prueba registrada — pagos reales aún no habilitados."
                refresh()
            }.onFailure {
                _message.value = M17DonationErrorMapper.userMessage(M17DonationErrorMapper.codeOf(it))
            }
        }
    }

    fun consumeMessage() { _message.value = null }

    companion object {
        fun factory(campaignId: String): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M17CampaignDetailViewModel(campaignId) as T
        }
    }
}

sealed class M17CampaignManageUiState {
    data object Loading : M17CampaignManageUiState()
    data object PermissionDenied : M17CampaignManageUiState()
    data object NoCampaigns : M17CampaignManageUiState()
    data class Content(
        val organizationId: String,
        val campaigns: List<M17DonationCampaign>,
        val summaryById: Map<String, M17CampaignFinancialSummary>
    ) : M17CampaignManageUiState()
    data class Error(val message: String) : M17CampaignManageUiState()
}

class M17CampaignManageViewModel(
    private val repository: M17DonationRepository = DataProvider.m17DonationRepository
) : ViewModel() {
    private val _selectedOrg = MutableStateFlow(M17MockOrganizations.MANAGE_ORGANIZATION_IDS.first())
    val selectedOrg: StateFlow<String> = _selectedOrg.asStateFlow()
    private val _uiState = MutableStateFlow<M17CampaignManageUiState>(M17CampaignManageUiState.Loading)
    val uiState: StateFlow<M17CampaignManageUiState> = _uiState.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private var observeJob: Job? = null

    init { selectOrganization(_selectedOrg.value) }

    fun selectOrganization(orgId: String) {
        _selectedOrg.value = orgId
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            if (!repository.canManageOrganization(orgId)) {
                _uiState.value = M17CampaignManageUiState.PermissionDenied
                return@launch
            }
            repository.observeCampaignsForOrganization(orgId).collect { campaigns ->
                val summaries = campaigns.associate { c ->
                    c.id to (repository.observeFinancialSummary(c.id).getOrNull()
                        ?: com.comunidapp.app.data.model.M17CampaignFinancialSummary(
                            0, c.goal.currency, c.goal.amountMinor, 0, 0, 0
                        ))
                }
                _uiState.value = when {
                    campaigns.isEmpty() -> M17CampaignManageUiState.NoCampaigns
                    else -> M17CampaignManageUiState.Content(orgId, campaigns, summaries)
                }
            }
        }
    }

    fun publish(campaignId: String) = mutate { repository.publishCampaign(campaignId) }
    fun pause(campaignId: String) = mutate { repository.pauseCampaign(campaignId) }
    fun complete(campaignId: String) = mutate { repository.completeCampaign(campaignId) }
    fun cancel(campaignId: String) = mutate { repository.cancelCampaign(campaignId) }

    private fun mutate(block: suspend () -> Result<M17DonationCampaign>) {
        viewModelScope.launch {
            block().onFailure {
                _message.value = M17DonationErrorMapper.userMessage(M17DonationErrorMapper.codeOf(it))
            }
        }
    }

    fun consumeMessage() { _message.value = null }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M17CampaignManageViewModel() as T
        }
    }
}

data class M17CampaignEditDraft(
    val organizationId: String = M17MockOrganizations.ORG_NORTE,
    val title: String = "",
    val description: String = "",
    val campaignType: M17CampaignType = M17CampaignType.GENERAL_SUPPORT,
    val goalAmountMinor: Long = 10_000_00,
    val currency: String = "ARS",
    val petPublicName: String = "",
    val shelterPublicName: String = "",
    val publicLocationText: String = ""
)

sealed class M17CampaignEditUiState {
    data object DraftEditing : M17CampaignEditUiState()
    data object Saving : M17CampaignEditUiState()
    data class Error(val message: String) : M17CampaignEditUiState()
    data class Saved(val campaignId: String) : M17CampaignEditUiState()
}

class M17CampaignEditViewModel(
    private val existingCampaignId: String?,
    private val repository: M17DonationRepository = DataProvider.m17DonationRepository
) : ViewModel() {
    private val _draft = MutableStateFlow(M17CampaignEditDraft())
    val draft: StateFlow<M17CampaignEditDraft> = _draft.asStateFlow()
    private val _uiState = MutableStateFlow<M17CampaignEditUiState>(M17CampaignEditUiState.DraftEditing)
    val uiState: StateFlow<M17CampaignEditUiState> = _uiState.asStateFlow()

    init {
        existingCampaignId?.let { id ->
            viewModelScope.launch {
                repository.refreshCampaign(id).onSuccess { c ->
                    _draft.value = M17CampaignEditDraft(
                        organizationId = c.organizationId,
                        title = c.title,
                        description = c.description,
                        campaignType = c.campaignType,
                        goalAmountMinor = c.goal.amountMinor,
                        currency = c.goal.currency,
                        petPublicName = c.reference.petPublicName.orEmpty(),
                        shelterPublicName = c.reference.shelterPublicName.orEmpty(),
                        publicLocationText = c.reference.publicLocationText.orEmpty()
                    )
                }
            }
        }
    }

    fun updateDraft(transform: (M17CampaignEditDraft) -> M17CampaignEditDraft) {
        _draft.value = transform(_draft.value)
    }

    fun save() {
        viewModelScope.launch {
            _uiState.value = M17CampaignEditUiState.Saving
            val d = _draft.value
            val ref = M17CampaignReference(
                petPublicName = d.petPublicName.takeIf { it.isNotBlank() },
                shelterPublicName = d.shelterPublicName.takeIf { it.isNotBlank() },
                publicLocationText = d.publicLocationText.takeIf { it.isNotBlank() }
            )
            val result = if (existingCampaignId == null) {
                repository.createCampaign(
                    CreateM17CampaignInput(
                        organizationId = d.organizationId,
                        title = d.title,
                        description = d.description,
                        campaignType = d.campaignType,
                        goalAmountMinor = d.goalAmountMinor,
                        currency = d.currency,
                        reference = ref
                    )
                )
            } else {
                repository.updateCampaignDetails(
                    UpdateM17CampaignDetailsInput(
                        campaignId = existingCampaignId,
                        title = d.title,
                        description = d.description,
                        campaignType = d.campaignType,
                        reference = ref
                    )
                ).fold(
                    onSuccess = { updated ->
                        repository.updateCampaignGoal(
                            UpdateM17CampaignGoalInput(existingCampaignId, d.goalAmountMinor, d.currency)
                        ).map { updated }
                    },
                    onFailure = { Result.failure(it) }
                )
            }
            result.fold(
                onSuccess = { _uiState.value = M17CampaignEditUiState.Saved(it.id) },
                onFailure = {
                    _uiState.value = M17CampaignEditUiState.Error(
                        M17DonationErrorMapper.userMessage(M17DonationErrorMapper.codeOf(it))
                    )
                }
            )
        }
    }

    companion object {
        fun factory(campaignId: String?): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M17CampaignEditViewModel(campaignId) as T
        }
    }
}

fun m17CampaignTypeLabel(type: M17CampaignType): String = when (type) {
    M17CampaignType.MEDICAL -> "Médica"
    M17CampaignType.FOOD_AND_SUPPLIES -> "Alimentos e insumos"
    M17CampaignType.RESCUE -> "Rescate"
    M17CampaignType.SHELTER_INFRASTRUCTURE -> "Infraestructura"
    M17CampaignType.TRANSPORT -> "Traslado"
    M17CampaignType.EMERGENCY -> "Emergencia"
    M17CampaignType.GENERAL_SUPPORT -> "Apoyo general"
}

fun m17CampaignStatusLabel(status: M17CampaignStatus): String = when (status) {
    M17CampaignStatus.DRAFT -> "Borrador"
    M17CampaignStatus.PUBLISHED -> "Publicada"
    M17CampaignStatus.PAUSED -> "Pausada"
    M17CampaignStatus.COMPLETED -> "Completada"
    M17CampaignStatus.CANCELLED -> "Cancelada"
}
