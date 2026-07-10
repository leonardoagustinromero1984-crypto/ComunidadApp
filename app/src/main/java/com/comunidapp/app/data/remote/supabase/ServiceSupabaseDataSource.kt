package com.comunidapp.app.data.remote.supabase

import com.comunidapp.app.data.model.BookingStatus
import com.comunidapp.app.data.model.FosterRequest
import com.comunidapp.app.data.model.PaymentStatus
import com.comunidapp.app.data.model.ServiceBooking
import com.comunidapp.app.data.model.ServiceCategory
import com.comunidapp.app.data.model.ServiceProfile
import com.comunidapp.app.data.model.Shelter
import com.comunidapp.app.data.model.User
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID
import kotlin.coroutines.coroutineContext

class ServiceSupabaseDataSource {

    suspend fun fetchServiceProfiles(
        category: ServiceCategory? = null,
        limit: Int = 100
    ): List<ServiceProfile> {
        return try {
            supabase.from(SupabaseTables.SERVICE_PROFILES)
                .select {
                    filter {
                        eq("active", true)
                        if (category != null) eq("category", category.name)
                    }
                    order("created_at", Order.DESCENDING)
                    limit(limit.toLong())
                }
                .decodeList<ServiceProfileRow>()
                .map(::parseServiceProfile)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun fetchMyServiceProfile(ownerId: String): ServiceProfile? {
        return try {
            supabase.from(SupabaseTables.SERVICE_PROFILES)
                .select {
                    filter { eq("owner_id", ownerId) }
                    limit(1)
                }
                .decodeList<ServiceProfileRow>()
                .firstOrNull()
                ?.let(::parseServiceProfile)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun upsertServiceProfile(profile: ServiceProfile): Result<String> {
        return try {
            val row = profile.toServiceProfileRow().copy(
                id = profile.id.ifBlank { UUID.randomUUID().toString() }
            )
            supabase.from(SupabaseTables.SERVICE_PROFILES).upsert(row)
            Result.success(row.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createBooking(booking: ServiceBooking): Result<String> {
        return try {
            val row = booking.toServiceBookingRow().copy(
                id = booking.id.ifBlank { UUID.randomUUID().toString() }
            )
            supabase.from(SupabaseTables.SERVICE_BOOKINGS).insert(row)
            Result.success(row.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchBookingsForProvider(providerId: String): List<ServiceBooking> {
        return try {
            supabase.from(SupabaseTables.SERVICE_BOOKINGS)
                .select {
                    filter { eq("provider_id", providerId) }
                    order("scheduled_at", Order.ASCENDING)
                }
                .decodeList<ServiceBookingRow>()
                .map(::parseServiceBooking)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun fetchBookingsForClient(clientId: String): List<ServiceBooking> {
        return try {
            supabase.from(SupabaseTables.SERVICE_BOOKINGS)
                .select {
                    filter { eq("client_id", clientId) }
                    order("scheduled_at", Order.ASCENDING)
                }
                .decodeList<ServiceBookingRow>()
                .map(::parseServiceBooking)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun updateBookingStatus(
        bookingId: String,
        status: BookingStatus,
        paymentStatus: PaymentStatus? = null
    ): Result<Unit> {
        return try {
            val payload = buildMap {
                put("status", status.name)
                if (paymentStatus != null) put("payment_status", paymentStatus.name)
            }
            supabase.from(SupabaseTables.SERVICE_BOOKINGS).update(payload) {
                filter { eq("id", bookingId) }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createFosterRequest(request: FosterRequest): Result<String> {
        return try {
            val row = request.toFosterRequestRow().copy(
                id = request.id.ifBlank { UUID.randomUUID().toString() }
            )
            supabase.from(SupabaseTables.FOSTER_REQUESTS).insert(row)
            Result.success(row.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun expressEventInterest(eventId: String, userId: String): Result<Unit> {
        return try {
            supabase.from(SupabaseTables.EVENT_INTERESTS).upsert(
                EventInterestRow(eventId = eventId, userId = userId)
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createShelter(owner: User, shelter: Shelter): Result<String> {
        return try {
            val row = shelter.copy(ownerId = owner.id).toShelterRow().copy(
                id = shelter.id.ifBlank { UUID.randomUUID().toString() }
            )
            supabase.from(SupabaseTables.SHELTERS_TABLE).insert(row)
            Result.success(row.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateShelter(shelter: Shelter): Result<Unit> {
        return try {
            supabase.from(SupabaseTables.SHELTERS_TABLE).upsert(shelter.toShelterRow())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addReputationPoints(
        userId: String,
        points: Int,
        badgeType: String? = null
    ): Result<Unit> {
        return try {
            supabase.postgrest.rpc(
                function = "add_reputation_points",
                parameters = buildJsonObject {
                    put("target_user_id", userId)
                    put("points", points)
                    if (badgeType != null) put("badge_type", badgeType)
                }
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeServiceProfiles(category: ServiceCategory? = null): Flow<List<ServiceProfile>> =
        pollingFlow { fetchServiceProfiles(category) }

    fun observeProviderBookings(providerId: String): Flow<List<ServiceBooking>> =
        pollingFlow { fetchBookingsForProvider(providerId) }

    private fun <T> pollingFlow(fetch: suspend () -> T): Flow<T> = flow {
        while (coroutineContext.isActive) {
            emit(fetch())
            delay(4_000)
        }
    }
}
