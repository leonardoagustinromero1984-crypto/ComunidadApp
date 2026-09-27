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
    suspend fun claimLostFound(alertId: String): Result<LostFoundClaimResult> =
        Result.failure(UnsupportedOperationException("CLAIM_UNAVAILABLE"))

    suspend fun attachLostFoundPhoto(alertId: String, assetId: String): Result<Unit> =
        Result.success(Unit)

    suspend fun assertFoundMightBeMine(foundId: String, lostId: String): Result<String> =
        Result.failure(UnsupportedOperationException("OWNER_ASSERT_UNAVAILABLE"))

    suspend fun listFoundMatchCandidates(foundId: String): Result<List<LostFoundMatchCandidate>> =
        Result.success(emptyList())

    suspend fun confirmFoundOwnerMatch(candidateId: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("OWNER_CONFIRM_UNAVAILABLE"))

    suspend fun rejectFoundMightBeMine(foundId: String, lostId: String): Result<Unit> =
        Result.success(Unit)

    suspend fun rejectFoundOwnerMatch(candidateId: String): Result<Unit> =
        Result.success(Unit)

    suspend fun markLostFoundInCare(alertId: String): Result<Unit> =
        Result.success(Unit)
}

data class LostFoundMatchCandidate(
    val id: String,
    val lostAlertId: String?,
    val assertedBy: String?,
    val status: String,
    val score: Double? = null,
    val matchReason: String? = null
)

data class LostFoundClaimResult(
    val alertId: String,
    val petId: String?,
    val status: String,
    val alreadyTaken: Boolean = false
)

data class ResponderBaseSnapshot(
    val hasBaseLocation: Boolean,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null
)

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

    override suspend fun claimLostFound(alertId: String): Result<LostFoundClaimResult> {
        val existing = InMemoryDataStore.lostFoundPosts.value.find { it.id == alertId }
            ?: return Result.failure(NoSuchElementException("ALERT_NOT_FOUND"))
        if (existing.status != LostFoundStatus.ACTIVE) {
            return Result.success(
                LostFoundClaimResult(alertId, existing.petId, existing.status.name, alreadyTaken = true)
            )
        }
        InMemoryDataStore.addLostFoundPost(existing.copy(status = LostFoundStatus.CLAIMED))
        return Result.success(LostFoundClaimResult(alertId, existing.petId, "CLAIMED"))
    }
}
