package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.AdoptionPost
import com.comunidapp.app.data.model.AdoptionStatus
import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.model.LostFoundPost
import com.comunidapp.app.data.model.LostFoundStatus
import com.comunidapp.app.data.model.LostFoundType
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.remote.supabase.AdoptionSupabaseDataSource
import com.comunidapp.app.data.remote.supabase.LostFoundSupabaseDataSource
import com.comunidapp.app.data.remote.supabase.SocialSupabaseDataSource
import com.comunidapp.app.data.remote.supabase.PostSupabaseDataSource
import com.comunidapp.app.data.remote.supabase.UserSupabaseDataSource
import com.comunidapp.app.data.remote.supabase.m09.CreateAdoptionParams
import com.comunidapp.app.data.remote.supabase.m09.M09AdoptionErrorMapper
import com.comunidapp.app.data.remote.supabase.m09.M09AdoptionException
import com.comunidapp.app.data.remote.supabase.m09.SupabaseAdoptionM09RemoteDataSource
import com.comunidapp.app.data.remote.supabase.m09.UpdateAdoptionParams
import com.comunidapp.app.data.remote.supabase.toAdoptionPost
import com.comunidapp.app.domain.user.UserProfileMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
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

    override suspend fun getOwnProfile(userId: String) = dataSource.getOwnProfile(userId)

    override fun observeOwnProfile(userId: String) =
        observeUser(userId).map { user ->
            user?.let {
                // privacy fetched lazily; bridge mapea desde flags legacy si falla
                UserProfileMapper.toUserProfile(it)
            }
        }

    override suspend fun isUsernameAvailable(username: String, excludingUserId: String?) =
        dataSource.isUsernameAvailable(username)

    override suspend fun completeOnboarding(
        userId: String,
        command: com.comunidapp.app.domain.user.CompleteOnboardingCommand
    ) = dataSource.completeOnboarding(command)

    override suspend fun updateMyProfile(
        userId: String,
        command: com.comunidapp.app.domain.user.UpdateMyProfileCommand
    ) = dataSource.updateMyProfile(command)

    override suspend fun getPublicProfile(viewerId: String, targetUserId: String) =
        dataSource.getPublicProfile(targetUserId)

    override suspend fun searchPublicProfiles(viewerId: String, query: String, limit: Int) =
        dataSource.searchPublicProfiles(query, limit)

    override suspend fun getPrivacySettings(userId: String) =
        dataSource.getPrivacySettings(userId)

    override suspend fun updatePrivacySettings(
        userId: String,
        settings: com.comunidapp.app.domain.user.UserPrivacySettings
    ) = dataSource.updatePrivacySettings(userId, settings)
}

/**
 * Legacy direct-table pet repository (pre-M08). Kept for reference only.
 * DataProvider wires [LegacyPetRepositoryAdapter] when useSupabase.
 */
@Deprecated("Use LegacyPetRepositoryAdapter (M08 Etapa 4B)")
class SupabasePetRepository(
    private val adapter: LegacyPetRepositoryAdapter = LegacyPetRepositoryAdapter()
) : PetRepository by adapter

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
    private val m09: SupabaseAdoptionM09RemoteDataSource = SupabaseAdoptionM09RemoteDataSource(),
    private val legacy: AdoptionSupabaseDataSource = AdoptionSupabaseDataSource()
) : AdoptionRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _posts = MutableStateFlow<List<AdoptionPost>>(emptyList())
    override fun observeAdoptionPosts(): StateFlow<List<AdoptionPost>> = _posts.asStateFlow()

    init {
        scope.launch { refreshPublished() }
    }

    private suspend fun refreshPublished() {
        try {
            _posts.value = m09.listPublished().map { it.toAdoptionPost() }
        } catch (_: Exception) {
            // keep last known
        }
    }

    override fun observePublishedAdoptions(): Flow<List<AdoptionPost>> =
        _posts.map { list -> list.filter { it.status == AdoptionStatus.PUBLISHED } }

    override fun observeMyAdoptions(publisherId: String): Flow<List<AdoptionPost>> =
        kotlinx.coroutines.flow.flow {
            while (true) {
                try {
                    emit(m09.listMine().map { it.toAdoptionPost() })
                } catch (_: Exception) {
                    emit(emptyList())
                }
                kotlinx.coroutines.delay(4_000)
            }
        }

    override fun getAdoptionPostById(id: String): AdoptionPost? =
        if (id.isBlank()) null else _posts.value.find { it.id == id }

    override suspend fun getAdoptionById(id: String): Result<AdoptionPost> {
        if (id.isBlank()) {
            return M09AdoptionErrorMapper.failure(
                M09AdoptionException(
                    "ADOPTION_NOT_FOUND",
                    M09AdoptionErrorMapper.userMessage("ADOPTION_NOT_FOUND")
                )
            )
        }
        return try {
            Result.success(m09.getById(id).toAdoptionPost())
        } catch (e: Exception) {
            M09AdoptionErrorMapper.failure(e)
        }
    }

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

    override suspend fun addAdoptionPost(post: AdoptionPost): Result<String> {
        if (post.petId.isNullOrBlank()) {
            return legacy.addAdoption(post)
        }
        return createAdoption(
            CreateAdoptionParams(
                petId = post.petId,
                title = post.title.ifBlank { post.name },
                description = post.description,
                requirements = post.requirements,
                locationText = post.location,
                publish = post.status == AdoptionStatus.PUBLISHED
            )
        ).map { it.id }
    }

    override suspend fun updateAdoptionPost(post: AdoptionPost): Result<Unit> =
        updateAdoption(
            UpdateAdoptionParams(
                adoptionId = post.id,
                title = post.title.ifBlank { post.name },
                description = post.description,
                requirements = post.requirements,
                locationText = post.location
            )
        ).map { }

    override suspend fun updateAdoptionStatus(id: String, status: AdoptionStatus): Result<Unit> =
        when (status) {
            AdoptionStatus.PAUSED -> pauseAdoption(id).map { }
            AdoptionStatus.PUBLISHED -> resumeAdoption(id).map { }
            AdoptionStatus.CLOSED -> closeAdoption(id).map { }
            AdoptionStatus.ADOPTED -> markAsAdopted(id).map { }
            AdoptionStatus.DRAFT -> setStatusRemote(id, "DRAFT").map { }
        }

    override suspend fun createAdoption(params: CreateAdoptionParams): Result<AdoptionPost> = try {
        val post = m09.create(params).toAdoptionPost()
        refreshPublished()
        Result.success(post)
    } catch (e: Exception) {
        M09AdoptionErrorMapper.failure(e)
    }

    override suspend fun updateAdoption(params: UpdateAdoptionParams): Result<AdoptionPost> = try {
        val post = m09.update(params).toAdoptionPost()
        refreshPublished()
        Result.success(post)
    } catch (e: Exception) {
        M09AdoptionErrorMapper.failure(e)
    }

    override suspend fun pauseAdoption(id: String): Result<AdoptionPost> =
        setStatusRemote(id, "PAUSED")

    override suspend fun resumeAdoption(id: String): Result<AdoptionPost> =
        setStatusRemote(id, "PUBLISHED")

    override suspend fun closeAdoption(id: String): Result<AdoptionPost> =
        setStatusRemote(id, "CLOSED")

    override suspend fun markAsAdopted(id: String): Result<AdoptionPost> = try {
        val post = m09.markAdopted(id).toAdoptionPost()
        refreshPublished()
        Result.success(post)
    } catch (e: Exception) {
        M09AdoptionErrorMapper.failure(e)
    }

    private suspend fun setStatusRemote(id: String, status: String): Result<AdoptionPost> = try {
        val post = m09.setStatus(id, status).toAdoptionPost()
        refreshPublished()
        Result.success(post)
    } catch (e: Exception) {
        M09AdoptionErrorMapper.failure(e)
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