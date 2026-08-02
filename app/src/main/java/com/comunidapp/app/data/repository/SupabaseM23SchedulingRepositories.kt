package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.M23AvailabilityException
import com.comunidapp.app.data.model.M23AvailabilityRule
import com.comunidapp.app.data.model.M23Booking
import com.comunidapp.app.data.model.M23BookingCancellation
import com.comunidapp.app.data.model.M23BookingPolicy
import com.comunidapp.app.data.model.M23BookingRescheduleRequest
import com.comunidapp.app.data.model.M23BookingSummary
import com.comunidapp.app.data.model.M23SlotPage
import com.comunidapp.app.data.model.M23SlotQuery
import com.comunidapp.app.data.remote.supabase.m23.M23BookingErrorMapper
import com.comunidapp.app.data.remote.supabase.m23.SupabaseM23RemoteDataSource
import com.comunidapp.app.data.remote.supabase.m23.toM23AvailabilityException
import com.comunidapp.app.data.remote.supabase.m23.toM23AvailabilityRule
import com.comunidapp.app.data.remote.supabase.m23.toM23Booking
import com.comunidapp.app.data.remote.supabase.m23.toM23SlotPage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class SupabaseM23AvailabilityRepository(
    private val remote: SupabaseM23RemoteDataSource = SupabaseM23RemoteDataSource()
) : M23AvailabilityRepository {
    override fun observeRules(providerId: String): Flow<List<M23AvailabilityRule>> = flow {
        emit(runCatching { remote.listRules(providerId).map { it.toM23AvailabilityRule() } }.getOrElse { emptyList() })
    }

    override fun observeSlots(query: M23SlotQuery): Flow<M23SlotPage> = flow {
        emit(runCatching {
            remote.publicSlots(query.providerId, query.offeringId, query.from.toString(), query.to.toString())
                .toM23SlotPage(query.providerId, query.offeringId, query.zoneId)
        }.getOrElse { M23SlotPage(emptyList()) })
    }

    override suspend fun upsertRule(rule: M23AvailabilityRule): Result<M23AvailabilityRule> = try {
        Result.success(remote.createRule(rule).toM23AvailabilityRule())
    } catch (error: Throwable) {
        M23BookingErrorMapper.failure(error)
    }

    override suspend fun upsertException(exception: M23AvailabilityException): Result<M23AvailabilityException> = try {
        Result.success(remote.createException(exception).toM23AvailabilityException())
    } catch (error: Throwable) {
        M23BookingErrorMapper.failure(error)
    }
}

class SupabaseM23BookingRepository(
    private val remote: SupabaseM23RemoteDataSource = SupabaseM23RemoteDataSource()
) : M23BookingRepository {
    override fun observeMyBookings(): Flow<List<M23BookingSummary>> = flow {
        emit(runCatching {
            remote.listMyBookings().map { json ->
                M23BookingSummary(
                    booking = json.toM23Booking(),
                    providerDisplayName = json["provider_display_name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    offeringName = json["offering_name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                )
            }
        }.getOrElse { emptyList() })
    }

    override fun observeProviderBookings(providerId: String): Flow<List<M23Booking>> = flow {
        emit(runCatching { remote.listProviderBookings(providerId).map { it.toM23Booking() } }.getOrElse { emptyList() })
    }

    override fun observeBooking(id: String): Flow<M23Booking?> = flow {
        emit(runCatching { remote.getMyBooking(id).toM23Booking() }.getOrNull())
    }

    override suspend fun request(booking: M23Booking): Result<M23Booking> = try {
        Result.success(remote.createBooking(booking).toM23Booking())
    } catch (error: Throwable) {
        M23BookingErrorMapper.failure(error)
    }

    override suspend fun confirm(id: String): Result<M23Booking> = transition { remote.confirm(id).toM23Booking() }
    override suspend fun reject(id: String): Result<M23Booking> = transition { remote.reject(id).toM23Booking() }
    override suspend fun complete(id: String): Result<M23Booking> = transition { remote.complete(id).toM23Booking() }
    override suspend fun noShow(id: String): Result<M23Booking> = transition { remote.noShow(id).toM23Booking() }

    override suspend fun cancel(cancellation: M23BookingCancellation): Result<M23Booking> = try {
        Result.success(remote.cancelOwn(cancellation.bookingId, cancellation.reason).toM23Booking())
    } catch (customerError: Throwable) {
        try {
            Result.success(remote.cancelByProvider(cancellation.bookingId).toM23Booking())
        } catch (providerError: Throwable) {
            M23BookingErrorMapper.failure(providerError)
        }
    }

    override suspend fun reschedule(request: M23BookingRescheduleRequest): Result<M23Booking> =
        M23BookingErrorMapper.fail("M23_RESCHEDULE_NOT_AVAILABLE")

    private suspend fun transition(block: suspend () -> M23Booking): Result<M23Booking> = try {
        Result.success(block())
    } catch (error: Throwable) {
        M23BookingErrorMapper.failure(error)
    }
}

class SupabaseM23BookingPolicyRepository : M23BookingPolicyRepository {
    override fun observePolicy(providerId: String): Flow<M23BookingPolicy> = flowOf(M23BookingPolicy(providerId))
}
