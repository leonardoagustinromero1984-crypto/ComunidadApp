package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.Shelter
import com.comunidapp.app.data.mock.InMemoryDataStore
import kotlinx.coroutines.flow.StateFlow

interface ShelterRepository {
    fun observeShelters(): StateFlow<List<Shelter>>
    fun getShelterById(id: String): Shelter?
}

class MockShelterRepository : ShelterRepository {
    override fun observeShelters(): StateFlow<List<Shelter>> = InMemoryDataStore.shelters

    override fun getShelterById(id: String): Shelter? = InMemoryDataStore.getShelterById(id)
}
