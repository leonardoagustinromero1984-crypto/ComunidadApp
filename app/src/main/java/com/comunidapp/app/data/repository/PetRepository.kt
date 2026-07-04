package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.mock.InMemoryDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

interface PetRepository {
    fun observePets(): StateFlow<List<Pet>>
    fun observePet(petId: String): Flow<Pet?>
    fun getPetsByOwner(ownerId: String): List<Pet>
    fun getPetById(petId: String): Pet?
    suspend fun createPet(pet: Pet): Result<String>
    suspend fun updatePet(pet: Pet): Result<Unit>
    suspend fun deletePet(petId: String): Result<Unit>
}

class MockPetRepository : PetRepository {
    override fun observePets(): StateFlow<List<Pet>> = InMemoryDataStore.pets

    override fun observePet(petId: String): Flow<Pet?> =
        InMemoryDataStore.pets.map { pets -> pets.find { it.id == petId } }

    override fun getPetsByOwner(ownerId: String): List<Pet> =
        InMemoryDataStore.pets.value.filter { it.ownerId == ownerId }

    override fun getPetById(petId: String): Pet? = InMemoryDataStore.getPetById(petId)

    override suspend fun createPet(pet: Pet): Result<String> =
        InMemoryDataStore.addPet(pet)

    override suspend fun updatePet(pet: Pet): Result<Unit> =
        InMemoryDataStore.updatePet(pet)

    override suspend fun deletePet(petId: String): Result<Unit> =
        InMemoryDataStore.deletePet(petId)
}
