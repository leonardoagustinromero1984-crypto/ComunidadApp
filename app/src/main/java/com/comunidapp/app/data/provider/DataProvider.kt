package com.comunidapp.app.data.provider

import com.comunidapp.app.BuildConfig
import com.comunidapp.app.data.remote.storage.FirebaseStorageService
import com.comunidapp.app.data.repository.AdoptionRepository
import com.comunidapp.app.data.repository.FeedRepository
import com.comunidapp.app.data.repository.FirebaseFeedRepository
import com.comunidapp.app.data.repository.FirebasePetRepository
import com.comunidapp.app.data.repository.FirebaseUserRepository
import com.comunidapp.app.data.repository.LostFoundRepository
import com.comunidapp.app.data.repository.MockAdoptionRepository
import com.comunidapp.app.data.repository.MockFeedRepository
import com.comunidapp.app.data.repository.MockLostFoundRepository
import com.comunidapp.app.data.repository.MockPetRepository
import com.comunidapp.app.data.repository.MockShelterRepository
import com.comunidapp.app.data.repository.MockUserRepository
import com.comunidapp.app.data.repository.PetRepository
import com.comunidapp.app.data.repository.ShelterRepository
import com.comunidapp.app.data.repository.UserRepository

object DataProvider {

    val useFirebase: Boolean get() = BuildConfig.FIREBASE_ENABLED

    val userRepository: UserRepository by lazy {
        if (useFirebase) FirebaseUserRepository() else MockUserRepository()
    }

    val petRepository: PetRepository by lazy {
        if (useFirebase) FirebasePetRepository() else MockPetRepository()
    }

    val feedRepository: FeedRepository by lazy {
        if (useFirebase) FirebaseFeedRepository() else MockFeedRepository()
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

    val storageService: FirebaseStorageService? by lazy {
        if (useFirebase) FirebaseStorageService() else null
    }
}
