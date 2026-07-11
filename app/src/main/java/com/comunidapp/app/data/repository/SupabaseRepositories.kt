package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.AdoptionPost
import com.comunidapp.app.data.model.AdoptionStatus
import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.model.LostFoundPost
import com.comunidapp.app.data.model.LostFoundStatus
import com.comunidapp.app.data.model.LostFoundType
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.remote.supabase.AdoptionSupabaseDataSource
import com.comunidapp.app.data.remote.supabase.LostFoundSupabaseDataSource
import com.comunidapp.app.data.remote.supabase.PetSupabaseDataSource
import com.comunidapp.app.data.remote.supabase.SocialSupabaseDataSource
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

    override suspend fun searchUsers(query: String, excludeUserId: String) =
        dataSource.searchUsers(query, excludeUserId)

    override fun observeUser(userId: String) = dataSource.observeUser(userId)

    override fun observeUsers() = dataSource.observeUsers()
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
    private val dataSource: PostSupabaseDataSource = PostSupabaseDataSource(),
    private val socialDataSource: SocialSupabaseDataSource = SocialSupabaseDataSource()
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

    override suspend fun refreshPosts(): Result<Unit> {
        return try {
            _posts.value = dataSource.fetchPosts()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun addFeedPost(post: FeedPost) = dataSource.addPost(post)

    override suspend fun updateFeedPost(post: FeedPost) = dataSource.updatePost(post)

    override suspend fun toggleLike(postId: String, userId: String): Result<Boolean> {
        val result = socialDataSource.toggleLike(postId, userId)
        refreshPosts()
        return result
    }

    override fun observeLikedPostIds(userId: String): Flow<Set<String>> =
        socialDataSource.observeLikedPostIds(userId)

    override fun observeComments(postId: String): Flow<List<com.comunidapp.app.data.model.PostComment>> =
        socialDataSource.observeComments(postId)

    override suspend fun addComment(
        postId: String,
        authorId: String,
        authorName: String,
        content: String
    ): Result<Unit> {
        val result = socialDataSource.addComment(postId, authorId, authorName, content)
        refreshPosts()
        return result
    }

    override suspend fun searchPosts(query: String): List<FeedPost> {
        if (query.isBlank()) return emptyList()
        return _posts.value.filter { post ->
            post.title.contains(query, ignoreCase = true) ||
                post.content.contains(query, ignoreCase = true) ||
                post.authorName.contains(query, ignoreCase = true)
        }
    }
}

class SupabaseAdoptionRepository(
    private val dataSource: AdoptionSupabaseDataSource = AdoptionSupabaseDataSource()
) : AdoptionRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _posts = MutableStateFlow<List<AdoptionPost>>(emptyList())
    override fun observeAdoptionPosts(): StateFlow<List<AdoptionPost>> = _posts.asStateFlow()

    init {
        scope.launch {
            dataSource.observeAdoptions().collect { posts ->
                _posts.value = posts
            }
        }
    }

    override fun getAdoptionPostById(id: String): AdoptionPost? =
        _posts.value.find { it.id == id }

    override fun getFilteredAdoptions(
        location: String?,
        sex: PetSex?,
        minAge: Int?,
        maxAge: Int?,
        size: PetSize?,
        status: AdoptionStatus?
    ): List<AdoptionPost> = _posts.value.filter { post ->
        (location.isNullOrBlank() || post.location.contains(location, ignoreCase = true)) &&
            (sex == null || post.sex == sex) &&
            (minAge == null || post.ageYears >= minAge) &&
            (maxAge == null || post.ageYears <= maxAge) &&
            (size == null || post.size == size) &&
            (status == null || post.status == status)
    }

    override fun getAdoptionsByShelter(shelterId: String): List<AdoptionPost> =
        _posts.value.filter { it.shelterId == shelterId || it.publisherId == shelterId }

    override suspend fun addAdoptionPost(post: AdoptionPost): Result<String> =
        dataSource.addAdoption(post)

    override suspend fun updateAdoptionPost(post: AdoptionPost): Result<Unit> =
        dataSource.updateAdoption(post)

    override suspend fun updateAdoptionStatus(id: String, status: com.comunidapp.app.data.model.AdoptionStatus): Result<Unit> {
        val post = getAdoptionPostById(id) ?: return Result.failure(NoSuchElementException())
        return dataSource.updateAdoption(post.copy(status = status))
    }

    override fun observeMyAdoptions(publisherId: String): Flow<List<AdoptionPost>> =
        kotlinx.coroutines.flow.flow {
            while (true) {
                emit(_posts.value.filter { it.publisherId == publisherId || it.shelterId == publisherId })
                kotlinx.coroutines.delay(4_000)
            }
        }
}

class SupabaseLostFoundRepository(
    private val dataSource: LostFoundSupabaseDataSource = LostFoundSupabaseDataSource()
) : LostFoundRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _posts = MutableStateFlow<List<LostFoundPost>>(emptyList())
    override fun observeLostFoundPosts(): StateFlow<List<LostFoundPost>> = _posts.asStateFlow()

    init {
        scope.launch {
            dataSource.observeLostFound().collect { posts ->
                _posts.value = posts
            }
        }
    }

    override fun getFilteredLostFound(
        type: LostFoundType?,
        species: PetSpecies?,
        location: String?,
        status: LostFoundStatus?
    ): List<LostFoundPost> = _posts.value.filter { post ->
        (type == null || post.type == type) &&
            (species == null || post.species == species) &&
            (location.isNullOrBlank() || post.location.contains(location, ignoreCase = true)) &&
            (status == null || post.status == status)
    }

    override suspend fun addLostFoundPost(post: LostFoundPost): Result<String> =
        dataSource.addLostFound(post)

    override suspend fun updateLostFoundPost(post: LostFoundPost): Result<Unit> =
        dataSource.updateLostFound(post)

    override suspend fun updateStatus(id: String, status: LostFoundStatus): Result<Unit> =
        dataSource.updateStatus(id, status)
}

class SupabaseAdoptionRequestRepository(
    private val socialDataSource: SocialSupabaseDataSource = SocialSupabaseDataSource()
) : AdoptionRequestRepository {

    override fun observeRequestsForPublisher(publisherId: String): Flow<List<com.comunidapp.app.data.model.AdoptionRequest>> =
        kotlinx.coroutines.flow.flow {
            while (true) {
                emit(socialDataSource.fetchAdoptionRequestsForPublisher(publisherId))
                kotlinx.coroutines.delay(4_000)
            }
        }

    override fun observeRequestsForAdoption(adoptionId: String): Flow<List<com.comunidapp.app.data.model.AdoptionRequest>> =
        kotlinx.coroutines.flow.flow {
            while (true) {
                emit(socialDataSource.fetchAdoptionRequestsForAdoption(adoptionId))
                kotlinx.coroutines.delay(4_000)
            }
        }

    override suspend fun submitRequest(request: com.comunidapp.app.data.model.AdoptionRequest): Result<String> =
        socialDataSource.submitAdoptionRequest(request)

    override suspend fun updateRequestStatus(
        id: String,
        status: com.comunidapp.app.data.model.AdoptionRequestStatus
    ): Result<Unit> = socialDataSource.updateAdoptionRequestStatus(id, status)

    override suspend fun scheduleInterview(id: String, dateText: String, notes: String): Result<Unit> =
        socialDataSource.scheduleAdoptionInterview(id, dateText, notes)
}