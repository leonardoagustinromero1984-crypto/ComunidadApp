package com.comunidapp.app.data.repository

import com.comunidapp.app.data.mock.InMemoryDataStore
import com.comunidapp.app.data.model.BookingStatus
import com.comunidapp.app.data.model.FosterRequest
import com.comunidapp.app.data.model.PaymentStatus
import com.comunidapp.app.data.model.ServiceBooking
import com.comunidapp.app.data.model.ServiceCategory
import com.comunidapp.app.data.model.ServiceProfile
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.remote.supabase.ServiceSupabaseDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

interface ServiceRepository {
    fun observeServices(category: ServiceCategory? = null): StateFlow<List<ServiceProfile>>
    fun getServiceById(id: String): ServiceProfile?
    suspend fun fetchMyServiceProfile(ownerId: String): ServiceProfile?
    suspend fun upsertServiceProfile(profile: ServiceProfile): Result<String>
    suspend fun createBooking(client: User, booking: ServiceBooking): Result<String>
    fun observeProviderBookings(providerId: String): StateFlow<List<ServiceBooking>>
    suspend fun fetchClientBookings(clientId: String): List<ServiceBooking>
    suspend fun updateBookingStatus(
        bookingId: String,
        status: BookingStatus,
        paymentStatus: PaymentStatus? = null
    ): Result<Unit>
    suspend fun createFosterRequest(request: FosterRequest): Result<String>
    suspend fun expressEventInterest(eventId: String, userId: String): Result<Unit>
}

class MockServiceRepository : ServiceRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun observeServices(category: ServiceCategory?): StateFlow<List<ServiceProfile>> {
        if (category == null) return InMemoryDataStore.serviceProfiles
        return InMemoryDataStore.serviceProfiles
            .map { list -> list.filter { it.category == category } }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())
    }

    override fun getServiceById(id: String): ServiceProfile? =
        InMemoryDataStore.getServiceById(id)

    override suspend fun fetchMyServiceProfile(ownerId: String): ServiceProfile? =
        InMemoryDataStore.getServiceByOwner(ownerId)

    override suspend fun upsertServiceProfile(profile: ServiceProfile): Result<String> =
        InMemoryDataStore.upsertServiceProfile(profile)

    override suspend fun createBooking(client: User, booking: ServiceBooking): Result<String> =
        InMemoryDataStore.addServiceBooking(
            booking.copy(clientId = client.id, clientName = client.name)
        )

    override fun observeProviderBookings(providerId: String): StateFlow<List<ServiceBooking>> =
        InMemoryDataStore.observeProviderBookings(providerId)

    override suspend fun fetchClientBookings(clientId: String): List<ServiceBooking> =
        InMemoryDataStore.getClientBookings(clientId)

    override suspend fun updateBookingStatus(
        bookingId: String,
        status: BookingStatus,
        paymentStatus: PaymentStatus?
    ): Result<Unit> = InMemoryDataStore.updateBookingStatus(bookingId, status, paymentStatus)

    override suspend fun createFosterRequest(request: FosterRequest): Result<String> =
        InMemoryDataStore.addFosterRequest(request)

    override suspend fun expressEventInterest(eventId: String, userId: String): Result<Unit> =
        Result.success(Unit)
}

class SupabaseServiceRepository(
    private val dataSource: ServiceSupabaseDataSource = ServiceSupabaseDataSource()
) : ServiceRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _services = MutableStateFlow<List<ServiceProfile>>(emptyList())
    private val providerBookingFlows = mutableMapOf<String, MutableStateFlow<List<ServiceBooking>>>()

    init {
        scope.launch {
            dataSource.observeServiceProfiles().collect { _services.value = it }
        }
    }

    override fun observeServices(category: ServiceCategory?): StateFlow<List<ServiceProfile>> {
        if (category == null) return _services.asStateFlow()
        return _services
            .map { list -> list.filter { it.category == category } }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())
    }

    override fun getServiceById(id: String): ServiceProfile? =
        _services.value.find { it.id == id }

    override suspend fun fetchMyServiceProfile(ownerId: String): ServiceProfile? =
        dataSource.fetchMyServiceProfile(ownerId)

    override suspend fun upsertServiceProfile(profile: ServiceProfile): Result<String> {
        val result = dataSource.upsertServiceProfile(profile)
        if (result.isSuccess) {
            _services.value = dataSource.fetchServiceProfiles()
        }
        return result
    }

    override suspend fun createBooking(client: User, booking: ServiceBooking): Result<String> =
        dataSource.createBooking(booking.copy(clientId = client.id, clientName = client.name))

    override fun observeProviderBookings(providerId: String): StateFlow<List<ServiceBooking>> {
        return providerBookingFlows.getOrPut(providerId) {
            val flow = MutableStateFlow<List<ServiceBooking>>(emptyList())
            scope.launch {
                dataSource.observeProviderBookings(providerId).collect { flow.value = it }
            }
            flow
        }.asStateFlow()
    }

    override suspend fun fetchClientBookings(clientId: String): List<ServiceBooking> =
        dataSource.fetchBookingsForClient(clientId)

    override suspend fun updateBookingStatus(
        bookingId: String,
        status: BookingStatus,
        paymentStatus: PaymentStatus?
    ): Result<Unit> = dataSource.updateBookingStatus(bookingId, status, paymentStatus)

    override suspend fun createFosterRequest(request: FosterRequest): Result<String> =
        dataSource.createFosterRequest(request)

    override suspend fun expressEventInterest(eventId: String, userId: String): Result<Unit> =
        dataSource.expressEventInterest(eventId, userId)
}
