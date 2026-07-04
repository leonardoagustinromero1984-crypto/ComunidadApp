package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.remote.firestore.PetFirestoreDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FirebasePetRepository(
    private val dataSource: PetFirestoreDataSource = PetFirestoreDataSource()
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

    override suspend fun createPet(pet: Pet): Result<String> = dataSource.createPet(pet)

    override suspend fun updatePet(pet: Pet): Result<Unit> = dataSource.updatePet(pet)

    override suspend fun deletePet(petId: String): Result<Unit> = dataSource.deletePet(petId)
}
