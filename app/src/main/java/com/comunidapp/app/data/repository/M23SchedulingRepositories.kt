package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.*
import com.comunidapp.app.domain.m23.M23BookingOperationsService
import com.comunidapp.app.domain.m23.M23PrivacySanitizer
import com.comunidapp.app.domain.m23.M23SlotGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

interface M23AvailabilityRepository {
    fun observeRules(providerId: String): Flow<List<M23AvailabilityRule>>
    fun observeSlots(query: M23SlotQuery): Flow<M23SlotPage>
    suspend fun upsertRule(rule: M23AvailabilityRule): Result<M23AvailabilityRule>
    suspend fun upsertException(exception: M23AvailabilityException): Result<M23AvailabilityException>
}
interface M23BookingRepository {
    fun observeMyBookings(): Flow<List<M23BookingSummary>>
    fun observeProviderBookings(providerId: String): Flow<List<M23Booking>>
    fun observeBooking(id: String): Flow<M23Booking?>
    suspend fun request(booking: M23Booking): Result<M23Booking>
    suspend fun confirm(id: String): Result<M23Booking>
    suspend fun reject(id: String): Result<M23Booking>
    suspend fun cancel(cancellation: M23BookingCancellation): Result<M23Booking>
    suspend fun reschedule(request: M23BookingRescheduleRequest): Result<M23Booking>
    suspend fun complete(id: String): Result<M23Booking>
    suspend fun noShow(id: String): Result<M23Booking>
}
interface M23BookingPolicyRepository { fun observePolicy(providerId: String): Flow<M23BookingPolicy> }

class M23SchedulingMemoryStore {
    val mutex = Mutex()
    val rules = MutableStateFlow<List<M23AvailabilityRule>>(emptyList())
    val exceptions = MutableStateFlow<List<M23AvailabilityException>>(emptyList())
    val bookings = MutableStateFlow<List<M23Booking>>(emptyList())
    val policies = MutableStateFlow<List<M23BookingPolicy>>(emptyList())
    val histories = MutableStateFlow<Map<String, List<M23BookingHistoryEntry>>>(emptyMap())
    private var sequence = 0
    fun nextId(prefix: String) = "${prefix}_${++sequence}"
    fun seedDefaults(clock: Clock = Clock.systemUTC()) {
        if (rules.value.isNotEmpty()) return
        val zone = ZoneId.of("America/Argentina/Buenos_Aires")
        val provider = M23MockProviderRefs.ACTIVE_MULTI_BRANCH
        rules.value = listOf(
            M23AvailabilityRule("m23_rule_mon", provider, M23MockOfferingIds.BATH, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0), 30, zone),
            M23AvailabilityRule("m23_rule_tue", provider, M23MockOfferingIds.BATH, DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(12, 0), 30, zone),
            M23AvailabilityRule("m23_rule_wed", provider, M23MockOfferingIds.BATH, DayOfWeek.WEDNESDAY, LocalTime.of(14, 0), LocalTime.of(17, 0), 30, zone),
            M23AvailabilityRule("m23_rule_walk", M23MockProviderRefs.DRAFT, M23MockOfferingIds.WALK, DayOfWeek.FRIDAY, LocalTime.of(10, 0), LocalTime.of(13, 0), 60, zone)
        )
        exceptions.value = listOf(
            M23AvailabilityException("m23_exception_holiday", provider, date = LocalDate.of(2030, 1, 1), type = M23ExceptionType.HOLIDAY),
            M23AvailabilityException("m23_exception_open", provider, M23MockOfferingIds.BATH, LocalDate.of(2030, 1, 2), LocalTime.of(9, 0), LocalTime.of(11, 0), M23ExceptionType.SPECIAL_OPENING)
        )
        val now = Instant.now(clock)
        fun booking(id: String, status: M23BookingStatus, hour: Long) = M23Booking(id, provider, M23MockOfferingIds.BATH, M23MockUsers.CUSTOMER, Instant.parse("2030-01-07T${hour.toString().padStart(2, '0')}:00:00Z"), Instant.parse("2030-01-07T${(hour + 1).toString().padStart(2, '0')}:00:00Z"), zone, M23BookingModality.IN_PERSON, status, createdAt = now, updatedAt = now)
        bookings.value = listOf(booking(M23MockBookingIds.REQUESTED, M23BookingStatus.REQUESTED, 12), booking(M23MockBookingIds.CONFIRMED, M23BookingStatus.CONFIRMED, 13), booking(M23MockBookingIds.COMPLETED, M23BookingStatus.COMPLETED, 14), booking(M23MockBookingIds.CANCELLED, M23BookingStatus.CANCELLED_BY_CUSTOMER, 15))
        policies.value = listOf(M23BookingPolicy(provider), M23BookingPolicy(M23MockProviderRefs.DRAFT))
        // 4 rules + 2 exceptions + 4 bookings + 2 policies + 13 deterministic history entries = 25 seed records.
        histories.value = bookings.value.associate { it.id to List(if (it.status == M23BookingStatus.COMPLETED) 4 else 3) { index -> M23BookingHistoryEntry(now.plusSeconds(index.toLong()), null, it.status) } }
    }
}

class MockM23AvailabilityRepository(private val store: M23SchedulingMemoryStore, private val clock: Clock = Clock.systemUTC()) : M23AvailabilityRepository {
    init { store.seedDefaults(clock) }
    override fun observeRules(providerId: String): Flow<List<M23AvailabilityRule>> = store.rules.map { it.filter { rule -> rule.providerId == providerId } }
    override fun observeSlots(query: M23SlotQuery): Flow<M23SlotPage> = store.rules.map {
        M23SlotGenerator.generate(query, it, store.exceptions.value, store.bookings.value)
    }
    override suspend fun upsertRule(rule: M23AvailabilityRule): Result<M23AvailabilityRule> = mutate {
        M23BookingValidators.validateRule(rule)?.let(::fail)
        store.rules.value = store.rules.value.filterNot { it.id == rule.id } + rule
        rule
    }
    override suspend fun upsertException(exception: M23AvailabilityException): Result<M23AvailabilityException> = mutate {
        store.exceptions.value = store.exceptions.value.filterNot { it.id == exception.id } + exception
        exception
    }
    private suspend fun <T> mutate(block: () -> T): Result<T> = store.mutex.withLock { try { Result.success(block()) } catch (e: Throwable) { M23BookingErrors.failure(e) } }
    private fun fail(code: String): Nothing = throw M23BookingException(code)
}

class MockM23BookingPolicyRepository(private val store: M23SchedulingMemoryStore) : M23BookingPolicyRepository {
    init { store.seedDefaults() }
    override fun observePolicy(providerId: String): Flow<M23BookingPolicy> = store.policies.map { it.firstOrNull { policy -> policy.providerId == providerId } ?: M23BookingPolicy(providerId) }
}

class MockM23BookingRepository(
    private val actorUserId: () -> String?,
    private val store: M23SchedulingMemoryStore,
    private val clock: Clock = Clock.systemUTC(),
    private val eligibility: M23BookingEligibilityAdapter = AllowAllM23BookingEligibilityAdapter,
    private val notifier: M23BookingNotificationAdapter = NoOpM23BookingNotificationAdapter
) : M23BookingRepository {
    private val operations = M23BookingOperationsService(clock)
    init { store.seedDefaults(clock) }
    override fun observeMyBookings(): Flow<List<M23BookingSummary>> = store.bookings.map { all ->
        val actor = actorUserId()
        all.filter { it.customerUserId == actor }.map { M23BookingSummary(it, "Patitas Centro", "Baño completo") }
    }
    override fun observeProviderBookings(providerId: String): Flow<List<M23Booking>> = store.bookings.map { all -> all.filter { it.providerId == providerId && isProvider(providerId) } }
    override fun observeBooking(id: String): Flow<M23Booking?> = store.bookings.map { it.firstOrNull { booking -> booking.id == id && canRead(booking) } }
    override suspend fun request(booking: M23Booking): Result<M23Booking> = mutate {
        val actor = actor()
        if (booking.customerUserId != actor) fail("M23_PERMISSION_DENIED")
        store.bookings.value.firstOrNull { it.idempotencyKey != null && it.idempotencyKey == booking.idempotencyKey && it.customerUserId == actor }?.let { return@mutate it }
        if (!eligibility.isEligible(actor, booking.providerId)) fail("M23_NOT_ELIGIBLE")
        val policy = policy(booking.providerId)
        M23BookingValidators.validateBooking(booking, policy, clock)?.let(::fail)
        operations.ensureAvailable(booking, store.bookings.value)
        booking.copy(id = booking.id.ifBlank { store.nextId("m23_booking") }, status = M23BookingStatus.REQUESTED, createdAt = Instant.now(clock), updatedAt = Instant.now(clock)).also(::save)
    }
    override suspend fun confirm(id: String) = providerOnly(id) { operations.confirm(it) }
    override suspend fun reject(id: String) = providerOnly(id) { operations.reject(it) }
    override suspend fun complete(id: String) = providerOnly(id) { operations.complete(it) }
    override suspend fun noShow(id: String) = providerOnly(id) { operations.noShow(it, policy(it.providerId)) }
    override suspend fun cancel(cancellation: M23BookingCancellation) = transition(cancellation.bookingId) { booking ->
        operations.cancel(booking, isProvider(booking.providerId), policy(booking.providerId))
    }
    override suspend fun reschedule(request: M23BookingRescheduleRequest): Result<M23Booking> = mutate {
        val old = ownedOrCustomer(request.bookingId)
        val moved = old.copy(startsAt = request.startsAt, endsAt = request.endsAt, zoneId = request.zoneId)
        operations.reschedule(old, moved, policy(old.providerId), store.bookings.value).also(::save)
    }
    private suspend fun transition(id: String, operation: (M23Booking) -> M23Booking): Result<M23Booking> = mutate { operation(ownedOrCustomer(id)).also(::save) }
    private suspend fun providerOnly(id: String, operation: (M23Booking) -> M23Booking): Result<M23Booking> = mutate {
        val booking = store.bookings.value.firstOrNull { it.id == id } ?: fail("M23_BOOKING_NOT_FOUND")
        if (!isProvider(booking.providerId)) fail("M23_PERMISSION_DENIED")
        operation(booking).also(::save)
    }
    private fun save(booking: M23Booking): M23Booking { store.bookings.value = store.bookings.value.filterNot { it.id == booking.id } + booking; return booking }
    private fun actor() = actorUserId() ?: fail("NOT_AUTHENTICATED")
    private fun isProvider(providerId: String) = actor() == M23MockUsers.PROVIDER && providerId == M23MockProviderRefs.ACTIVE_MULTI_BRANCH
    private fun canRead(booking: M23Booking) = booking.customerUserId == actorUserId() || isProvider(booking.providerId)
    private fun ownedOrCustomer(id: String): M23Booking = store.bookings.value.firstOrNull { it.id == id && canRead(it) } ?: fail("M23_PERMISSION_DENIED")
    private fun policy(providerId: String) = store.policies.value.firstOrNull { it.providerId == providerId } ?: M23BookingPolicy(providerId)
    private suspend fun <T> mutate(block: suspend () -> T): Result<T> = store.mutex.withLock { try { Result.success(block()) } catch (e: Throwable) { M23BookingErrors.failure(e) } }
    private fun fail(code: String): Nothing = throw M23BookingException(code)
}
