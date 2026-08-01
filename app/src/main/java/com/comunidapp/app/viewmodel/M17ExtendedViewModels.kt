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
    private val repository: M17InKindRepository = DataProvider.m17InKindRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<M17InKindListUiState>(M17InKindListUiState.Loading)
    val uiState: StateFlow<M17InKindListUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = M17InKindListUiState.Loading
            repository.searchPublicNeeds(M17InKindSearchFilter())
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
                M17InKindListViewModel() as T
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
    private val repository: M17VolunteerRepository = DataProvider.m17VolunteerRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<M17VolunteerListUiState>(M17VolunteerListUiState.Loading)
    val uiState: StateFlow<M17VolunteerListUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = M17VolunteerListUiState.Loading
            repository.searchPublicOpportunities(M17VolunteerSearchFilter())
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
                M17VolunteerListViewModel() as T
        }
    }
}

class M17HubViewModel : ViewModel()
