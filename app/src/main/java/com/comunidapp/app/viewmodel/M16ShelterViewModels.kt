package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.M16PublicShelter
import com.comunidapp.app.data.model.M16ShelterOperationalStatus
import com.comunidapp.app.data.model.M16ShelterProfile
import com.comunidapp.app.data.model.M16ShelterPublicationStatus
import com.comunidapp.app.data.model.M16ShelterSearchFilter
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.remote.supabase.m16.M16ShelterErrorMapper
import com.comunidapp.app.data.repository.M16ShelterRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val _query = MutableStateFlow("")

    init {
        load()
    }

    fun setQuery(value: String) {
        _query.value = value
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = M16SheltersListUiState.Loading
            repository.searchPublic(M16ShelterSearchFilter(query = _query.value))
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
        viewModelScope.launch {
            repository.getPublicById(shelterId)
                .onSuccess { _shelter.value = it }
                .onFailure {
                    _message.value = M16ShelterErrorMapper.userMessage(M16ShelterErrorMapper.codeOf(it))
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

class M16ShelterManageViewModel(
    private val repository: M16ShelterRepository = DataProvider.m16ShelterRepository
) : ViewModel() {
    private val _profile = MutableStateFlow<M16ShelterProfile?>(null)
    val profile: StateFlow<M16ShelterProfile?> = _profile.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeProfileByOrganization("org_refugio_norte").collect { p ->
                _profile.value = p
            }
        }
    }

    fun pause() = updateOperational(M16ShelterOperationalStatus.PAUSED)
    fun activate() = updateOperational(M16ShelterOperationalStatus.ACTIVE)
    fun publish() = updatePublication(M16ShelterPublicationStatus.PUBLISHED)
    fun unpublish() = updatePublication(M16ShelterPublicationStatus.UNPUBLISHED)

    fun requestVerification() {
        val id = _profile.value?.id ?: return
        viewModelScope.launch {
            repository.requestVerification(id)
                .onSuccess {
                    _profile.value = it
                    _message.value = "Verificación solicitada."
                }
                .onFailure {
                    _message.value = M16ShelterErrorMapper.userMessage(M16ShelterErrorMapper.codeOf(it))
                }
        }
    }

    private fun updateOperational(status: M16ShelterOperationalStatus) {
        val id = _profile.value?.id ?: return
        viewModelScope.launch {
            repository.updateOperationalStatus(id, status)
                .onSuccess { _profile.value = it }
                .onFailure {
                    _message.value = M16ShelterErrorMapper.userMessage(M16ShelterErrorMapper.codeOf(it))
                }
        }
    }

    private fun updatePublication(status: M16ShelterPublicationStatus) {
        val id = _profile.value?.id ?: return
        viewModelScope.launch {
            repository.updatePublicationStatus(id, status)
                .onSuccess { _profile.value = it }
                .onFailure {
                    _message.value = M16ShelterErrorMapper.userMessage(M16ShelterErrorMapper.codeOf(it))
                }
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M16ShelterManageViewModel() as T
        }
    }
}
