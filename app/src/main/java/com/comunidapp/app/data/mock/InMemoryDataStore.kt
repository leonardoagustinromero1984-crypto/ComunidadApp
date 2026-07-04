package com.comunidapp.app.data.mock

import com.comunidapp.app.data.model.AdoptionPost
import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.model.LostFoundPost
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.Shelter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object InMemoryDataStore {

    private val _feedPosts = MutableStateFlow(MockData.feedPosts)
    val feedPosts: StateFlow<List<FeedPost>> = _feedPosts.asStateFlow()

    private val _adoptionPosts = MutableStateFlow(MockData.adoptionPosts)
    val adoptionPosts: StateFlow<List<AdoptionPost>> = _adoptionPosts.asStateFlow()

    private val _shelters = MutableStateFlow(MockData.shelters)
    val shelters: StateFlow<List<Shelter>> = _shelters.asStateFlow()

    private val _lostFoundPosts = MutableStateFlow(MockData.lostFoundPosts)
    val lostFoundPosts: StateFlow<List<LostFoundPost>> = _lostFoundPosts.asStateFlow()

    private val _pets = MutableStateFlow(MockData.pets)
    val pets: StateFlow<List<Pet>> = _pets.asStateFlow()

    fun addFeedPost(post: FeedPost) {
        _feedPosts.update { listOf(post) + it }
    }

    fun addAdoptionPost(post: AdoptionPost) {
        _adoptionPosts.update { listOf(post) + it }
    }

    fun addLostFoundPost(post: LostFoundPost) {
        _lostFoundPosts.update { listOf(post) + it }
    }

    fun getAdoptionPostById(id: String): AdoptionPost? =
        _adoptionPosts.value.find { it.id == id }

    fun getShelterById(id: String): Shelter? =
        _shelters.value.find { it.id == id }

    fun getPetById(id: String): Pet? =
        _pets.value.find { it.id == id }

    fun addPet(pet: Pet): Result<String> {
        val id = pet.id.ifBlank { "pet_${System.currentTimeMillis()}" }
        val saved = pet.copy(id = id)
        _pets.update { listOf(saved) + it }
        return Result.success(id)
    }

    fun updatePet(pet: Pet): Result<Unit> {
        _pets.update { list ->
            if (list.none { it.id == pet.id }) {
                return Result.failure(IllegalArgumentException("Mascota no encontrada"))
            }
            list.map { if (it.id == pet.id) pet else it }
        }
        return Result.success(Unit)
    }

    fun deletePet(petId: String): Result<Unit> {
        _pets.update { list ->
            if (list.none { it.id == petId }) {
                return Result.failure(IllegalArgumentException("Mascota no encontrada"))
            }
            list.filterNot { it.id == petId }
        }
        return Result.success(Unit)
    }

    fun updateFeedPost(post: FeedPost) {
        _feedPosts.update { list ->
            list.map { if (it.id == post.id) post else it }
        }
    }

    fun getAdoptionsByShelter(shelterId: String): List<AdoptionPost> =
        _adoptionPosts.value.filter { it.shelterId == shelterId }
}
