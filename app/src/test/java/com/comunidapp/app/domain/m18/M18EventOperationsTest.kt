package com.comunidapp.app.domain.m18

import com.comunidapp.app.data.model.M18EventStatus
import com.comunidapp.app.data.model.M18PublicEvent
import com.comunidapp.app.data.model.M18RegistrationStatus
import com.comunidapp.app.data.repository.M18EventMemoryStore
import com.comunidapp.app.data.repository.M18EventValidators
import com.comunidapp.app.data.repository.MockM18EventRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** M18 Bloque 3 — participación, capacidad, waitlist y asistencia (mock). */
class M18EventOperationsTest {

    private lateinit var store: M18EventMemoryStore
    private lateinit var repository: MockM18EventRepository

    @Before
    fun setup() {
        store = M18EventMemoryStore()
        repository = MockM18EventRepository(actorUserId = { "mock_user_admin" }, store = store)
    }

    private fun openEventWithCapacity(): String =
        store.events.value.first {
            it.status == M18EventStatus.PUBLISHED &&
                it.title.contains("Charla tenencia", ignoreCase = true)
        }.id

    private fun fullEventWithWaitlist(): String =
        store.events.value.first { it.title.contains("primeros auxilios") }.id

    private fun eventWithOpenCheckIn(): String {
        val e = store.events.value.first { it.title.contains("Charla tenencia", ignoreCase = true) }
        val now = System.currentTimeMillis()
        val adjusted = e.copy(
            startsAt = now + 3_600_000L,
            endsAt = now + 7_200_000L,
            checkInOpensAt = now - 3_600_000L,
            checkInClosesAt = now + 7_200_000L
        )
        store.upsertEvent(adjusted)
        return adjusted.id
    }

    @Test
    fun registerWithCapacityBecomesRegistered() = runBlocking {
        val eventId = openEventWithCapacity()
        val reg = repository.registerForEvent(eventId).getOrThrow()
        assertEquals(M18RegistrationStatus.REGISTERED, reg.status)
    }

    @Test
    fun registerWithoutCapacityBecomesWaitlisted() = runBlocking {
        val eventId = fullEventWithWaitlist()
        val reg = repository.registerForEvent(eventId).getOrThrow()
        assertEquals(M18RegistrationStatus.WAITLISTED, reg.status)
    }

    @Test
    fun duplicateRegistrationIsIdempotent() = runBlocking {
        val eventId = openEventWithCapacity()
        repository.registerForEvent(eventId).getOrThrow()
        val before = store.idempotentRetryCount()
        val again = repository.registerForEvent(eventId).getOrThrow()
        assertEquals(M18RegistrationStatus.REGISTERED, again.status)
        assertTrue(store.idempotentRetryCount() > before)
    }

    @Test
    fun userCannotHaveTwoActiveRegistrations() = runBlocking {
        val eventId = openEventWithCapacity()
        val first = repository.registerForEvent(eventId).getOrThrow()
        val second = repository.registerForEvent(eventId).getOrThrow()
        assertEquals(first.id, second.id)
    }

    @Test
    fun cancellationFreesSpot() = runBlocking {
        val eventId = openEventWithCapacity()
        val before = repository.observeCapacitySummary(eventId).getOrThrow().availableSpots
        repository.registerForEvent(eventId).getOrThrow()
        val during = repository.observeCapacitySummary(eventId).getOrThrow().availableSpots
        assertTrue(during < before)
        repository.cancelRegistration(eventId).getOrThrow()
        val after = repository.observeCapacitySummary(eventId).getOrThrow().availableSpots
        assertEquals(before, after)
    }

    @Test
    fun cancellationPromotesFirstWaitlisted() = runBlocking {
        val eventId = fullEventWithWaitlist()
        val waitlisted = store.registrationsFor(eventId)
            .first { it.status == M18RegistrationStatus.WAITLISTED && it.userId == "user_wait_1" }
        store.registrationsFor(eventId)
            .filter { it.status == M18RegistrationStatus.REGISTERED }
            .forEach { r ->
                store.upsertRegistration(r.copy(status = M18RegistrationStatus.CANCELLED))
            }
        repository.promoteNextWaitlisted(eventId).getOrThrow()
        val promoted = store.registrationsFor(eventId).first { it.id == waitlisted.id }
        assertEquals(M18RegistrationStatus.REGISTERED, promoted.status)
    }

    @Test
    fun promotionIsIdempotent() = runBlocking {
        val eventId = openEventWithCapacity()
        val before = store.idempotentRetryCount()
        repository.promoteNextWaitlisted(eventId).getOrThrow()
        assertTrue(store.idempotentRetryCount() > before)
    }

    @Test
    fun capacityNeverExceeded() = runBlocking {
        val eventId = fullEventWithWaitlist()
        val event = store.events.value.first { it.id == eventId }
        val occupied = store.registrationsFor(eventId).count { it.status.occupiesCapacity }
        assertTrue(occupied <= event.maxCapacity)
    }

    @Test
    fun validCheckIn() = runBlocking {
        val eventId = eventWithOpenCheckIn()
        repository.registerForEvent(eventId).getOrThrow()
        val reg = store.registrationForUser(eventId, "mock_user_admin")!!
        val checked = repository.checkInRegistration(reg.id).getOrThrow()
        assertEquals(M18RegistrationStatus.CHECKED_IN, checked.status)
    }

    @Test
    fun duplicateCheckInIsIdempotent() = runBlocking {
        val eventId = eventWithOpenCheckIn()
        repository.registerForEvent(eventId).getOrThrow()
        val reg = store.registrationForUser(eventId, "mock_user_admin")!!
        repository.checkInRegistration(reg.id).getOrThrow()
        val before = store.idempotentRetryCount()
        repository.checkInRegistration(reg.id).getOrThrow()
        assertTrue(store.idempotentRetryCount() > before)
    }

    @Test
    fun unauthorizedUserCannotCheckInOthers() = runBlocking {
        val repo = MockM18EventRepository(actorUserId = { "user_vol_1" }, store = store)
        val eventId = store.events.value.first { it.status == M18EventStatus.PUBLISHED }.id
        val other = store.registrationsFor(eventId).first { it.userId != "user_vol_1" }
        assertTrue(repo.checkInRegistration(other.id).isFailure)
    }

    @Test
    fun markAttendanceFromCheckIn() = runBlocking {
        val completed = store.events.value.first { it.status == M18EventStatus.COMPLETED }
        val reg = store.registrationsFor(completed.id).first { it.status == M18RegistrationStatus.CHECKED_IN }
        val updated = repository.markAttendance(reg.id).getOrThrow()
        assertEquals(M18RegistrationStatus.ATTENDED, updated.status)
    }

    @Test
    fun noShowOnlyAfterEventEnds() = runBlocking {
        val future = store.events.value.first { it.title.contains("Feria de adopciones") }
        val reg = store.registrationsFor(future.id).first { it.status == M18RegistrationStatus.REGISTERED }
        assertEquals("M18_EVENT_NOT_ENDED", M18EventOperationsService.validateMarkNoShow(future, reg))
        val completed = store.events.value.first { it.status == M18EventStatus.COMPLETED }
        val show = store.registrationsFor(completed.id).first { it.status == M18RegistrationStatus.NO_SHOW }
        assertEquals(M18RegistrationStatus.NO_SHOW, show.status)
    }

    @Test
    fun closedEventRejectsRegistration() = runBlocking {
        val completed = store.events.value.first { it.status == M18EventStatus.COMPLETED }
        assertTrue(repository.registerForEvent(completed.id).isFailure)
    }

    @Test
    fun cancelledEventRejectsRegistration() = runBlocking {
        val cancelled = store.events.value.first { it.status == M18EventStatus.CANCELLED }
        assertTrue(repository.registerForEvent(cancelled.id).isFailure)
    }

    @Test
    fun publicStatsExcludeParticipants() = runBlocking {
        val eventId = store.events.value.first { it.status == M18EventStatus.PUBLISHED }.id
        val stats = repository.observePublicRegistrationStats(eventId).getOrThrow()
        val json = stats.toString()
        assertFalse(json.contains("user_vol"))
        assertFalse(json.contains("@"))
    }

    @Test
    fun publicEventModelHasNoUserId() {
        val published = store.events.value.first { it.status == M18EventStatus.PUBLISHED }
        val public = published.toPublicEvent(
            com.comunidapp.app.data.model.M18CapacityCalculator.summarize(
                published.maxCapacity, published.waitlistEnabled, store.registrationsFor(published.id)
            )
        )
        val fields = M18PublicEvent::class.java.declaredFields.map { it.name }
        assertFalse(fields.any { it.equals("userId", ignoreCase = true) })
        assertFalse(public.description.contains("mock_user"))
    }

    @Test
    fun mockSeedsAreDeterministic() {
        val count1 = store.events.value.size
        val store2 = M18EventMemoryStore()
        store2.seedDefaults("mock_user_admin")
        assertEquals(count1, store2.events.value.size)
    }

    @Test
    fun remoteAttendancePendingMapped() {
        val msg = com.comunidapp.app.data.remote.supabase.m18.M18EventErrorMapper.userMessage(
            "M18_ATTENDANCE_REMOTE_PENDING"
        )
        assertTrue(msg.contains("058") || msg.contains("remota"))
    }

    @Test
    fun m06UnavailableDoesNotBlockRegistration() = runBlocking {
        store.m06InfrastructureAvailable = false
        val eventId = openEventWithCapacity()
        assertTrue(repository.registerForEvent(eventId).isSuccess)
    }
}
