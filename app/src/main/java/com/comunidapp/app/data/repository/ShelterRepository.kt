package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.Shelter
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.mock.InMemoryDataStore
import com.comunidapp.app.data.remote.supabase.CommunitySupabaseDataSource
import com.comunidapp.app.data.remote.supabase.ServiceSupabaseDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

interface ShelterRepository {
    fun observeShelters(): StateFlow<List<Shelter>>
    fun getShelterById(id: String): Shelter?
    suspend fun createShelter(owner: User, shelter: Shelter): Result<String>
    suspend fun updateShelter(shelter: Shelter): Result<Unit>
}

class MockShelterRepository : ShelterRepository {
    override fun observeShelters(): StateFlow<List<Shelter>> = InMemoryDataStore.shelters

    override fun getShelterById(id: String): Shelter? = InMemoryDataStore.getShelterById(id)

    override suspend fun createShelter(owner: User, shelter: Shelter): Result<String> =
        InMemoryDataStore.addShelter(shelter.copy(ownerId = owner.id))

    override suspend fun updateShelter(shelter: Shelter): Result<Unit> =
        InMemoryDataStore.updateShelter(shelter)
}

class SupabaseShelterRepository(
    private val dataSource: CommunitySupabaseDataSource = CommunitySupabaseDataSource(),
    private val serviceDataSource: ServiceSupabaseDataSource = ServiceSupabaseDataSource()
) : ShelterRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _shelters = MutableStateFlow<List<Shelter>>(emptyList())

    init {
        scope.launch {
            dataSource.observeShelters().collect { _shelters.value = it }
        }
    }

    override fun observeShelters(): StateFlow<List<Shelter>> = _shelters.asStateFlow()

    override fun getShelterById(id: String): Shelter? = _shelters.value.find { it.id == id }

    override suspend fun createShelter(owner: User, shelter: Shelter): Result<String> {
        val result = serviceDataSource.createShelter(owner, shelter)
        if (result.isSuccess) {
            _shelters.value = dataSource.fetchShelters()
        }
        return result
    }

    override suspend fun updateShelter(shelter: Shelter): Result<Unit> {
        val result = serviceDataSource.updateShelter(shelter)
        if (result.isSuccess) {
            _shelters.value = dataSource.fetchShelters()
        }
        return result
    }
}
