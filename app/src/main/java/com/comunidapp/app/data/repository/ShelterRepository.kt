package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.Shelter
import com.comunidapp.app.data.mock.InMemoryDataStore
import com.comunidapp.app.data.remote.supabase.CommunitySupabaseDataSource
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
}

class MockShelterRepository : ShelterRepository {
    override fun observeShelters(): StateFlow<List<Shelter>> = InMemoryDataStore.shelters

    override fun getShelterById(id: String): Shelter? = InMemoryDataStore.getShelterById(id)
}

class SupabaseShelterRepository(
    private val dataSource: CommunitySupabaseDataSource = CommunitySupabaseDataSource()
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
}
