package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.M23AvailabilityException
import com.comunidapp.app.data.model.M23AvailabilityRule
import com.comunidapp.app.data.model.M23Booking
import com.comunidapp.app.data.model.M23BookingCancellation
import com.comunidapp.app.data.model.M23BookingFilter
import com.comunidapp.app.data.model.M23BookingHistoryEntry
import com.comunidapp.app.data.model.M23BookingPolicy
import com.comunidapp.app.data.model.M23BookingRejectRequest
import com.comunidapp.app.data.model.M23BookingRescheduleRequest
import com.comunidapp.app.data.model.M23BookingSummary
import com.comunidapp.app.data.model.M23ProviderBookingFilter
import com.comunidapp.app.data.model.M23SlotPage
import com.comunidapp.app.data.model.M23SlotQuery
import com.comunidapp.app.data.remote.supabase.m23.M23BookingErrorMapper
import com.comunidapp.app.data.remote.supabase.m23.SupabaseM23RemoteDataSource
import com.comunidapp.app.data.remote.supabase.m23.toM23AvailabilityException
import com.comunidapp.app.data.remote.supabase.m23.toM23AvailabilityRule
import com.comunidapp.app.data.remote.supabase.m23.toM23Booking
import com.comunidapp.app.data.remote.supabase.m23.toM23SlotPage
import com.comunidapp.app.domain.m23.M23BookingFilters
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
    override fun observeMyBookings(filter: M23BookingFilter): Flow<List<M23BookingSummary>> = flow {
        emit(runCatching {
            remote.listMyBookings().map { json ->
                M23BookingSummary(
                    booking = json.toM23Booking(),
                    providerDisplayName = json["provider_display_name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    offeringName = json["offering_name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                )
            }.filter { M23BookingFilters.matchesCustomer(it.booking, filter) }
                .let(M23BookingFilters::sortCustomer)
        }.getOrElse { emptyList() })
    }

    override fun observeProviderBookings(providerId: String, filter: M23ProviderBookingFilter): Flow<List<M23Booking>> = flow {
        emit(runCatching {
            remote.listProviderBookings(providerId).map { it.toM23Booking() }
                .filter { M23BookingFilters.matchesProvider(it, filter) }
                .let(M23BookingFilters::sortProvider)
        }.getOrElse { emptyList() })
    }

    override fun observeBooking(id: String): Flow<M23Booking?> = flow {
        emit(runCatching { remote.getMyBooking(id).toM23Booking() }.getOrNull())
    }

    override fun observeBookingHistory(id: String): Flow<List<M23BookingHistoryEntry>> = flow {
        emit(runCatching { remote.listBookingHistory(id).map { it.toHistoryEntry() } }.getOrElse { emptyList() })
    }

    override suspend fun request(booking: M23Booking): Result<M23Booking> = try {
        Result.success(remote.createBooking(booking).toM23Booking())
    } catch (error: Throwable) {
        M23BookingErrorMapper.failure(error)
    }

    override suspend fun confirm(id: String): Result<M23Booking> = transition { remote.confirm(id).toM23Booking() }

    override suspend fun reject(request: M23BookingRejectRequest): Result<M23Booking> = transition {
        remote.reject(request.bookingId, request.publicReason, request.privateReason).toM23Booking()
    }

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

    override suspend fun reschedule(request: M23BookingRescheduleRequest): Result<M23Booking> = try {
        Result.success(remote.reschedule(request).toM23Booking())
    } catch (error: Throwable) {
        M23BookingErrorMapper.failure(error)
    }

    override suspend fun expire(id: String): Result<M23Booking> = try {
        Result.success(remote.expire(id).toM23Booking())
    } catch (error: Throwable) {
        M23BookingErrorMapper.failure(error)
    }

    override suspend fun openConversation(bookingId: String): Result<String> =
        M23BookingErrorMapper.fail("M23_MESSAGING_UNAVAILABLE")

    private suspend fun transition(block: suspend () -> M23Booking): Result<M23Booking> = try {
        Result.success(block())
    } catch (error: Throwable) {
        M23BookingErrorMapper.failure(error)
    }
}

class SupabaseM23BookingPolicyRepository : M23BookingPolicyRepository {
    override fun observePolicy(providerId: String): Flow<M23BookingPolicy> = flowOf(M23BookingPolicy(providerId))
}

private fun kotlinx.serialization.json.JsonObject.toHistoryEntry(): M23BookingHistoryEntry {
    val status = text("to_status")?.let { runCatching { com.comunidapp.app.data.model.M23BookingStatus.valueOf(it) }.getOrNull() }
        ?: com.comunidapp.app.data.model.M23BookingStatus.REQUESTED
    val from = text("from_status")?.let { runCatching { com.comunidapp.app.data.model.M23BookingStatus.valueOf(it) }.getOrNull() }
    return M23BookingHistoryEntry(
        at = text("created_at")?.let(java.time.Instant::parse) ?: java.time.Instant.EPOCH,
        from = from,
        to = status,
        reason = text("reason")
    )
}

private fun kotlinx.serialization.json.JsonObject.text(key: String): String? =
    (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
