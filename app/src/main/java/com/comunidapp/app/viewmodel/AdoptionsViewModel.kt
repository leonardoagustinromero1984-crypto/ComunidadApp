package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.AdoptionPost
import com.comunidapp.app.data.model.AdoptionStatus
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AdoptionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class AdoptionFilters(
    val location: String = "",
    val sex: PetSex? = null,
    val size: PetSize? = null,
    val status: AdoptionStatus? = null
)

class AdoptionsViewModel(
    private val adoptionRepository: AdoptionRepository = DataProvider.adoptionRepository
) : ViewModel() {

    private val _filters = MutableStateFlow(AdoptionFilters())
    val filters: StateFlow<AdoptionFilters> = _filters.asStateFlow()

    val posts: StateFlow<List<AdoptionPost>> = combine(
        adoptionRepository.observeAdoptionPosts(),
        _filters
    ) { allPosts, filters ->
        allPosts.filter { post ->
            (filters.location.isBlank() || post.location.contains(filters.location, ignoreCase = true)) &&
                (filters.sex == null || post.sex == filters.sex) &&
                (filters.size == null || post.size == filters.size) &&
                (filters.status == null || post.status == filters.status)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
