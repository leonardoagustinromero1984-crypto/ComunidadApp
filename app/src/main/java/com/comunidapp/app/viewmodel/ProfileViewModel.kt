package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.model.Friendship
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.FeedRepository
import com.comunidapp.app.data.repository.FriendsRepository
import com.comunidapp.app.data.repository.PetRepository
import com.comunidapp.app.data.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

data class ProfileUiState(
    val isLoading: Boolean = true,
    val user: User? = null,
    val pets: List<Pet> = emptyList(),
    val posts: List<FeedPost> = emptyList(),
    val friends: List<User> = emptyList(),
    val pendingRequests: List<Friendship> = emptyList(),
    val errorMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModel(
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val userRepository: UserRepository = DataProvider.userRepository,
    private val petRepository: PetRepository = DataProvider.petRepository,
    private val feedRepository: FeedRepository = DataProvider.feedRepository,
    private val friendsRepository: FriendsRepository = DataProvider.friendsRepository
) : ViewModel() {

    val uiState: StateFlow<ProfileUiState> = authRepository.observeAuthState()
        .flatMapLatest { authUser ->
            if (authUser == null) {
                flowOf(ProfileUiState(isLoading = false, user = null))
            } else {
                combine(
                    userRepository.observeUser(authUser.id),
                    petRepository.observePets(),
                    feedRepository.observeFeedPosts(),
                    friendsRepository.observeFriends(authUser.id),
                    friendsRepository.observePendingRequests(authUser.id)
                ) { profile, pets, posts, friends, pending ->
                    val user = profile ?: authUser
                    ProfileUiState(
                        isLoading = false,
                        user = user,
                        pets = pets.filter { it.ownerId == authUser.id },
                        posts = posts.filter { it.authorId == authUser.id },
                        friends = friends,
                        pendingRequests = pending
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ProfileUiState()
        )

    fun logout() {
        authRepository.logout()
    }
}
