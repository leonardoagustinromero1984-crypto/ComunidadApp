package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.mock.InMemoryDataStore
import kotlinx.coroutines.flow.StateFlow

interface PetRepository {
    fun observePets(): StateFlow<List<Pet>>
    fun getPetsByOwner(ownerId: String): List<Pet>
    fun getPetById(petId: String): Pet?
}

class MockPetRepository : PetRepository {
    override fun observePets(): StateFlow<List<Pet>> = InMemoryDataStore.pets

    override fun getPetsByOwner(ownerId: String): List<Pet> =
        InMemoryDataStore.pets.value.filter { it.ownerId == ownerId }

    override fun getPetById(petId: String): Pet? = InMemoryDataStore.getPetById(petId)
}
