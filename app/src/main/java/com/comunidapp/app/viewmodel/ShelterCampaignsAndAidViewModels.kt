package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.ShelterCampaign
import com.comunidapp.app.data.model.ShelterCampaignCategory
import com.comunidapp.app.data.model.ShelterCampaignPublicListing
import com.comunidapp.app.data.model.ShelterCampaignStatus
import com.comunidapp.app.data.model.ShelterCampaignUpdate
import com.comunidapp.app.data.model.ShelterCampaignVisibility
import com.comunidapp.app.data.model.ShelterSupplyCategory
import com.comunidapp.app.data.model.ShelterSupplyContribution
import com.comunidapp.app.data.model.ShelterSupplyPriority
import com.comunidapp.app.data.model.ShelterSupplyRequest
import com.comunidapp.app.data.model.ShelterSupplyRequestPublicListing
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.remote.supabase.m11.M11ShelterErrorMapper
import com.comunidapp.app.data.repository.AddShelterCampaignUpdateInput
import com.comunidapp.app.data.repository.CreateShelterCampaignInput
import com.comunidapp.app.data.repository.CreateSupplyRequestInput
import com.comunidapp.app.data.repository.PledgeSupplyContributionInput
import com.comunidapp.app.data.repository.ShelterCampaignRepository
import com.comunidapp.app.data.repository.ShelterSupplyRepository
import com.comunidapp.app.data.repository.UpdateShelterCampaignInput
import com.comunidapp.app.data.repository.UpdateSupplyRequestInput
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class ShelterPublicCampaignsUiState {
    data object Loading : ShelterPublicCampaignsUiState()
    data object Empty : ShelterPublicCampaignsUiState()
    data class Content(val items: List<ShelterCampaignPublicListing>) : ShelterPublicCampaignsUiState()
    data class Error(val message: String) : ShelterPublicCampaignsUiState()
}

class ShelterPublicCampaignsViewModel(
    private val repo: ShelterCampaignRepository = DataProvider.shelterCampaignRepository
) : ViewModel() {
    private val _ui = MutableStateFlow<ShelterPublicCampaignsUiState>(ShelterPublicCampaignsUiState.Loading)
    val uiState: StateFlow<ShelterPublicCampaignsUiState> = _ui.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _ui.value = ShelterPublicCampaignsUiState.Loading
            repo.observePublicCampaigns().collect { list ->
                _ui.value = when {
                    list.isEmpty() -> ShelterPublicCampaignsUiState.Empty
                    else -> ShelterPublicCampaignsUiState.Content(list)
                }
            }
        }
    }

    companion object {
        fun factory() = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ShelterPublicCampaignsViewModel() as T
        }
    }
}

class ShelterCampaignsViewModel(
    private val shelterId: String,
    private val repo: ShelterCampaignRepository = DataProvider.shelterCampaignRepository
) : ViewModel() {
    val campaigns: StateFlow<List<ShelterCampaign>> =
        repo.observeShelterCampaigns(shelterId)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun campaignShelterId(campaign: ShelterCampaign): String = campaign.shelterProfileId

    companion object {
        fun factory(shelterId: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ShelterCampaignsViewModel(shelterId) as T
        }
    }
}

data class ShelterCampaignDetailData(
    val campaign: ShelterCampaign,
    val updates: List<ShelterCampaignUpdate>
)

sealed class ShelterCampaignDetailUiState {
    data object Loading : ShelterCampaignDetailUiState()
    data class Content(val data: ShelterCampaignDetailData) : ShelterCampaignDetailUiState()
    data class Error(val message: String) : ShelterCampaignDetailUiState()
}

class ShelterCampaignDetailViewModel(
    private val campaignId: String,
    private val repo: ShelterCampaignRepository = DataProvider.shelterCampaignRepository
) : ViewModel() {
    private val _ui = MutableStateFlow<ShelterCampaignDetailUiState>(ShelterCampaignDetailUiState.Loading)
    val uiState = _ui.asStateFlow()
    private val _campaign = MutableStateFlow<ShelterCampaign?>(null)
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    init {
        if (campaignId.isBlank()) {
            _ui.value = ShelterCampaignDetailUiState.Error(
                M11ShelterErrorMapper.userMessage("SHELTER_CAMPAIGN_NOT_FOUND")
            )
        } else {
            viewModelScope.launch {
                repo.getCampaign(campaignId).onSuccess { _campaign.value = it }
                    .onFailure {
                        _ui.value = ShelterCampaignDetailUiState.Error(
                            M11ShelterErrorMapper.userMessage(M11ShelterErrorMapper.codeOf(it))
                        )
                        return@launch
                    }
                combine(
                    repo.observeCampaignUpdates(campaignId),
                    _campaign
                ) { updates, c ->
                    c?.let { ShelterCampaignDetailData(campaign = it, updates = updates) }
                }.collect { data ->
                    if (data != null) _ui.value = ShelterCampaignDetailUiState.Content(data)
                }
            }
        }
    }

    fun changeStatus(status: ShelterCampaignStatus) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            repo.changeCampaignStatus(campaignId, status)
                .onSuccess { _campaign.value = it }
                .onFailure {
                    _error.value = M11ShelterErrorMapper.userMessage(M11ShelterErrorMapper.codeOf(it))
                }
            _busy.value = false
        }
    }

    companion object {
        fun factory(campaignId: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ShelterCampaignDetailViewModel(campaignId) as T
        }
    }
}

class ShelterCampaignFormViewModel(
    private val shelterId: String,
    private val campaignId: String? = null,
    private val repo: ShelterCampaignRepository = DataProvider.shelterCampaignRepository
) : ViewModel() {
    private val _submitting = MutableStateFlow(false)
    val submitting = _submitting.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _saved = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val saved = _saved.asSharedFlow()
    private val _existing = MutableStateFlow<ShelterCampaign?>(null)
    val existing = _existing.asStateFlow()

    init {
        if (!campaignId.isNullOrBlank()) {
            viewModelScope.launch {
                repo.getCampaign(campaignId).onSuccess { _existing.value = it }
                    .onFailure {
                        _error.value = M11ShelterErrorMapper.userMessage(M11ShelterErrorMapper.codeOf(it))
                    }
            }
        }
    }

    fun create(
        title: String,
        description: String,
        category: ShelterCampaignCategory,
        visibility: ShelterCampaignVisibility,
        activate: Boolean
    ) {
        if (_submitting.value) return
        viewModelScope.launch {
            _submitting.value = true
            _error.value = null
            repo.createCampaign(
                CreateShelterCampaignInput(
                    shelterProfileId = shelterId,
                    title = title,
                    description = description,
                    category = category,
                    visibility = visibility,
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
        category: ShelterCampaignCategory,
        visibility: ShelterCampaignVisibility
    ) {
        val id = campaignId ?: return
        if (_submitting.value) return
        viewModelScope.launch {
            _submitting.value = true
            _error.value = null
            repo.updateCampaign(
                UpdateShelterCampaignInput(
                    campaignId = id,
                    title = title,
                    description = description,
                    category = category,
                    visibility = visibility
                )
            ).onSuccess { _saved.tryEmit(it.id) }
                .onFailure {
                    _error.value = M11ShelterErrorMapper.userMessage(M11ShelterErrorMapper.codeOf(it))
                }
            _submitting.value = false
        }
    }

    companion object {
        fun factory(shelterId: String, campaignId: String? = null) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ShelterCampaignFormViewModel(shelterId, campaignId) as T
        }
    }
}

class ShelterCampaignUpdateFormViewModel(
    private val campaignId: String,
    private val repo: ShelterCampaignRepository = DataProvider.shelterCampaignRepository
) : ViewModel() {
    private val _submitting = MutableStateFlow(false)
    val submitting = _submitting.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _saved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saved = _saved.asSharedFlow()

    fun addUpdate(visibility: ShelterCampaignVisibility, message: String) {
        if (_submitting.value) return
        viewModelScope.launch {
            _submitting.value = true
            _error.value = null
            repo.addCampaignUpdate(
                AddShelterCampaignUpdateInput(
                    campaignId = campaignId,
                    visibility = visibility,
                    message = message
                )
            ).onSuccess { _saved.tryEmit(Unit) }
                .onFailure {
                    _error.value = M11ShelterErrorMapper.userMessage(M11ShelterErrorMapper.codeOf(it))
                }
            _submitting.value = false
        }
    }

    companion object {
        fun factory(campaignId: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ShelterCampaignUpdateFormViewModel(campaignId) as T
        }
    }
}

sealed class ShelterPublicSupplyRequestsUiState {
    data object Loading : ShelterPublicSupplyRequestsUiState()
    data object Empty : ShelterPublicSupplyRequestsUiState()
    data class Content(val items: List<ShelterSupplyRequestPublicListing>) : ShelterPublicSupplyRequestsUiState()
    data class Error(val message: String) : ShelterPublicSupplyRequestsUiState()
}

class ShelterPublicSupplyRequestsViewModel(
    private val repo: ShelterSupplyRepository = DataProvider.shelterSupplyRepository
) : ViewModel() {
    private val _ui = MutableStateFlow<ShelterPublicSupplyRequestsUiState>(ShelterPublicSupplyRequestsUiState.Loading)
    val uiState: StateFlow<ShelterPublicSupplyRequestsUiState> = _ui.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _ui.value = ShelterPublicSupplyRequestsUiState.Loading
            repo.observePublicSupplyRequests().collect { list ->
                _ui.value = when {
                    list.isEmpty() -> ShelterPublicSupplyRequestsUiState.Empty
                    else -> ShelterPublicSupplyRequestsUiState.Content(list)
                }
            }
        }
    }

    companion object {
        fun factory() = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ShelterPublicSupplyRequestsViewModel() as T
        }
    }
}

class ShelterSupplyRequestsViewModel(
    private val shelterId: String,
    private val repo: ShelterSupplyRepository = DataProvider.shelterSupplyRepository
) : ViewModel() {
    val requests: StateFlow<List<ShelterSupplyRequest>> =
        repo.observeShelterSupplyRequests(shelterId)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    companion object {
        fun factory(shelterId: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ShelterSupplyRequestsViewModel(shelterId) as T
        }
    }
}

data class ShelterSupplyRequestDetailData(
    val request: ShelterSupplyRequest,
    val contributions: List<ShelterSupplyContribution>
)

sealed class ShelterSupplyRequestDetailUiState {
    data object Loading : ShelterSupplyRequestDetailUiState()
    data class Content(val data: ShelterSupplyRequestDetailData) : ShelterSupplyRequestDetailUiState()
    data class Error(val message: String) : ShelterSupplyRequestDetailUiState()
}

class ShelterSupplyRequestDetailViewModel(
    private val requestId: String,
    private val repo: ShelterSupplyRepository = DataProvider.shelterSupplyRepository
) : ViewModel() {
    private val _ui = MutableStateFlow<ShelterSupplyRequestDetailUiState>(ShelterSupplyRequestDetailUiState.Loading)
    val uiState = _ui.asStateFlow()
    private val _request = MutableStateFlow<ShelterSupplyRequest?>(null)
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _cancelled = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val cancelled = _cancelled.asSharedFlow()

    init {
        if (requestId.isBlank()) {
            _ui.value = ShelterSupplyRequestDetailUiState.Error(
                M11ShelterErrorMapper.userMessage("SHELTER_SUPPLY_REQUEST_NOT_FOUND")
            )
        } else {
            viewModelScope.launch {
                repo.getSupplyRequest(requestId).onSuccess { _request.value = it }
                    .onFailure {
                        _ui.value = ShelterSupplyRequestDetailUiState.Error(
                            M11ShelterErrorMapper.userMessage(M11ShelterErrorMapper.codeOf(it))
                        )
                        return@launch
                    }
                combine(
                    repo.observeContributions(requestId),
                    _request
                ) { contribs, req ->
                    req?.let { ShelterSupplyRequestDetailData(request = it, contributions = contribs) }
                }.collect { data ->
                    if (data != null) _ui.value = ShelterSupplyRequestDetailUiState.Content(data)
                }
            }
        }
    }

    private suspend fun refreshRequest() {
        repo.getSupplyRequest(requestId).onSuccess { _request.value = it }
    }

    fun cancel() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            repo.cancelSupplyRequest(requestId)
                .onSuccess {
                    _request.value = it
                    _cancelled.tryEmit(Unit)
                }
                .onFailure {
                    _error.value = M11ShelterErrorMapper.userMessage(M11ShelterErrorMapper.codeOf(it))
                }
            _busy.value = false
        }
    }

    fun confirmContribution(contributionId: String) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            repo.confirmContribution(contributionId)
                .onFailure {
                    _error.value = M11ShelterErrorMapper.userMessage(M11ShelterErrorMapper.codeOf(it))
                }
            refreshRequest()
            _busy.value = false
        }
    }

    fun recordReceipt(contributionId: String, quantityReceived: Int) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            repo.recordReceipt(contributionId, quantityReceived)
                .onFailure {
                    _error.value = M11ShelterErrorMapper.userMessage(M11ShelterErrorMapper.codeOf(it))
                }
            refreshRequest()
            _busy.value = false
        }
    }

    companion object {
        fun factory(requestId: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ShelterSupplyRequestDetailViewModel(requestId) as T
        }
    }
}

class ShelterSupplyRequestFormViewModel(
    private val shelterId: String,
    private val requestId: String? = null,
    private val repo: ShelterSupplyRepository = DataProvider.shelterSupplyRepository
) : ViewModel() {
    private val _submitting = MutableStateFlow(false)
    val submitting = _submitting.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _saved = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val saved = _saved.asSharedFlow()
    private val _existing = MutableStateFlow<ShelterSupplyRequest?>(null)
    val existing = _existing.asStateFlow()

    init {
        if (!requestId.isNullOrBlank()) {
            viewModelScope.launch {
                repo.getSupplyRequest(requestId).onSuccess { _existing.value = it }
                    .onFailure {
                        _error.value = M11ShelterErrorMapper.userMessage(M11ShelterErrorMapper.codeOf(it))
                    }
            }
        }
    }

    fun create(
        category: ShelterSupplyCategory,
        itemName: String,
        description: String?,
        quantityRequested: Int,
        unitText: String,
        priority: ShelterSupplyPriority,
        publicNotes: String?,
        internalNotes: String?,
        publishOpen: Boolean
    ) {
        if (_submitting.value) return
        viewModelScope.launch {
            _submitting.value = true
            _error.value = null
            repo.createSupplyRequest(
                CreateSupplyRequestInput(
                    shelterProfileId = shelterId,
                    category = category,
                    itemName = itemName,
                    description = description,
                    quantityRequested = quantityRequested,
                    unitText = unitText,
                    priority = priority,
                    publicNotes = publicNotes,
                    internalNotes = internalNotes,
                    publishOpen = publishOpen
                )
            ).onSuccess { _saved.tryEmit(it.id) }
                .onFailure {
                    _error.value = M11ShelterErrorMapper.userMessage(M11ShelterErrorMapper.codeOf(it))
                }
            _submitting.value = false
        }
    }

    fun update(
        category: ShelterSupplyCategory,
        itemName: String,
        description: String?,
        quantityRequested: Int,
        unitText: String,
        priority: ShelterSupplyPriority,
        publicNotes: String?,
        internalNotes: String?
    ) {
        val id = requestId ?: return
        if (_submitting.value) return
        viewModelScope.launch {
            _submitting.value = true
            _error.value = null
            repo.updateSupplyRequest(
                UpdateSupplyRequestInput(
                    requestId = id,
                    category = category,
                    itemName = itemName,
                    description = description,
                    quantityRequested = quantityRequested,
                    unitText = unitText,
                    priority = priority,
                    publicNotes = publicNotes,
                    internalNotes = internalNotes
                )
            ).onSuccess { _saved.tryEmit(it.id) }
                .onFailure {
                    _error.value = M11ShelterErrorMapper.userMessage(M11ShelterErrorMapper.codeOf(it))
                }
            _submitting.value = false
        }
    }

    companion object {
        fun factory(shelterId: String, requestId: String? = null) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ShelterSupplyRequestFormViewModel(shelterId, requestId) as T
        }
    }
}

class ShelterSupplyContributeViewModel(
    private val requestId: String,
    private val repo: ShelterSupplyRepository = DataProvider.shelterSupplyRepository
) : ViewModel() {
    private val _submitting = MutableStateFlow(false)
    val submitting = _submitting.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _saved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saved = _saved.asSharedFlow()

    fun pledge(quantityCommitted: Int, contributorNotes: String?) {
        if (_submitting.value) return
        viewModelScope.launch {
            _submitting.value = true
            _error.value = null
            repo.pledgeContribution(
                PledgeSupplyContributionInput(
                    requestId = requestId,
                    quantityCommitted = quantityCommitted,
                    contributorNotes = contributorNotes
                )
            ).onSuccess { _saved.tryEmit(Unit) }
                .onFailure {
                    _error.value = M11ShelterErrorMapper.userMessage(M11ShelterErrorMapper.codeOf(it))
                }
            _submitting.value = false
        }
    }

    companion object {
        fun factory(requestId: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ShelterSupplyContributeViewModel(requestId) as T
        }
    }
}

class ShelterSupplyContributionsViewModel(
    private val requestId: String,
    private val repo: ShelterSupplyRepository = DataProvider.shelterSupplyRepository
) : ViewModel() {
    val contributions: StateFlow<List<ShelterSupplyContribution>> =
        repo.observeContributions(requestId)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun confirm(contributionId: String) = mutate { repo.confirmContribution(contributionId) }

    fun recordReceipt(contributionId: String, quantityReceived: Int) =
        mutate { repo.recordReceipt(contributionId, quantityReceived) }

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
        fun factory(requestId: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ShelterSupplyContributionsViewModel(requestId) as T
        }
    }
}
