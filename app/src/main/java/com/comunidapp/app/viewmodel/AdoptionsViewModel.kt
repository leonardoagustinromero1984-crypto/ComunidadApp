package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.AdoptionPost
import com.comunidapp.app.data.model.AdoptionStatus
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.remote.supabase.m09.M09AdoptionErrorMapper
import com.comunidapp.app.data.repository.AdoptionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdoptionFilters(
    val location: String = "",
    val sex: PetSex? = null,
    val size: PetSize? = null,
    val status: AdoptionStatus? = AdoptionStatus.PUBLISHED
)

sealed class AdoptionListUiState {
    data object Loading : AdoptionListUiState()
    data object Empty : AdoptionListUiState()
    data class Content(val posts: List<AdoptionPost>) : AdoptionListUiState()
    data class Error(val message: String) : AdoptionListUiState()
}

class AdoptionsViewModel(
    private val adoptionRepository: AdoptionRepository = DataProvider.adoptionRepository
) : ViewModel() {

    private val _filters = MutableStateFlow(AdoptionFilters())
    val filters: StateFlow<AdoptionFilters> = _filters.asStateFlow()

    private val _loadError = MutableStateFlow<String?>(null)

    private val published = MutableStateFlow<List<AdoptionPost>>(emptyList())
    private val _loading = MutableStateFlow(true)

    val uiState: StateFlow<AdoptionListUiState> = combine(
        published,
        _filters,
        _loading,
        _loadError
    ) { posts, filters, loading, error ->
        when {
            loading && posts.isEmpty() && error == null -> AdoptionListUiState.Loading
            error != null && posts.isEmpty() -> AdoptionListUiState.Error(error)
            else -> {
                val filtered = posts.filter { post ->
                    post.status == AdoptionStatus.PUBLISHED &&
                        (filters.location.isBlank() ||
                            post.location.contains(filters.location, ignoreCase = true)) &&
                        (filters.sex == null || post.sex == filters.sex) &&
                        (filters.size == null || post.size == filters.size)
                }
                if (filtered.isEmpty()) AdoptionListUiState.Empty
                else AdoptionListUiState.Content(filtered)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AdoptionListUiState.Loading)

    /** Compatibilidad con UI previa. */
    val posts: StateFlow<List<AdoptionPost>> = uiState.map { state ->
        (state as? AdoptionListUiState.Content)?.posts.orEmpty()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _loadError.value = null
            try {
                adoptionRepository.observePublishedAdoptions()
                    .catch { e ->
                        _loadError.value = M09AdoptionErrorMapper.userMessage(
                            M09AdoptionErrorMapper.codeOf(e)
                        )
                        _loading.value = false
                    }
                    .collect { list ->
                        published.value = list.filter { it.status == AdoptionStatus.PUBLISHED }
                        _loading.value = false
                        _loadError.value = null
                    }
            } catch (e: Exception) {
                _loadError.value = M09AdoptionErrorMapper.userMessage(
                    M09AdoptionErrorMapper.codeOf(e)
                )
                _loading.value = false
            }
        }
    }

    fun onLocationChange(location: String) {
        _filters.update { it.copy(location = location) }
    }

    fun onSexFilterChange(sex: PetSex?) {
        _filters.update { it.copy(sex = sex) }
    }

    fun onSizeFilterChange(size: PetSize?) {
        _filters.update { it.copy(size = size) }
    }

    fun onStatusFilterChange(status: AdoptionStatus?) {
        _filters.update { it.copy(status = status) }
    }
}
