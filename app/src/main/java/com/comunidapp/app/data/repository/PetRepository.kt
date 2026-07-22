package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.mock.InMemoryDataStore
import com.comunidapp.app.data.remote.supabase.m08.PetAccessContext
import com.comunidapp.app.data.remote.supabase.m08.PetDuplicateCandidateRow
import com.comunidapp.app.data.remote.supabase.m08.PetStatusHistoryM08Row
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

interface PetRepository {
    fun observePets(): StateFlow<List<Pet>>
    fun observePetsForOwner(ownerId: String): Flow<List<Pet>>
    fun observePet(petId: String): Flow<Pet?>
    fun getPetsByOwner(ownerId: String): List<Pet>
    fun getPetById(petId: String): Pet?
    suspend fun fetchPetById(petId: String): Pet?
    suspend fun createPet(pet: Pet): Result<String>
    suspend fun updatePet(pet: Pet): Result<Unit>
    suspend fun deletePet(petId: String): Result<Unit>
    suspend fun getPetAccessContext(petId: String): Result<PetAccessContext>
    suspend fun setPetAvatarAsset(petId: String, assetId: String?): Result<Pet>

    /** M08 Etapa 6 — mark pet as DECEASED via RPC. */
    suspend fun markPetDeceased(petId: String, reason: String? = null): Result<Pet>

    /** M08 Etapa 6 — restore ARCHIVED pet to ACTIVE via RPC. */
    suspend fun restorePet(petId: String): Result<Pet>

    /** M08 Etapa 6 — status history (RLS SELECT), newest first. */
    suspend fun listStatusHistory(petId: String): Result<List<PetStatusHistoryM08Row>>

    /**
     * M08 Etapa 6 — private duplicate hints via scoped RPC.
     * Returns only pet_id + match_reason (no foreign PII).
     */
    suspend fun detectDuplicateCandidates(
        microchip: String? = null,
        name: String? = null
    ): Result<List<PetDuplicateCandidateRow>>
}

class MockPetRepository : PetRepository {
    override fun observePets(): StateFlow<List<Pet>> = InMemoryDataStore.pets

    override fun observePetsForOwner(ownerId: String): Flow<List<Pet>> =
        InMemoryDataStore.pets.map { pets -> pets.filter { it.ownerId == ownerId } }

    override fun observePet(petId: String): Flow<Pet?> =
        InMemoryDataStore.pets.map { pets -> pets.find { it.id == petId } }

    override fun getPetsByOwner(ownerId: String): List<Pet> =
        InMemoryDataStore.pets.value.filter { it.ownerId == ownerId }

    override fun getPetById(petId: String): Pet? = InMemoryDataStore.getPetById(petId)

    override suspend fun fetchPetById(petId: String): Pet? = getPetById(petId)

    override suspend fun createPet(pet: Pet): Result<String> =
        InMemoryDataStore.addPet(pet)

    override suspend fun updatePet(pet: Pet): Result<Unit> =
        InMemoryDataStore.updatePet(pet)

    override suspend fun deletePet(petId: String): Result<Unit> =
        InMemoryDataStore.deletePet(petId)

    override suspend fun getPetAccessContext(petId: String): Result<PetAccessContext> {
        val pet = getPetById(petId)
            ?: return Result.failure(IllegalStateException("PET_NOT_FOUND"))
        val isOwner = pet.ownerId != null
        return Result.success(
            PetAccessContext(
                petId = petId,
                relationCode = if (isOwner) "PRINCIPAL" else "NONE",
                principalPersonId = pet.ownerId,
                principalOrganizationId = null,
                capabilities = if (isOwner) {
                    listOf(
                        "pet.read",
                        "pet.update",
                        "pet.manage_health",
                        "pet.manage_media",
                        "pet.archive",
                        "pet.mark_deceased"
                    )
                } else {
                    emptyList()
                },
                canRead = isOwner,
                canUpdate = isOwner,
                canManageHealth = isOwner,
                canManageMedia = isOwner,
                canManageResponsibilities = isOwner,
                canManageAuthorizations = isOwner,
                canInitiateTransfer = isOwner,
                canAcceptTransfer = false,
                canCancelTransfer = isOwner,
                canArchive = isOwner,
                canRestore = isOwner,
                canMarkDeceased = isOwner,
                canViewHistory = isOwner
            )
        )
    }

    override suspend fun setPetAvatarAsset(petId: String, assetId: String?): Result<Pet> {
        val pet = getPetById(petId)
            ?: return Result.failure(IllegalStateException("PET_NOT_FOUND"))
        // Mock-only: surface asset id via photoUrl for in-memory display.
        val updated = pet.copy(
            photoUrl = assetId ?: pet.photoUrl,
            avatarFileAssetId = assetId
        )
        return InMemoryDataStore.updatePet(updated).map { updated }
    }

    override suspend fun markPetDeceased(petId: String, reason: String?): Result<Pet> {
        val pet = getPetById(petId)
            ?: return Result.failure(IllegalStateException("PET_NOT_FOUND"))
        if (pet.status == "DECEASED") {
            return Result.failure(IllegalStateException("PET_ALREADY_DECEASED"))
        }
        val updated = pet.copy(
            status = "DECEASED",
            deceasedAt = System.currentTimeMillis(),
            archivedAt = null
        )
        return InMemoryDataStore.updatePet(updated).map { updated }
    }

    override suspend fun restorePet(petId: String): Result<Pet> {
        val pet = getPetById(petId)
            ?: return Result.failure(IllegalStateException("PET_NOT_FOUND"))
        if (pet.status == "DECEASED") {
            return Result.failure(IllegalStateException("PET_DECEASED_CANNOT_RESTORE"))
        }
        if (pet.status != "ARCHIVED") {
            return Result.failure(IllegalStateException("PET_NOT_ARCHIVED"))
        }
        val updated = pet.copy(status = "ACTIVE", archivedAt = null)
        return InMemoryDataStore.updatePet(updated).map { updated }
    }

    override suspend fun listStatusHistory(petId: String): Result<List<PetStatusHistoryM08Row>> {
        if (getPetById(petId) == null) {
            return Result.failure(IllegalStateException("PET_NOT_FOUND"))
        }
        return Result.success(emptyList())
    }

    override suspend fun detectDuplicateCandidates(
        microchip: String?,
        name: String?
    ): Result<List<PetDuplicateCandidateRow>> {
        val chip = microchip?.trim()?.takeIf { it.isNotEmpty() }
        val nm = name?.trim()?.takeIf { it.isNotEmpty() }
        if (chip == null && nm == null) return Result.success(emptyList())
        val matches = InMemoryDataStore.pets.value.mapNotNull { pet ->
            val reason = when {
                chip != null && pet.microchipId.equals(chip, ignoreCase = true) -> "MICROCHIP"
                nm != null && pet.name.equals(nm, ignoreCase = true) -> "NAME"
                else -> null
            }
            reason?.let { PetDuplicateCandidateRow(petId = pet.id, matchReason = it) }
        }
        return Result.success(matches)
    }
}
