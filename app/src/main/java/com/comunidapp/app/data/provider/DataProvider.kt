package com.comunidapp.app.data.provider

import com.comunidapp.app.core.config.AppConfigProvider
import com.comunidapp.app.data.remote.storage.ImageStorageService
import com.comunidapp.app.data.remote.storage.OrganizationMediaStorageService
import com.comunidapp.app.data.remote.storage.ProfileAvatarStorageService
import com.comunidapp.app.data.remote.storage.SupabaseStorageService
import com.comunidapp.app.data.repository.AdoptionRepository
import com.comunidapp.app.data.repository.AdoptionRequestRepository
import com.comunidapp.app.data.repository.ChatRepository
import com.comunidapp.app.data.repository.CommunityRepository
import com.comunidapp.app.data.repository.MockAdoptionRequestRepository
import com.comunidapp.app.data.repository.MockChatRepository
import com.comunidapp.app.data.repository.MockCommunityRepository
import com.comunidapp.app.data.repository.SupabaseAdoptionRequestRepository
import com.comunidapp.app.data.repository.SupabaseChatRepository
import com.comunidapp.app.data.repository.FeedRepository
import com.comunidapp.app.data.repository.FriendRepository
import com.comunidapp.app.data.repository.MockFriendRepository
import com.comunidapp.app.data.repository.SupabaseFriendRepository
import com.comunidapp.app.data.repository.LostFoundRepository
import com.comunidapp.app.data.repository.MockAdoptionRepository
import com.comunidapp.app.data.repository.MockFeedRepository
import com.comunidapp.app.data.repository.MockLostFoundRepository
import com.comunidapp.app.data.repository.MockPetRepository
import com.comunidapp.app.data.repository.MockShelterRepository
import com.comunidapp.app.data.repository.MockUserRepository
import com.comunidapp.app.data.repository.PetRepository
import com.comunidapp.app.data.repository.MockPlatformRepository
import com.comunidapp.app.data.repository.MockOrganizationInvitationRepository
import com.comunidapp.app.data.repository.MockOrganizationMembershipRepository
import com.comunidapp.app.data.repository.MockOrganizationPermissionRepository
import com.comunidapp.app.data.repository.MockOrganizationRepository
import com.comunidapp.app.data.repository.MockPermissionRepository
import com.comunidapp.app.data.repository.MockPlatformAdministrationRepository
import com.comunidapp.app.data.repository.MockServiceRepository
import com.comunidapp.app.data.repository.OrganizationInvitationRepository
import com.comunidapp.app.data.repository.OrganizationMembershipRepository
import com.comunidapp.app.data.repository.OrganizationPermissionRepository
import com.comunidapp.app.data.repository.OrganizationRepository
import com.comunidapp.app.data.repository.PermissionRepository
import com.comunidapp.app.data.repository.PlatformAdministrationRepository
import com.comunidapp.app.data.repository.PlatformRepository
import com.comunidapp.app.data.repository.ServiceRepository
import com.comunidapp.app.data.repository.SupabaseOrganizationInvitationRepository
import com.comunidapp.app.data.repository.SupabaseOrganizationMembershipRepository
import com.comunidapp.app.data.repository.SupabaseOrganizationPermissionRepository
import com.comunidapp.app.data.repository.SupabaseOrganizationRepository
import com.comunidapp.app.data.repository.SupabasePermissionRepository
import com.comunidapp.app.data.repository.SupabasePlatformAdministrationRepository
import com.comunidapp.app.data.repository.SupabasePlatformRepository
import com.comunidapp.app.data.repository.SupabaseServiceRepository
import com.comunidapp.app.data.repository.ShelterRepository
import com.comunidapp.app.data.repository.SupabaseAdoptionRepository
import com.comunidapp.app.data.repository.SupabaseCommunityRepository
import com.comunidapp.app.data.repository.SupabaseFeedRepository
import com.comunidapp.app.data.repository.SupabaseLostFoundRepository
import com.comunidapp.app.data.repository.SupabasePetRepository
import com.comunidapp.app.data.repository.SupabaseShelterRepository
import com.comunidapp.app.data.repository.SupabaseUserRepository
import com.comunidapp.app.data.repository.UserRepository

object DataProvider {

    val useSupabase: Boolean get() = AppConfigProvider.featureFlags().useSupabase

    val userRepository: UserRepository by lazy {
        if (useSupabase) SupabaseUserRepository() else MockUserRepository()
    }

    val petRepository: PetRepository by lazy {
        if (useSupabase) SupabasePetRepository() else MockPetRepository()
    }

    val feedRepository: FeedRepository by lazy {
        if (useSupabase) SupabaseFeedRepository() else MockFeedRepository()
    }

    val adoptionRepository: AdoptionRepository by lazy {
        if (useSupabase) SupabaseAdoptionRepository() else MockAdoptionRepository()
    }

    val lostFoundRepository: LostFoundRepository by lazy {
        if (useSupabase) SupabaseLostFoundRepository() else MockLostFoundRepository()
    }

    val adoptionRequestRepository: AdoptionRequestRepository by lazy {
        if (useSupabase) SupabaseAdoptionRequestRepository() else MockAdoptionRequestRepository()
    }

    val chatRepository: ChatRepository by lazy {
        if (useSupabase) SupabaseChatRepository() else MockChatRepository()
    }

    val communityRepository: CommunityRepository by lazy {
        if (useSupabase) SupabaseCommunityRepository() else MockCommunityRepository()
    }

    val shelterRepository: ShelterRepository by lazy {
        if (useSupabase) SupabaseShelterRepository() else MockShelterRepository()
    }

    val serviceRepository: ServiceRepository by lazy {
        if (useSupabase) SupabaseServiceRepository() else MockServiceRepository()
    }

    val friendRepository: FriendRepository by lazy {
        if (useSupabase) SupabaseFriendRepository() else MockFriendRepository()
    }

    val platformRepository: PlatformRepository by lazy {
        if (useSupabase) SupabasePlatformRepository() else MockPlatformRepository()
    }

    val permissionRepository: PermissionRepository by lazy {
        if (useSupabase) SupabasePermissionRepository() else MockPermissionRepository()
    }

    /**
     * M03 Etapa 4: invitaciones Supabase cuando useSupabase; mock local en caso contrario.
     */
    private val mockOrganizationMembershipRepository by lazy {
        MockOrganizationMembershipRepository()
    }

    private val mockOrganizationInvitationRepository by lazy {
        MockOrganizationInvitationRepository(mockOrganizationMembershipRepository)
    }

    private val mockOrganizationRepository by lazy {
        MockOrganizationRepository(mockOrganizationMembershipRepository)
    }

    val organizationRepository: OrganizationRepository by lazy {
        if (useSupabase) SupabaseOrganizationRepository() else mockOrganizationRepository
    }

    val organizationMembershipRepository: OrganizationMembershipRepository by lazy {
        if (useSupabase) {
            SupabaseOrganizationMembershipRepository()
        } else {
            mockOrganizationMembershipRepository
        }
    }

    val organizationInvitationRepository: OrganizationInvitationRepository by lazy {
        if (useSupabase) {
            SupabaseOrganizationInvitationRepository(organizationMembershipRepository)
        } else {
            mockOrganizationInvitationRepository
        }
    }

    val organizationPermissionRepository: OrganizationPermissionRepository by lazy {
        if (useSupabase) {
            SupabaseOrganizationPermissionRepository(
                organizationRepository,
                organizationMembershipRepository
            )
        } else {
            MockOrganizationPermissionRepository(
                organizationRepository,
                organizationMembershipRepository
            )
        }
    }

    val platformAdministrationRepository: PlatformAdministrationRepository by lazy {
        if (useSupabase) {
            SupabasePlatformAdministrationRepository()
        } else {
            MockPlatformAdministrationRepository(permissionRepository)
        }
    }

    val storageService: ImageStorageService? by lazy {
        if (useSupabase) SupabaseStorageService() else null
    }

    val profileAvatarStorage: ProfileAvatarStorageService? by lazy {
        if (useSupabase) ProfileAvatarStorageService() else null
    }

    val organizationMediaStorage: OrganizationMediaStorageService? by lazy {
        if (useSupabase) OrganizationMediaStorageService() else null
    }
}
