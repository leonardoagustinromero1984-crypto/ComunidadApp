package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.mock.InMemoryDataStore
import com.comunidapp.app.data.remote.supabase.m08.PetAccessContext
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
}
