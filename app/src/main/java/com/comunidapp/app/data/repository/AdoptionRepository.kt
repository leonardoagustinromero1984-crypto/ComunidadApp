package com.comunidapp.app.data.repository

import com.comunidapp.app.data.mock.InMemoryDataStore
import com.comunidapp.app.data.model.AdoptionPost
import com.comunidapp.app.data.model.AdoptionStatus
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.remote.supabase.m09.CreateAdoptionParams
import com.comunidapp.app.data.remote.supabase.m09.M09AdoptionErrorMapper
import com.comunidapp.app.data.remote.supabase.m09.M09AdoptionException
import com.comunidapp.app.data.remote.supabase.m09.UpdateAdoptionParams
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

interface AdoptionRepository {
    fun observeAdoptionPosts(): StateFlow<List<AdoptionPost>>
    fun observePublishedAdoptions(): Flow<List<AdoptionPost>>
    fun observeMyAdoptions(publisherId: String = ""): Flow<List<AdoptionPost>>
    fun getAdoptionPostById(id: String): AdoptionPost?
    suspend fun getAdoptionById(id: String): Result<AdoptionPost>
    fun getFilteredAdoptions(
        location: String? = null,
        sex: PetSex? = null,
        minAge: Int? = null,
        maxAge: Int? = null,
        size: PetSize? = null,
        status: AdoptionStatus? = AdoptionStatus.PUBLISHED
    ): List<AdoptionPost>
    fun getAdoptionsByShelter(shelterId: String): List<AdoptionPost>
    suspend fun addAdoptionPost(post: AdoptionPost): Result<String>
    suspend fun updateAdoptionPost(post: AdoptionPost): Result<Unit>
    suspend fun updateAdoptionStatus(id: String, status: AdoptionStatus): Result<Unit>

    suspend fun createAdoption(params: CreateAdoptionParams): Result<AdoptionPost>
    suspend fun updateAdoption(params: UpdateAdoptionParams): Result<AdoptionPost>
    suspend fun pauseAdoption(id: String): Result<AdoptionPost>
    suspend fun resumeAdoption(id: String): Result<AdoptionPost>
    suspend fun closeAdoption(id: String): Result<AdoptionPost>
    suspend fun markAsAdopted(id: String): Result<AdoptionPost>
}

/**
 * In-memory M09 adoption rules for mock/local and unit tests.
 */
class MockAdoptionRepository(
    private val actorUserId: () -> String? = { null },
    private val canManagePet: (petId: String, userId: String) -> Boolean = { _, _ -> true },
    private val statusHistory: MutableList<Pair<String, String>> = mutableListOf()
) : AdoptionRepository {

    /** Test hook: (petId, reason) history entries from markAsAdopted. */
    fun statusHistoryEntries(): List<Pair<String, String>> = statusHistory.toList()

    override fun observeAdoptionPosts(): StateFlow<List<AdoptionPost>> =
        InMemoryDataStore.adoptionPosts

    override fun observePublishedAdoptions(): Flow<List<AdoptionPost>> =
        InMemoryDataStore.adoptionPosts.map { list ->
            list.filter { it.status == AdoptionStatus.PUBLISHED }
        }

    override fun observeMyAdoptions(publisherId: String): Flow<List<AdoptionPost>> =
        InMemoryDataStore.adoptionPosts.map { posts ->
            val uid = publisherId.ifBlank { actorUserId().orEmpty() }
            posts.filter {
                it.publisherId == uid || it.shelterId == uid ||
                    (!it.petId.isNullOrBlank() && uid.isNotBlank() && canManagePet(it.petId!!, uid))
            }
        }

    override fun getAdoptionPostById(id: String): AdoptionPost? =
        if (id.isBlank()) null else InMemoryDataStore.getAdoptionPostById(id)

    override suspend fun getAdoptionById(id: String): Result<AdoptionPost> {
        if (id.isBlank()) {
            return fail("ADOPTION_NOT_FOUND")
        }
        val post = InMemoryDataStore.getAdoptionPostById(id)
            ?: return fail("ADOPTION_NOT_FOUND")
        return Result.success(post)
    }

    override fun getFilteredAdoptions(
        location: String?,
        sex: PetSex?,
        minAge: Int?,
        maxAge: Int?,
        size: PetSize?,
        status: AdoptionStatus?
    ): List<AdoptionPost> = InMemoryDataStore.adoptionPosts.value.filter { post ->
        (location.isNullOrBlank() || post.location.contains(location, ignoreCase = true)) &&
            (sex == null || post.sex == sex) &&
            (minAge == null || post.ageYears >= minAge) &&
            (maxAge == null || post.ageYears <= maxAge) &&
            (size == null || post.size == size) &&
            (status == null || post.status == status)
    }

    override fun getAdoptionsByShelter(shelterId: String): List<AdoptionPost> =
        InMemoryDataStore.getAdoptionsByShelter(shelterId)

    override suspend fun addAdoptionPost(post: AdoptionPost): Result<String> {
        if (!post.petId.isNullOrBlank()) {
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
        // Legacy publish path (sin petId): solo mock/local hasta formulario M09.
        InMemoryDataStore.addAdoptionPost(
            post.copy(status = if (post.status == AdoptionStatus.DRAFT) AdoptionStatus.DRAFT else AdoptionStatus.PUBLISHED)
        )
        return Result.success(post.id.ifBlank { "adopt_${System.currentTimeMillis()}" })
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
            AdoptionStatus.DRAFT -> setStatus(id, AdoptionStatus.DRAFT).map { }
        }

    override suspend fun createAdoption(params: CreateAdoptionParams): Result<AdoptionPost> {
        val actor = actorUserId()
        if (actor.isNullOrBlank()) return fail("NOT_AUTHENTICATED")
        if (params.petId.isBlank()) return fail("PET_NOT_FOUND")
        val pet = InMemoryDataStore.getPetById(params.petId) ?: return fail("PET_NOT_FOUND")
        when (pet.status.uppercase()) {
            "DECEASED", "ARCHIVED" -> return fail("PET_NOT_ADOPTABLE")
            "ACTIVE" -> Unit
            else -> return fail("PET_NOT_ADOPTABLE")
        }
        if (!canManagePet(params.petId, actor)) return fail("FORBIDDEN")
        val open = InMemoryDataStore.adoptionPosts.value.any {
            it.petId == params.petId && AdoptionStatus.isOpen(it.status)
        }
        if (open) return fail("ADOPTION_ALREADY_EXISTS")
        if (params.title.isBlank()) return fail("ADOPTION_TITLE_REQUIRED")
        if (params.description.isBlank()) return fail("ADOPTION_DESCRIPTION_REQUIRED")

        val now = System.currentTimeMillis()
        val status = if (params.publish) AdoptionStatus.PUBLISHED else AdoptionStatus.DRAFT
        val post = AdoptionPost(
            id = "adopt_${now}",
            petId = pet.id,
            publisherId = actor,
            shelterName = "Usuario",
            title = params.title.trim(),
            name = pet.name,
            photoUrl = pet.photoUrl,
            species = pet.species,
            sex = pet.sex,
            ageYears = pet.ageYears,
            ageMonths = pet.ageMonths,
            size = pet.size,
            location = params.locationText.ifBlank { pet.locationText.orEmpty() },
            description = params.description.trim(),
            requirements = params.requirements,
            status = status,
            publishedAt = if (params.publish) now else null,
            createdAt = now,
            updatedAt = now
        )
        InMemoryDataStore.addAdoptionPost(post)
        return Result.success(InMemoryDataStore.getAdoptionPostById(post.id) ?: post)
    }

    override suspend fun updateAdoption(params: UpdateAdoptionParams): Result<AdoptionPost> {
        val actor = actorUserId() ?: return fail("NOT_AUTHENTICATED")
        if (params.adoptionId.isBlank()) return fail("ADOPTION_NOT_FOUND")
        val existing = InMemoryDataStore.getAdoptionPostById(params.adoptionId)
            ?: return fail("ADOPTION_NOT_FOUND")
        if (existing.status == AdoptionStatus.CLOSED || existing.status == AdoptionStatus.ADOPTED) {
            return fail("ADOPTION_NOT_EDITABLE")
        }
        if (!isOwnerOrManager(existing, actor)) return fail("FORBIDDEN")
        if (params.title.isBlank() || params.description.isBlank()) {
            return fail("ADOPTION_NOT_EDITABLE")
        }
        val updated = existing.copy(
            title = params.title.trim(),
            description = params.description.trim(),
            requirements = params.requirements,
            location = params.locationText.ifBlank { existing.location },
            updatedAt = System.currentTimeMillis()
        )
        InMemoryDataStore.updateAdoptionPost(updated)
        return Result.success(updated)
    }

    override suspend fun pauseAdoption(id: String): Result<AdoptionPost> {
        val existing = requireEditable(id) ?: return lastFail
        if (existing.status == AdoptionStatus.PAUSED) return Result.success(existing)
        if (existing.status != AdoptionStatus.PUBLISHED) return fail("ADOPTION_NOT_EDITABLE")
        return setStatus(id, AdoptionStatus.PAUSED)
    }

    override suspend fun resumeAdoption(id: String): Result<AdoptionPost> {
        val existing = requireEditable(id) ?: return lastFail
        if (existing.status == AdoptionStatus.PUBLISHED) return Result.success(existing)
        if (existing.status != AdoptionStatus.DRAFT && existing.status != AdoptionStatus.PAUSED) {
            return fail("ADOPTION_NOT_EDITABLE")
        }
        return setStatus(id, AdoptionStatus.PUBLISHED)
    }

    override suspend fun closeAdoption(id: String): Result<AdoptionPost> {
        val existing = getOrFail(id) ?: return lastFail
        if (existing.status == AdoptionStatus.CLOSED) return Result.success(existing)
        if (existing.status == AdoptionStatus.ADOPTED) return fail("ADOPTION_ALREADY_ADOPTED")
        return setStatus(id, AdoptionStatus.CLOSED)
    }

    override suspend fun markAsAdopted(id: String): Result<AdoptionPost> {
        val actor = actorUserId() ?: return fail("NOT_AUTHENTICATED")
        if (id.isBlank()) return fail("ADOPTION_NOT_FOUND")
        val existing = InMemoryDataStore.getAdoptionPostById(id) ?: return fail("ADOPTION_NOT_FOUND")
        if (existing.status == AdoptionStatus.ADOPTED) return Result.success(existing)
        if (existing.status == AdoptionStatus.CLOSED) return fail("ADOPTION_ALREADY_CLOSED")
        if (!isOwnerOrManager(existing, actor)) return fail("FORBIDDEN")

        val now = System.currentTimeMillis()
        val updated = existing.copy(status = AdoptionStatus.ADOPTED, updatedAt = now)
        InMemoryDataStore.updateAdoptionPost(updated)

        val petId = existing.petId
        if (!petId.isNullOrBlank()) {
            val pet = InMemoryDataStore.getPetById(petId)
            if (pet != null && pet.status.equals("ACTIVE", ignoreCase = true)) {
                InMemoryDataStore.updatePet(
                    pet.copy(status = "ARCHIVED", archivedAt = now, updatedAt = now)
                )
                statusHistory += petId to "ADOPTED"
            }
        }
        return Result.success(updated)
    }

    private var lastFail: Result<AdoptionPost> = fail("ADOPTION_NOT_FOUND")

    private fun getOrFail(id: String): AdoptionPost? {
        if (id.isBlank()) {
            lastFail = fail("ADOPTION_NOT_FOUND")
            return null
        }
        val post = InMemoryDataStore.getAdoptionPostById(id)
        if (post == null) {
            lastFail = fail("ADOPTION_NOT_FOUND")
            return null
        }
        val actor = actorUserId()
        if (actor.isNullOrBlank()) {
            lastFail = fail("NOT_AUTHENTICATED")
            return null
        }
        if (!isOwnerOrManager(post, actor)) {
            lastFail = fail("FORBIDDEN")
            return null
        }
        return post
    }

    private fun requireEditable(id: String): AdoptionPost? {
        val post = getOrFail(id) ?: return null
        if (post.status == AdoptionStatus.CLOSED || post.status == AdoptionStatus.ADOPTED) {
            lastFail = fail("ADOPTION_NOT_EDITABLE")
            return null
        }
        return post
    }

    private suspend fun setStatus(id: String, status: AdoptionStatus): Result<AdoptionPost> {
        val existing = getOrFail(id) ?: return lastFail
        if (existing.status == status) return Result.success(existing)
        val now = System.currentTimeMillis()
        val updated = existing.copy(
            status = status,
            publishedAt = if (status == AdoptionStatus.PUBLISHED) {
                existing.publishedAt ?: now
            } else {
                existing.publishedAt
            },
            updatedAt = now
        )
        InMemoryDataStore.updateAdoptionPost(updated)
        return Result.success(updated)
    }

    private fun isOwnerOrManager(post: AdoptionPost, actor: String): Boolean {
        if (post.publisherId == actor) return true
        val petId = post.petId ?: return false
        return canManagePet(petId, actor)
    }

    private fun <T> fail(code: String): Result<T> =
        Result.failure(M09AdoptionException(code, M09AdoptionErrorMapper.userMessage(code)))
}
