package com.comunidapp.app.data.provider

import com.comunidapp.app.BuildConfig
import com.comunidapp.app.data.remote.storage.ImageStorageService
import com.comunidapp.app.data.remote.storage.SupabaseStorageService
import com.comunidapp.app.data.repository.AdoptionRepository
import com.comunidapp.app.data.repository.FeedRepository
import com.comunidapp.app.data.repository.LostFoundRepository
import com.comunidapp.app.data.repository.MockAdoptionRepository
import com.comunidapp.app.data.repository.MockFeedRepository
import com.comunidapp.app.data.repository.MockLostFoundRepository
import com.comunidapp.app.data.repository.MockPetRepository
import com.comunidapp.app.data.repository.MockShelterRepository
import com.comunidapp.app.data.repository.MockUserRepository
import com.comunidapp.app.data.repository.PetRepository
import com.comunidapp.app.data.repository.ShelterRepository
import com.comunidapp.app.data.repository.SupabaseFeedRepository
import com.comunidapp.app.data.repository.SupabasePetRepository
import com.comunidapp.app.data.repository.SupabaseUserRepository
import com.comunidapp.app.data.repository.UserRepository
import com.comunidapp.app.data.repository.FriendsRepository
import com.comunidapp.app.data.repository.MockFriendsRepository

object DataProvider {

    val useSupabase: Boolean get() = BuildConfig.SUPABASE_ENABLED

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
        MockAdoptionRepository()
    }

    val lostFoundRepository: LostFoundRepository by lazy {
        MockLostFoundRepository()
    }

    val shelterRepository: ShelterRepository by lazy {
        MockShelterRepository()
    }

    val friendsRepository: FriendsRepository by lazy {
        MockFriendsRepository(userRepository)
    }

    val storageService: ImageStorageService? by lazy {
        if (useSupabase) SupabaseStorageService() else null
    }
}
