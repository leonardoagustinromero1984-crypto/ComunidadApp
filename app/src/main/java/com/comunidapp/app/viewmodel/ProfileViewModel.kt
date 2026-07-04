package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.model.LostFoundPost
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.mock.MockData
import com.comunidapp.app.data.repository.FeedRepository
import com.comunidapp.app.data.repository.LostFoundRepository
import com.comunidapp.app.data.repository.MockFeedRepository
import com.comunidapp.app.data.repository.MockLostFoundRepository
import com.comunidapp.app.data.repository.MockPetRepository
import com.comunidapp.app.data.repository.PetRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ProfileViewModel(
    petRepository: PetRepository = MockPetRepository(),
    feedRepository: FeedRepository = MockFeedRepository()
) : ViewModel() {

    private val _user = MutableStateFlow(MockData.currentUser)
    val user: StateFlow<User> = _user.asStateFlow()

    val pets: StateFlow<List<Pet>> = petRepository.observePets()
        .map { all -> all.filter { it.ownerId == MockData.currentUser.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val posts: StateFlow<List<FeedPost>> = feedRepository.observeFeedPosts()
        .map { all -> all.filter { it.authorId == MockData.currentUser.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

class MyPetsViewModel(
    petRepository: PetRepository = MockPetRepository()
) : ViewModel() {

    val pets: StateFlow<List<Pet>> = petRepository.observePets()
        .map { all -> all.filter { it.ownerId == MockData.currentUser.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

class LostFoundViewModel(
    lostFoundRepository: LostFoundRepository = MockLostFoundRepository()
) : ViewModel() {

    val posts: StateFlow<List<LostFoundPost>> = lostFoundRepository.observeLostFoundPosts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
