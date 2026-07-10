package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.AdoptionRequest
import com.comunidapp.app.data.model.AdoptionRequestStatus
import com.comunidapp.app.data.mock.InMemoryDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface AdoptionRequestRepository {
    fun observeRequestsForPublisher(publisherId: String): Flow<List<AdoptionRequest>>
    fun observeRequestsForAdoption(adoptionId: String): Flow<List<AdoptionRequest>>
    suspend fun submitRequest(request: AdoptionRequest): Result<String>
    suspend fun updateRequestStatus(id: String, status: AdoptionRequestStatus): Result<Unit>
    suspend fun scheduleInterview(id: String, dateText: String, notes: String): Result<Unit>
}

class MockAdoptionRequestRepository : AdoptionRequestRepository {
    override fun observeRequestsForPublisher(publisherId: String): Flow<List<AdoptionRequest>> =
        InMemoryDataStore.observeAdoptionRequestsForPublisher(publisherId)

    override fun observeRequestsForAdoption(adoptionId: String): Flow<List<AdoptionRequest>> =
        InMemoryDataStore.observeAdoptionRequestsForAdoption(adoptionId)

    override suspend fun submitRequest(request: AdoptionRequest): Result<String> =
        InMemoryDataStore.addAdoptionRequest(request)

    override suspend fun updateRequestStatus(id: String, status: AdoptionRequestStatus): Result<Unit> =
        InMemoryDataStore.updateAdoptionRequestStatus(id, status)

    override suspend fun scheduleInterview(id: String, dateText: String, notes: String): Result<Unit> =
        InMemoryDataStore.scheduleAdoptionInterview(id, dateText, notes)
}
