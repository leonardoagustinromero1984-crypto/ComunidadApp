package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.remote.supabase.PetSupabaseDataSource
import com.comunidapp.app.data.remote.supabase.PostSupabaseDataSource
import com.comunidapp.app.data.remote.supabase.UserSupabaseDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SupabaseUserRepository(
    private val dataSource: UserSupabaseDataSource = UserSupabaseDataSource()
) : UserRepository {

    override suspend fun getUser(userId: String) = dataSource.getUser(userId)

    override suspend fun createUser(user: com.comunidapp.app.data.model.User) = dataSource.createUser(user)

    override suspend fun updateUser(user: com.comunidapp.app.data.model.User) = dataSource.updateUser(user)

    override fun observeUser(userId: String) = dataSource.observeUser(userId)
}

class SupabasePetRepository(
    private val dataSource: PetSupabaseDataSource = PetSupabaseDataSource()
) : PetRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _pets = MutableStateFlow<List<Pet>>(emptyList())
    override fun observePets(): StateFlow<List<Pet>> = _pets.asStateFlow()

    init {
        scope.launch {
            dataSource.observePets().collect { pets ->
                _pets.value = pets
            }
        }
    }

    override fun observePetsForOwner(ownerId: String): Flow<List<Pet>> =
        dataSource.observePetsForOwner(ownerId)

    override fun observePet(petId: String): Flow<Pet?> = dataSource.observePet(petId)

    override fun getPetsByOwner(ownerId: String): List<Pet> =
        _pets.value.filter { it.ownerId == ownerId }

    override fun getPetById(petId: String): Pet? =
        _pets.value.find { it.id == petId }

    override suspend fun fetchPetById(petId: String): Pet? =
        dataSource.getPet(petId) ?: getPetById(petId)

    override suspend fun createPet(pet: Pet) = dataSource.createPet(pet)

    override suspend fun updatePet(pet: Pet) = dataSource.updatePet(pet)

    override suspend fun deletePet(petId: String) = dataSource.deletePet(petId)
}

class SupabaseFeedRepository(
    private val dataSource: PostSupabaseDataSource = PostSupabaseDataSource()
) : FeedRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _posts = MutableStateFlow<List<FeedPost>>(emptyList())
    override fun observeFeedPosts(): StateFlow<List<FeedPost>> = _posts.asStateFlow()

    init {
        scope.launch {
            dataSource.observePosts().collect { posts ->
                _posts.value = posts
            }
        }
    }

    override suspend fun addFeedPost(post: FeedPost) = dataSource.addPost(post)

    override suspend fun updateFeedPost(post: FeedPost) = dataSource.updatePost(post)
}
