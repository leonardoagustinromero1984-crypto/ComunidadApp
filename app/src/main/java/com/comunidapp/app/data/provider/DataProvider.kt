package com.comunidapp.app.data.provider

import com.comunidapp.app.core.config.AppConfigProvider
import com.comunidapp.app.data.remote.storage.ImageStorageService
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
import com.comunidapp.app.data.repository.MockServiceRepository
import com.comunidapp.app.data.repository.PlatformRepository
import com.comunidapp.app.data.repository.ServiceRepository
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

    val storageService: ImageStorageService? by lazy {
        if (useSupabase) SupabaseStorageService() else null
    }
}
