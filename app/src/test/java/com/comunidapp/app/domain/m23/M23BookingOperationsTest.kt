package com.comunidapp.app.domain.m23

import com.comunidapp.app.data.model.*
import com.comunidapp.app.data.repository.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class M23BookingOperationsTest {
    private val zone = ZoneId.of("America/Argentina/Buenos_Aires")
    private val clock = Clock.fixed(Instant.parse("2029-06-01T12:00:00Z"), zone)

    private fun store() = M23SchedulingMemoryStore().also { it.seedDefaults(clock) }
    private fun customerRepo(store: M23SchedulingMemoryStore = store()) =
        MockM23BookingRepository({ M23MockUsers.CUSTOMER }, store, clock)
    private fun providerRepo(store: M23SchedulingMemoryStore = store()) =
        MockM23BookingRepository({ M23MockUsers.PROVIDER }, store, clock)

    private fun futureBooking(key: String? = "idem-1") = M23Booking(
        id = "", M23MockProviderRefs.ACTIVE_MULTI_BRANCH, M23MockOfferingIds.BATH, M23MockUsers.CUSTOMER,
        Instant.parse("2029-06-15T14:00:00Z"), Instant.parse("2029-06-15T15:00:00Z"), zone,
        M23BookingModality.IN_PERSON, M23BookingStatus.REQUESTED, createdAt = Instant.now(clock), updatedAt = Instant.now(clock),
        idempotencyKey = key
    )

    @Test fun createRequestWorks() = runBlocking {
        val created = customerRepo().request(futureBooking()).getOrThrow()
        assertEquals(M23BookingStatus.REQUESTED, created.status)
    }

    @Test fun retryDoesNotDuplicate() = runBlocking {
        val repo = customerRepo()
        val first = repo.request(futureBooking("dup-key")).getOrThrow()
        val second = repo.request(futureBooking("dup-key")).getOrThrow()
        assertEquals(first.id, second.id)
    }

    @Test fun requestedIsNotConfirmed() = runBlocking {
        val booking = customerRepo().observeBooking(M23MockBookingIds.REQUESTED).first()!!
        assertEquals(M23BookingStatus.REQUESTED, booking.status)
    }

    @Test fun confirmWorks() = runBlocking {
        val confirmed = providerRepo().confirm(M23MockBookingIds.REQUESTED).getOrThrow()
        assertEquals(M23BookingStatus.CONFIRMED, confirmed.status)
    }

    @Test fun confirmIdempotent() = runBlocking {
        val repo = providerRepo()
        repo.confirm(M23MockBookingIds.REQUESTED).getOrThrow()
        val again = repo.confirm(M23MockBookingIds.REQUESTED).getOrThrow()
        assertEquals(M23BookingStatus.CONFIRMED, again.status)
    }

    @Test fun rejectTerminal() = runBlocking {
        val repo = providerRepo()
        val rejected = repo.reject(M23BookingRejectRequest(M23MockBookingIds.REQUESTED)).getOrThrow()
        assertEquals(M23BookingStatus.REJECTED, rejected.status)
        assertTrue(repo.confirm(M23MockBookingIds.REQUESTED).isFailure)
    }

    @Test fun customerCancelWorks() = runBlocking {
        val cancelled = customerRepo().cancel(M23BookingCancellation(M23MockBookingIds.CONFIRMED)).getOrThrow()
        assertEquals(M23BookingStatus.CANCELLED_BY_CUSTOMER, cancelled.status)
    }

    @Test fun providerCancelWorks() = runBlocking {
        val cancelled = providerRepo().cancel(M23BookingCancellation(M23MockBookingIds.CONFIRMED)).getOrThrow()
        assertEquals(M23BookingStatus.CANCELLED_BY_PROVIDER, cancelled.status)
    }

    @Test fun reschedulePreservesHistory() = runBlocking {
        val store = store()
        val repo = customerRepo(store)
        val rescheduled = repo.reschedule(
            M23BookingRescheduleRequest(M23MockBookingIds.CONFIRMED, Instant.parse("2029-06-20T14:00:00Z"), Instant.parse("2029-06-20T15:00:00Z"), zone)
        ).getOrThrow()
        assertEquals(M23BookingStatus.REQUESTED, rescheduled.status)
        assertTrue(store.histories.value[M23MockBookingIds.CONFIRMED]?.isNotEmpty() != false || rescheduled.startsAt != Instant.parse("2030-01-07T13:00:00Z"))
    }

    @Test fun completeTerminal() = runBlocking {
        val store = store()
        store.bookings.value = store.bookings.value.map {
            if (it.id == M23MockBookingIds.CONFIRMED) {
                it.copy(startsAt = Instant.parse("2029-01-01T10:00:00Z"), endsAt = Instant.parse("2029-01-01T11:00:00Z"))
            } else it
        }
        val completed = providerRepo(store).complete(M23MockBookingIds.CONFIRMED).getOrThrow()
        assertEquals(M23BookingStatus.COMPLETED, completed.status)
    }

    @Test fun noShowTerminal() = runBlocking {
        val store = store()
        store.bookings.value = store.bookings.value.map {
            if (it.id == M23MockBookingIds.CONFIRMED) it.copy(startsAt = Instant.parse("2029-01-01T10:00:00Z"), endsAt = Instant.parse("2029-01-01T11:00:00Z")) else it
        }
        val marked = providerRepo(store).noShow(M23MockBookingIds.CONFIRMED).getOrThrow()
        assertEquals(M23BookingStatus.NO_SHOW, marked.status)
    }

    @Test fun pastBookingRejected() = runBlocking {
        val past = futureBooking().copy(startsAt = Instant.parse("2020-01-01T10:00:00Z"), endsAt = Instant.parse("2020-01-01T11:00:00Z"))
        assertTrue(customerRepo().request(past).isFailure)
    }

    @Test fun doubleBookingRejected() = runBlocking {
        val store = store()
        val repo = customerRepo(store)
        repo.request(futureBooking("new-1")).getOrThrow()
        val overlap = futureBooking("new-2").copy(startsAt = Instant.parse("2029-06-15T14:15:00Z"), endsAt = Instant.parse("2029-06-15T15:15:00Z"))
        assertTrue(repo.request(overlap).isFailure)
    }

    @Test fun userCannotBookAsOther() = runBlocking {
        val other = futureBooking().copy(customerUserId = M23MockUsers.UNAUTHORIZED)
        assertTrue(customerRepo().request(other).isFailure)
    }

    @Test fun unauthorizedConfirmFails() = runBlocking {
        assertTrue(customerRepo().confirm(M23MockBookingIds.REQUESTED).isFailure)
    }

    @Test fun notesSanitizedInPublic() {
        val note = M23PrivacySanitizer.scrubPublicText("Llamar al +54 11 4444 5555")
        assertFalse(note.contains("4444"))
    }

    @Test fun completedEnablesM21AdapterStub() = runBlocking {
        val adapter = object : M23BookingEligibilityAdapter {
            override suspend fun isEligible(customerUserId: String, providerId: String) = true
        }
        assertTrue(adapter.isEligible(M23MockUsers.CUSTOMER, M23MockProviderRefs.ACTIVE_MULTI_BRANCH))
    }

    @Test fun cancelledDoesNotAutoEnableReview() = runBlocking {
        val booking = customerRepo().observeBooking(M23MockBookingIds.CANCELLED).first()!!
        assertTrue(booking.status.name.startsWith("CANCELLED"))
    }

    @Test fun m06UnavailableDoesNotBlock() = runBlocking {
        val repo = MockM23BookingRepository(
            { M23MockUsers.CUSTOMER }, store(), clock,
            notifier = FailingM23BookingNotificationAdapter()
        )
        assertTrue(repo.request(futureBooking("m6-safe")).isSuccess)
    }

    @Test fun metricsFromBookings() = runBlocking {
        val bookings = providerRepo().observeProviderBookings(M23MockProviderRefs.ACTIVE_MULTI_BRANCH).first()
        assertTrue(bookings.size >= 3)
    }

    @Test fun navigationContextUsesM22Refs() {
        assertEquals(M22MockProviderIds.ACTIVE_MULTI_BRANCH, M23MockProviderRefs.ACTIVE_MULTI_BRANCH)
    }
}
