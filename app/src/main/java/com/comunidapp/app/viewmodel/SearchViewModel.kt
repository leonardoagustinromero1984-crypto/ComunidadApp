package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.AdoptionPost
import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AdoptionRepository
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.FeedRepository
import com.comunidapp.app.data.repository.FriendRepository
import com.comunidapp.app.data.repository.PetRepository
import com.comunidapp.app.data.repository.UserRepository
import com.comunidapp.app.domain.ProfilePrivacy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

data class SearchResults(
    val posts: List<FeedPost> = emptyList(),
    val users: List<User> = emptyList(),
    val pets: List<Pet> = emptyList(),
    val adoptions: List<AdoptionPost> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val feedRepository: FeedRepository = DataProvider.feedRepository,
    private val userRepository: UserRepository = DataProvider.userRepository,
    private val petRepository: PetRepository = DataProvider.petRepository,
    private val adoptionRepository: AdoptionRepository = DataProvider.adoptionRepository,
    private val friendRepository: FriendRepository = DataProvider.friendRepository,
    private val authRepository: AuthRepository = AuthProvider.repository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow(SearchResults())
    val results: StateFlow<SearchResults> = _results.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    fun onQueryChange(value: String) {
        _query.value = value
        if (value.length >= 2) search(value) else _results.value = SearchResults()
    }

    fun search(text: String = _query.value) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _isSearching.value = true
            val currentUser = authRepository.getCurrentUser()
            val users = userRepository.observeUsers().first()
            val usersById = users.associateBy { it.id }
            val connections = currentUser?.let {
                friendRepository.observeConnections(it.id).first()
            }.orEmpty()
            val friendIds = currentUser?.let {
                ProfilePrivacy.friendIdsFor(it.id, connections)
            }.orEmpty()

            val posts = ProfilePrivacy.filterVisiblePosts(
                feedRepository.searchPosts(text),
                usersById,
                currentUser?.id,
                friendIds
            )
            val matchingUsers = users.filter {
                it.id != currentUser?.id && (
                    it.name.contains(text, ignoreCase = true) ||
                        it.locationText?.contains(text, ignoreCase = true) == true
                    )
            }
            val pets = ProfilePrivacy.filterVisiblePets(
                petRepository.observePets().first().filter {
                    it.name.contains(text, ignoreCase = true) ||
                        it.description.contains(text, ignoreCase = true)
                },
                usersById,
                currentUser?.id,
                friendIds
            )
            val adoptions = adoptionRepository.observeAdoptionPosts().first().filter {
                it.name.contains(text, ignoreCase = true) ||
                    it.location.contains(text, ignoreCase = true)
            }
            _results.value = SearchResults(posts, matchingUsers, pets, adoptions)
            _isSearching.value = false
        }
    }
}
