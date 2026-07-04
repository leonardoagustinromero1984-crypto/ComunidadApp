package com.comunidapp.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.FeedRepository
import com.comunidapp.app.data.repository.PetRepository
import com.comunidapp.app.data.repository.UserRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

data class PublicProfileUiState(
    val isLoading: Boolean = true,
    val user: User? = null,
    val pets: List<Pet> = emptyList(),
    val posts: List<FeedPost> = emptyList(),
    val errorMessage: String? = null
)

class UserPublicProfileViewModel(
    savedStateHandle: SavedStateHandle,
    private val userRepository: UserRepository = DataProvider.userRepository,
    private val petRepository: PetRepository = DataProvider.petRepository,
    private val feedRepository: FeedRepository = DataProvider.feedRepository
) : ViewModel() {

    private val userId: String = savedStateHandle["userId"] ?: ""

    val uiState: StateFlow<PublicProfileUiState> = combine(
        if (userId.isBlank()) flowOf(null) else userRepository.observeUser(userId),
        petRepository.observePets(),
        feedRepository.observeFeedPosts()
    ) { user, pets, posts ->
        if (userId.isBlank()) {
            PublicProfileUiState(isLoading = false, errorMessage = "Perfil no válido")
        } else if (user == null) {
            PublicProfileUiState(isLoading = false, errorMessage = "Usuario no encontrado")
        } else {
            PublicProfileUiState(
                isLoading = false,
                user = user,
                pets = pets.filter { it.ownerId == userId },
                posts = posts.filter { it.authorId == userId }
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        PublicProfileUiState()
    )
}
