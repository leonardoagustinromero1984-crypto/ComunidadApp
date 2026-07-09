package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.LostFoundPost
import com.comunidapp.app.data.model.LostFoundStatus
import com.comunidapp.app.data.model.LostFoundType
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.LostFoundRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class LostFoundFilters(
    val type: LostFoundType? = null,
    val species: PetSpecies? = null,
    val location: String = "",
    val status: LostFoundStatus? = LostFoundStatus.ACTIVE
)

class LostFoundViewModel(
    private val lostFoundRepository: LostFoundRepository = DataProvider.lostFoundRepository
) : ViewModel() {

    private val _filters = MutableStateFlow(LostFoundFilters())
    val filters: StateFlow<LostFoundFilters> = _filters.asStateFlow()

    val posts: StateFlow<List<LostFoundPost>> = combine(
        lostFoundRepository.observeLostFoundPosts(),
        _filters
    ) { allPosts, filters ->
        allPosts.filter { post ->
            (filters.type == null || post.type == filters.type) &&
                (filters.species == null || post.species == filters.species) &&
                (filters.location.isBlank() || post.location.contains(filters.location, ignoreCase = true)) &&
                (filters.status == null || post.status == filters.status)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onTypeFilterChange(type: LostFoundType?) {
        _filters.update { it.copy(type = type) }
    }

    fun onSpeciesFilterChange(species: PetSpecies?) {
        _filters.update { it.copy(species = species) }
    }

    fun onLocationChange(location: String) {
        _filters.update { it.copy(location = location) }
    }

    fun onStatusFilterChange(status: LostFoundStatus?) {
        _filters.update { it.copy(status = status) }
    }

    fun markResolved(postId: String) {
        viewModelScope.launch {
            lostFoundRepository.updateStatus(postId, LostFoundStatus.RESOLVED)
        }
    }
}
