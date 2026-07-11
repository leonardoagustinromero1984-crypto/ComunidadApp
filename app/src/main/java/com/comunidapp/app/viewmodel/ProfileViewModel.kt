package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.model.FriendConnection
import com.comunidapp.app.data.model.FriendConnectionStatus
import com.comunidapp.app.data.model.Friendship
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.model.UserBadge
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.CommunityRepository
import com.comunidapp.app.data.repository.FeedRepository
import com.comunidapp.app.data.repository.FriendRepository
import com.comunidapp.app.data.repository.FriendsRepository
import com.comunidapp.app.data.repository.PetRepository
import com.comunidapp.app.data.repository.PlatformRepository
import com.comunidapp.app.data.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

data class ProfileUiState(
    val isLoading: Boolean = true,
    val user: User? = null,
    val pets: List<Pet> = emptyList(),
    val posts: List<FeedPost> = emptyList(),
    val friends: List<User> = emptyList(),
    val pendingRequests: List<Friendship> = emptyList(),
    val badges: List<UserBadge> = emptyList(),
    val pendingFriendRequests: Int = 0,
    val unreadNotifications: Int = 0,
    val errorMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModel(
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val userRepository: UserRepository = DataProvider.userRepository,
    private val petRepository: PetRepository = DataProvider.petRepository,
    private val feedRepository: FeedRepository = DataProvider.feedRepository,
    private val friendRepository: FriendRepository = DataProvider.friendRepository,
    private val friendsRepository: FriendsRepository = DataProvider.friendsRepository,
    private val communityRepository: CommunityRepository = DataProvider.communityRepository,
    private val platformRepository: PlatformRepository = DataProvider.platformRepository
) : ViewModel() {

    val uiState: StateFlow<ProfileUiState> = authRepository.observeAuthState()
        .flatMapLatest { authUser ->
            if (authUser == null) {
                flowOf(ProfileUiState(isLoading = false, user = null))
            } else {
                val badgesFlow = flow {
                    emit(communityRepository.fetchUserBadges(authUser.id))
                }
                val profileCore = combine(
                    userRepository.observeUser(authUser.id),
                    petRepository.observePets(),
                    feedRepository.observeFeedPosts(),
                    friendRepository.observeConnections(authUser.id),
                    badgesFlow
                ) { profile, pets, posts, connections, badges ->
                    ProfileCore(profile, pets, posts, connections, badges)
                }
                val socialCore = combine(
                    friendsRepository.observeFriends(authUser.id),
                    friendsRepository.observePendingRequests(authUser.id),
                    platformRepository.observeNotifications(authUser.id)
                ) { friends, pending, notifications ->
                    SocialCore(friends, pending, notifications.count { it.isUnread })
                }
                combine(profileCore, socialCore) { core, social ->
                    val user = core.profile ?: authUser
                    val pendingFriendRequests = core.connections.count {
                        it.status == FriendConnectionStatus.PENDING &&
                            it.addresseeId == authUser.id
                    }
                    ProfileUiState(
                        isLoading = false,
                        user = user,
                        pets = core.pets.filter { it.ownerId == authUser.id },
                        posts = core.posts.filter { it.authorId == authUser.id },
                        friends = social.friends,
                        pendingRequests = social.pending,
                        badges = core.badges.ifEmpty { user.badges },
                        pendingFriendRequests = pendingFriendRequests,
                        unreadNotifications = social.unreadNotifications
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

    private data class ProfileCore(
        val profile: User?,
        val pets: List<Pet>,
        val posts: List<FeedPost>,
        val connections: List<FriendConnection>,
        val badges: List<UserBadge>
    )

    private data class SocialCore(
        val friends: List<User>,
        val pending: List<Friendship>,
        val unreadNotifications: Int
    )
}
