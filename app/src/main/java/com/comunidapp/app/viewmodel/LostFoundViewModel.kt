package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.LostFoundPost
import com.comunidapp.app.data.model.LostFoundSighting
import com.comunidapp.app.data.model.LostFoundStatus
import com.comunidapp.app.data.model.LostFoundType
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.LostFoundRepository
import com.comunidapp.app.data.repository.PlatformRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LostFoundFilters(
    val type: LostFoundType? = null,
    val species: PetSpecies? = null,
    val location: String = "",
    val status: LostFoundStatus? = LostFoundStatus.ACTIVE
)

class LostFoundViewModel(
    private val lostFoundRepository: LostFoundRepository = DataProvider.lostFoundRepository,
    private val platformRepository: PlatformRepository = DataProvider.platformRepository,
    private val authRepository: AuthRepository = AuthProvider.repository
) : ViewModel() {

    private val _filters = MutableStateFlow(LostFoundFilters())
    val filters: StateFlow<LostFoundFilters> = _filters.asStateFlow()

    private val _sightingsByPost = MutableStateFlow<Map<String, List<LostFoundSighting>>>(emptyMap())
    val sightingsByPost: StateFlow<Map<String, List<LostFoundSighting>>> = _sightingsByPost.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

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

    init {
        viewModelScope.launch {
            posts.collect { list ->
                list.forEach { post ->
                    launch {
                        platformRepository.observeSightings(post.id).collect { sightings ->
                            _sightingsByPost.update { current -> current + (post.id to sightings) }
                        }
                    }
                }
            }
        }
    }

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

    fun addSighting(postId: String, note: String, locationText: String) {
        val user = authRepository.getCurrentUser()
        if (user == null) {
            _message.value = "Iniciá sesión para reportar un avistamiento"
            return
        }
        if (note.isBlank()) {
            _message.value = "Escribí una nota del avistamiento"
            return
        }
        viewModelScope.launch {
            platformRepository.addSighting(
                LostFoundSighting(
                    id = "",
                    postId = postId,
                    reporterId = user.id,
                    reporterName = user.name,
                    note = note.trim(),
                    locationText = locationText.trim().ifBlank { null }
                )
            ).onSuccess {
                _message.value = "Avistamiento reportado"
            }.onFailure { error ->
                _message.value = error.message ?: "No se pudo reportar el avistamiento"
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
