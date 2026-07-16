package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.model.FriendConnection
import com.comunidapp.app.data.model.FriendConnectionStatus
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.model.UserBadge
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.CommunityRepository
import com.comunidapp.app.data.repository.FeedRepository
import com.comunidapp.app.data.repository.FriendRepository
import com.comunidapp.app.data.repository.PermissionRepository
import com.comunidapp.app.data.repository.PetRepository
import com.comunidapp.app.data.repository.PlatformRepository
import com.comunidapp.app.data.repository.UserRepository
import com.comunidapp.app.domain.ProfilePrivacy
import com.comunidapp.app.domain.authorization.AuthorizationService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileUiState(
    val isLoading: Boolean = true,
    val user: User? = null,
    val pets: List<Pet> = emptyList(),
    val posts: List<FeedPost> = emptyList(),
    val friends: List<User> = emptyList(),
    val badges: List<UserBadge> = emptyList(),
    val pendingFriendRequests: Int = 0,
    val unreadNotifications: Int = 0,
    /** D-M02-08: solo con permiso real moderation.view */
    val canViewModeration: Boolean = false,
    /** Roles/admin: roles.view o users.change_status */
    val canViewPlatformAdmin: Boolean = false,
    val canViewCases: Boolean = false,
    val canReviewAppeals: Boolean = false,
    val canReviewVerification: Boolean = false,
    val canViewSupportStaff: Boolean = false,
    val canViewAudit: Boolean = false,
    val errorMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModel(
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val userRepository: UserRepository = DataProvider.userRepository,
    private val petRepository: PetRepository = DataProvider.petRepository,
    private val feedRepository: FeedRepository = DataProvider.feedRepository,
    private val friendRepository: FriendRepository = DataProvider.friendRepository,
    private val communityRepository: CommunityRepository = DataProvider.communityRepository,
    private val platformRepository: PlatformRepository = DataProvider.platformRepository,
    private val permissionRepository: PermissionRepository = DataProvider.permissionRepository
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
                combine(
                    profileCore,
                    userRepository.observeUsers(),
                    platformRepository.observeNotifications(authUser.id),
                    flow {
                        emit(permissionRepository.refresh(authUser.id))
                        permissionRepository.observeAuthorizationContext(authUser.id).collect {
                            emit(it)
                        }
                    }
                ) { core, users, notifications, authz ->
                    val user = core.profile ?: authUser
                    val friendIds = ProfilePrivacy.friendIdsFor(authUser.id, core.connections)
                    val friends = users.filter { it.id in friendIds }
                    val pendingFriendRequests = core.connections.count {
                        it.status == FriendConnectionStatus.PENDING &&
                            it.addresseeId == authUser.id
                    }
                    ProfileUiState(
                        isLoading = false,
                        user = user,
                        pets = core.pets.filter { it.ownerId == authUser.id },
                        posts = core.posts.filter { it.authorId == authUser.id },
                        friends = friends,
                        badges = core.badges.ifEmpty { user.badges },
                        pendingFriendRequests = pendingFriendRequests,
                        unreadNotifications = notifications.count { it.isUnread },
                        canViewModeration = AuthorizationService.canViewModeration(authz),
                        canViewPlatformAdmin = AuthorizationService.hasPermission(
                            authz,
                            com.comunidapp.app.domain.authorization.PermissionCode.ROLES_VIEW
                        ) || AuthorizationService.hasPermission(
                            authz,
                            com.comunidapp.app.domain.authorization.PermissionCode.USERS_CHANGE_STATUS
                        ),
                        canViewCases = AuthorizationService.canViewModeration(authz),
                        canReviewAppeals = AuthorizationService.hasPermission(
                            authz,
                            com.comunidapp.app.domain.authorization.PermissionCode.MODERATION_REVIEW_APPEALS
                        ),
                        canReviewVerification = AuthorizationService.hasPermission(
                            authz,
                            com.comunidapp.app.domain.authorization.PermissionCode.ORGANIZATIONS_REVIEW_VERIFICATION
                        ),
                        canViewSupportStaff = AuthorizationService.hasPermission(
                            authz,
                            com.comunidapp.app.domain.authorization.PermissionCode.SUPPORT_VIEW
                        ),
                        canViewAudit = AuthorizationService.hasPermission(
                            authz,
                            com.comunidapp.app.domain.authorization.PermissionCode.AUDIT_VIEW
                        )
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
        viewModelScope.launch {
            permissionRepository.invalidate()
            authRepository.logout()
        }
    }

    private data class ProfileCore(
        val profile: User?,
        val pets: List<Pet>,
        val posts: List<FeedPost>,
        val connections: List<FriendConnection>,
        val badges: List<UserBadge>
    )
}
