package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.M17InKindSearchFilter
import com.comunidapp.app.data.model.M17PublicInKindNeed
import com.comunidapp.app.data.model.M17PublicVolunteerOpportunity
import com.comunidapp.app.data.model.M17VolunteerSearchFilter
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.remote.supabase.m17.M17DonationErrorMapper
import com.comunidapp.app.data.repository.M17InKindRepository
import com.comunidapp.app.data.repository.M17TransparencyRepository
import com.comunidapp.app.data.repository.M17VolunteerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class M17InKindListUiState {
    data object Loading : M17InKindListUiState()
    data object Empty : M17InKindListUiState()
    data class Content(val items: List<M17PublicInKindNeed>) : M17InKindListUiState()
    data class Error(val message: String) : M17InKindListUiState()
}

class M17InKindListViewModel(
    private val repository: M17InKindRepository = DataProvider.m17InKindRepository,
    private val organizationId: String? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow<M17InKindListUiState>(M17InKindListUiState.Loading)
    val uiState: StateFlow<M17InKindListUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = M17InKindListUiState.Loading
            repository.searchPublicNeeds(M17InKindSearchFilter(organizationId = organizationId))
                .onSuccess { list ->
                    _uiState.value = if (list.isEmpty()) M17InKindListUiState.Empty
                    else M17InKindListUiState.Content(list)
                }
                .onFailure {
                    _uiState.value = M17InKindListUiState.Error(
                        M17DonationErrorMapper.userMessage(M17DonationErrorMapper.codeOf(it))
                    )
                }
        }
    }

    companion object {
        fun factory() = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M17InKindListViewModel(
                    organizationId = com.comunidapp.app.domain.organization.OrganizationListContext.organizationId
                ) as T
        }
    }
}

sealed class M17VolunteerListUiState {
    data object Loading : M17VolunteerListUiState()
    data object Empty : M17VolunteerListUiState()
    data class Content(val items: List<M17PublicVolunteerOpportunity>) : M17VolunteerListUiState()
    data class Error(val message: String) : M17VolunteerListUiState()
}

class M17VolunteerListViewModel(
    private val repository: M17VolunteerRepository = DataProvider.m17VolunteerRepository,
    private val organizationId: String? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow<M17VolunteerListUiState>(M17VolunteerListUiState.Loading)
    val uiState: StateFlow<M17VolunteerListUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = M17VolunteerListUiState.Loading
            repository.searchPublicOpportunities(
                M17VolunteerSearchFilter(organizationId = organizationId)
            )
                .onSuccess { list ->
                    _uiState.value = if (list.isEmpty()) M17VolunteerListUiState.Empty
                    else M17VolunteerListUiState.Content(list)
                }
                .onFailure {
                    _uiState.value = M17VolunteerListUiState.Error(
                        M17DonationErrorMapper.userMessage(M17DonationErrorMapper.codeOf(it))
                    )
                }
        }
    }

    companion object {
        fun factory() = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M17VolunteerListViewModel(
                    organizationId = com.comunidapp.app.domain.organization.OrganizationListContext.organizationId
                ) as T
        }
    }
}

class M17HubViewModel : ViewModel()

sealed class M17GoodsDetailUiState {
    data object Loading : M17GoodsDetailUiState()
    data class Content(val need: M17PublicInKindNeed) : M17GoodsDetailUiState()
    data class Error(val message: String) : M17GoodsDetailUiState()
}

class M17GoodsDetailViewModel(
    private val needId: String,
    private val repository: M17InKindRepository = DataProvider.m17InKindRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<M17GoodsDetailUiState>(M17GoodsDetailUiState.Loading)
    val uiState: StateFlow<M17GoodsDetailUiState> = _uiState.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            repository.getPublicNeed(needId)
                .onSuccess { _uiState.value = M17GoodsDetailUiState.Content(it) }
                .onFailure {
                    _uiState.value = M17GoodsDetailUiState.Error(
                        M17DonationErrorMapper.userMessage(M17DonationErrorMapper.codeOf(it))
                    )
                }
        }
    }

    fun offer(quantityRaw: String, note: String?) {
        val quantity = quantityRaw.trim().toIntOrNull()
        if (quantity == null || quantity <= 0) {
            _message.value = "Ingresá una cantidad válida."
            return
        }
        viewModelScope.launch {
            _submitting.value = true
            repository.createPledge(needId, quantity, note?.trim()?.takeIf { it.isNotEmpty() })
                .onSuccess {
                    _message.value = com.comunidapp.app.domain.m17.CommunityHelpPresentation.PLEDGE_SAVED
                    load()
                }
                .onFailure {
                    _message.value = M17DonationErrorMapper.userMessage(M17DonationErrorMapper.codeOf(it))
                }
            _submitting.value = false
        }
    }

    companion object {
        fun factory(needId: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M17GoodsDetailViewModel(needId) as T
        }
    }
}

sealed class M17VolunteerDetailUiState {
    data object Loading : M17VolunteerDetailUiState()
    data class Content(val opportunity: M17PublicVolunteerOpportunity) : M17VolunteerDetailUiState()
    data class Error(val message: String) : M17VolunteerDetailUiState()
}

class M17VolunteerDetailViewModel(
    private val opportunityId: String,
    private val repository: M17VolunteerRepository = DataProvider.m17VolunteerRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<M17VolunteerDetailUiState>(M17VolunteerDetailUiState.Loading)
    val uiState: StateFlow<M17VolunteerDetailUiState> = _uiState.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            repository.getPublicOpportunity(opportunityId)
                .onSuccess { _uiState.value = M17VolunteerDetailUiState.Content(it) }
                .onFailure {
                    _uiState.value = M17VolunteerDetailUiState.Error(
                        M17DonationErrorMapper.userMessage(M17DonationErrorMapper.codeOf(it))
                    )
                }
        }
    }

    fun offer(note: String?) {
        viewModelScope.launch {
            _submitting.value = true
            repository.submitApplication(opportunityId, note?.trim()?.takeIf { it.isNotEmpty() })
                .onSuccess {
                    _message.value = com.comunidapp.app.domain.m17.CommunityHelpPresentation.INTEREST_SAVED
                    load()
                }
                .onFailure {
                    _message.value = M17DonationErrorMapper.userMessage(M17DonationErrorMapper.codeOf(it))
                }
            _submitting.value = false
        }
    }

    companion object {
        fun factory(opportunityId: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M17VolunteerDetailViewModel(opportunityId) as T
        }
    }
}

data class M17MyHelpUiState(
    val loading: Boolean = true,
    val money: List<com.comunidapp.app.domain.m17.MyMoneyContribution> = emptyList(),
    val goods: List<com.comunidapp.app.domain.m17.MyGoodsPledge> = emptyList(),
    val time: List<com.comunidapp.app.domain.m17.MyVolunteerInterest> = emptyList(),
    val moneyError: String? = null,
    val goodsError: String? = null,
    val timeError: String? = null
)

class M17MyHelpViewModel(
    private val donations: com.comunidapp.app.data.repository.M17DonationRepository = DataProvider.m17DonationRepository,
    private val goods: M17InKindRepository = DataProvider.m17InKindRepository,
    private val volunteering: M17VolunteerRepository = DataProvider.m17VolunteerRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(M17MyHelpUiState())
    val uiState: StateFlow<M17MyHelpUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true)
            val money = donations.listMyContributions()
            val pledged = goods.listMyPledges()
            val interests = volunteering.listMyApplications()
            _uiState.value = M17MyHelpUiState(
                loading = false,
                money = money.getOrDefault(emptyList()),
                goods = pledged.getOrDefault(emptyList()),
                time = interests.getOrDefault(emptyList()),
                moneyError = money.exceptionOrNull()?.let {
                    M17DonationErrorMapper.userMessage(M17DonationErrorMapper.codeOf(it))
                },
                goodsError = pledged.exceptionOrNull()?.let {
                    M17DonationErrorMapper.userMessage(M17DonationErrorMapper.codeOf(it))
                },
                timeError = interests.exceptionOrNull()?.let {
                    M17DonationErrorMapper.userMessage(M17DonationErrorMapper.codeOf(it))
                }
            )
        }
    }

    companion object {
        fun factory() = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M17MyHelpViewModel() as T
        }
    }
}
