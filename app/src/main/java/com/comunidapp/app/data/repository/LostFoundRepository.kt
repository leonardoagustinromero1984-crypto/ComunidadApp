package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.LostFoundPost
import com.comunidapp.app.data.model.LostFoundStatus
import com.comunidapp.app.data.model.LostFoundType
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.mock.InMemoryDataStore
import kotlinx.coroutines.flow.StateFlow

interface LostFoundRepository {
    fun observeLostFoundPosts(): StateFlow<List<LostFoundPost>>
    fun getFilteredLostFound(
        type: LostFoundType? = null,
        species: PetSpecies? = null,
        location: String? = null,
        status: LostFoundStatus? = LostFoundStatus.ACTIVE
    ): List<LostFoundPost>
    suspend fun addLostFoundPost(post: LostFoundPost): Result<String>
    suspend fun updateLostFoundPost(post: LostFoundPost): Result<Unit>
    suspend fun updateStatus(id: String, status: LostFoundStatus): Result<Unit>
}

class MockLostFoundRepository : LostFoundRepository {
    override fun observeLostFoundPosts(): StateFlow<List<LostFoundPost>> =
        InMemoryDataStore.lostFoundPosts

    override fun getFilteredLostFound(
        type: LostFoundType?,
        species: PetSpecies?,
        location: String?,
        status: LostFoundStatus?
    ): List<LostFoundPost> = InMemoryDataStore.lostFoundPosts.value.filter { post ->
        (type == null || post.type == type) &&
            (species == null || post.species == species) &&
            (location.isNullOrBlank() || post.location.contains(location, ignoreCase = true)) &&
            (status == null || post.status == status)
    }

    override suspend fun addLostFoundPost(post: LostFoundPost): Result<String> {
        val id = post.id.ifBlank { "lf_${System.currentTimeMillis()}" }
        InMemoryDataStore.addLostFoundPost(post.copy(id = id))
        return Result.success(id)
    }

    override suspend fun updateLostFoundPost(post: LostFoundPost): Result<Unit> {
        InMemoryDataStore.updateLostFoundPost(post)
        return Result.success(Unit)
    }

    override suspend fun updateStatus(id: String, status: LostFoundStatus): Result<Unit> {
        val existing = InMemoryDataStore.lostFoundPosts.value.find { it.id == id } ?: return Result.failure(
            NoSuchElementException("Publicación no encontrada")
        )
        InMemoryDataStore.addLostFoundPost(existing.copy(status = status))
        return Result.success(Unit)
    }
}
