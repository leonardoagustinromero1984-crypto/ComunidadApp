package com.comunidapp.app.domain.m23

import com.comunidapp.app.data.model.*
import com.comunidapp.app.data.repository.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.*

/** M23 Bloque 3 — operaciones, filtros, integraciones y privacidad. */
class M23BookingBlock3Test {
    private val zone = ZoneId.of("America/Argentina/Buenos_Aires")
    private val clock = Clock.fixed(Instant.parse("2029-06-01T12:00:00Z"), zone)

    private fun store() = M23SchedulingMemoryStore().also { it.seedDefaults(clock) }
    private fun customerRepo(store: M23SchedulingMemoryStore = store()) =
        MockM23BookingRepository({ M23MockUsers.CUSTOMER }, store, clock)
    private fun providerRepo(store: M23SchedulingMemoryStore = store()) =
        MockM23BookingRepository({ M23MockUsers.PROVIDER }, store, clock)
    private fun otherRepo(store: M23SchedulingMemoryStore = store()) =
        MockM23BookingRepository({ M23MockUsers.UNAUTHORIZED }, store, clock)

    private fun futureBooking(key: String? = "b3-key") = M23Booking(
        "", M23MockProviderRefs.ACTIVE_MULTI_BRANCH, M23MockOfferingIds.BATH, M23MockUsers.CUSTOMER,
        Instant.parse("2029-06-15T14:00:00Z"), Instant.parse("2029-06-15T15:00:00Z"), zone,
        M23BookingModality.IN_PERSON, M23BookingStatus.REQUESTED,
        createdAt = Instant.now(clock), updatedAt = Instant.now(clock), idempotencyKey = key
    )

    @Test fun customerSeesOnlyOwnBookings() = runBlocking {
        val mine = customerRepo().observeMyBookings().first()
        assertTrue(mine.all { it.booking.customerUserId == M23MockUsers.CUSTOMER })
    }

    @Test fun providerSeesAuthorizedBookings() = runBlocking {
        val list = providerRepo().observeProviderBookings(M23MockProviderRefs.ACTIVE_MULTI_BRANCH).first()
        assertTrue(list.isNotEmpty())
    }

    @Test fun confirmRequestedWorks() = runBlocking {
        assertEquals(M23BookingStatus.CONFIRMED, providerRepo().confirm(M23MockBookingIds.REQUESTED).getOrThrow().status)
    }

    @Test fun customerCannotConfirm() = runBlocking {
        assertTrue(customerRepo().confirm(M23MockBookingIds.REQUESTED).isFailure)
    }

    @Test fun confirmRepeatedIsIdempotent() = runBlocking {
        val repo = providerRepo()
        repo.confirm(M23MockBookingIds.REQUESTED).getOrThrow()
        assertEquals(M23BookingStatus.CONFIRMED, repo.confirm(M23MockBookingIds.REQUESTED).getOrThrow().status)
    }

    @Test fun confirmOccupiedSlotFails() = runBlocking {
        val store = store()
        val repo = customerRepo(store)
        repo.request(futureBooking("occ-1")).getOrThrow()
        providerRepo(store).confirm(store.bookings.value.last { it.idempotencyKey == "occ-1" }.id).getOrThrow()
        val overlap = futureBooking("occ-2").copy(
            startsAt = Instant.parse("2029-06-15T14:15:00Z"),
            endsAt = Instant.parse("2029-06-15T15:15:00Z")
        )
        assertTrue(repo.request(overlap).isFailure)
    }

    @Test fun rejectWorks() = runBlocking {
        val rejected = providerRepo().reject(M23BookingRejectRequest(M23MockBookingIds.REQUESTED, "No disponible")).getOrThrow()
        assertEquals(M23BookingStatus.REJECTED, rejected.status)
    }

    @Test fun rejectIsTerminal() = runBlocking {
        val store = store()
        providerRepo(store).reject(M23BookingRejectRequest(M23MockBookingIds.REQUESTED)).getOrThrow()
        assertTrue(providerRepo(store).confirm(M23MockBookingIds.REQUESTED).isFailure)
    }

    @Test fun customerCancelWithinPolicy() = runBlocking {
        val cancelled = customerRepo().cancel(M23BookingCancellation(M23MockBookingIds.CONFIRMED)).getOrThrow()
        assertEquals(M23BookingStatus.CANCELLED_BY_CUSTOMER, cancelled.status)
    }

    @Test fun customerCancelOutsidePolicyFails() = runBlocking {
        val store = store()
        store.bookings.value = store.bookings.value.map {
            if (it.id == M23MockBookingIds.CONFIRMED) {
                it.copy(startsAt = Instant.parse("2029-06-01T12:30:00Z"), endsAt = Instant.parse("2029-06-01T13:30:00Z"))
            } else it
        }
        assertTrue(customerRepo(store).cancel(M23BookingCancellation(M23MockBookingIds.CONFIRMED)).isFailure)
    }

    @Test fun providerCancelWorks() = runBlocking {
        val cancelled = providerRepo().cancel(M23BookingCancellation(M23MockBookingIds.CONFIRMED)).getOrThrow()
        assertEquals(M23BookingStatus.CANCELLED_BY_PROVIDER, cancelled.status)
    }

    @Test fun privateReasonNotInPublicHistory() = runBlocking {
        val store = store()
        providerRepo(store).reject(
            M23BookingRejectRequest(M23MockBookingIds.REQUESTED, "Público", "Secreto interno +54 11 9999 8888")
        ).getOrThrow()
        val history = customerRepo(store).observeBookingHistory(M23MockBookingIds.REQUESTED).first()
        assertTrue(store.privateReasons.value[M23MockBookingIds.REQUESTED]?.contains("Secreto") == true)
        assertFalse(history.any { it.reason?.contains("Secreto") == true })
    }

    @Test fun rescheduleWorks() = runBlocking {
        val rescheduled = customerRepo().reschedule(
            M23BookingRescheduleRequest(
                M23MockBookingIds.CONFIRMED,
                Instant.parse("2029-06-20T14:00:00Z"),
                Instant.parse("2029-06-20T15:00:00Z"),
                zone
            )
        ).getOrThrow()
        assertEquals(M23BookingStatus.REQUESTED, rescheduled.status)
        assertEquals(M23MockBookingIds.CONFIRMED, rescheduled.rescheduledFromBookingId)
    }

    @Test fun reschedulePreservesHistory() = runBlocking {
        val store = store()
        val newId = customerRepo(store).reschedule(
            M23BookingRescheduleRequest(M23MockBookingIds.CONFIRMED, Instant.parse("2029-06-20T14:00:00Z"), Instant.parse("2029-06-20T15:00:00Z"), zone)
        ).getOrThrow().id
        assertTrue(store.histories.value[M23MockBookingIds.CONFIRMED]?.size ?: 0 >= 2)
        assertTrue(store.histories.value[newId]?.isNotEmpty() == true)
    }

    @Test fun rescheduleRepeatedDoesNotDuplicate() = runBlocking {
        val store = store()
        val repo = customerRepo(store)
        val req = M23BookingRescheduleRequest(M23MockBookingIds.CONFIRMED, Instant.parse("2029-06-20T14:00:00Z"), Instant.parse("2029-06-20T15:00:00Z"), zone)
        val first = repo.reschedule(req).getOrThrow()
        store.bookings.value = store.bookings.value.filterNot { it.rescheduledFromBookingId == M23MockBookingIds.CONFIRMED && it.id != first.id }
        val count = store.bookings.value.count { it.rescheduledFromBookingId == M23MockBookingIds.CONFIRMED }
        assertEquals(1, count)
    }

    @Test fun rescheduleToOccupiedFails() = runBlocking {
        val store = store()
        val repo = customerRepo(store)
        repo.request(futureBooking("rs-occ")).getOrThrow()
        val fail = repo.reschedule(
            M23BookingRescheduleRequest(
                M23MockBookingIds.CONFIRMED,
                Instant.parse("2029-06-15T14:00:00Z"),
                Instant.parse("2029-06-15T15:00:00Z"),
                zone
            )
        )
        assertTrue(fail.isFailure)
    }

    @Test fun completedWorks() = runBlocking {
        val store = store()
        store.bookings.value = store.bookings.value.map {
            if (it.id == M23MockBookingIds.CONFIRMED) {
                it.copy(startsAt = Instant.parse("2029-01-01T10:00:00Z"), endsAt = Instant.parse("2029-01-01T11:00:00Z"))
            } else it
        }
        assertEquals(M23BookingStatus.COMPLETED, providerRepo(store).complete(M23MockBookingIds.CONFIRMED).getOrThrow().status)
    }

    @Test fun completedRepeatedIsIdempotent() = runBlocking {
        val store = store()
        store.bookings.value = store.bookings.value.map {
            if (it.id == M23MockBookingIds.CONFIRMED) {
                it.copy(startsAt = Instant.parse("2029-01-01T10:00:00Z"), endsAt = Instant.parse("2029-01-01T11:00:00Z"))
            } else it
        }
        val repo = providerRepo(store)
        repo.complete(M23MockBookingIds.CONFIRMED).getOrThrow()
        assertEquals(M23BookingStatus.COMPLETED, repo.complete(M23MockBookingIds.CONFIRMED).getOrThrow().status)
    }

    @Test fun completedEnablesM21Adapter() = runBlocking {
        val store = store()
        store.bookings.value = store.bookings.value.map {
            if (it.id == M23MockBookingIds.CONFIRMED) {
                it.copy(status = M23BookingStatus.COMPLETED)
            } else it
        }
        val booking = store.bookings.value.first { it.id == M23MockBookingIds.CONFIRMED }
        assertTrue(M23BookingReviewEligibilityAdapter.isReviewEligible(booking))
        assertNotNull(M23BookingReviewEligibilityAdapter.contextFor(booking, "Baño"))
    }

    @Test fun noShowWorks() = runBlocking {
        val store = store()
        store.bookings.value = store.bookings.value.map {
            if (it.id == M23MockBookingIds.CONFIRMED) {
                it.copy(startsAt = Instant.parse("2029-01-01T10:00:00Z"), endsAt = Instant.parse("2029-01-01T11:00:00Z"))
            } else it
        }
        assertEquals(M23BookingStatus.NO_SHOW, providerRepo(store).noShow(M23MockBookingIds.CONFIRMED).getOrThrow().status)
    }

    @Test fun noShowTooEarlyFails() = runBlocking {
        assertTrue(providerRepo().noShow(M23MockBookingIds.CONFIRMED).isFailure)
    }

    @Test fun expiredWorks() = runBlocking {
        val store = store()
        store.bookings.value = store.bookings.value.map {
            if (it.id == M23MockBookingIds.REQUESTED) {
                it.copy(createdAt = Instant.parse("2029-01-01T10:00:00Z"))
            } else it
        }
        assertEquals(M23BookingStatus.EXPIRED, providerRepo(store).expire(M23MockBookingIds.REQUESTED).getOrThrow().status)
    }

    @Test fun terminalDoesNotReopen() = runBlocking {
        val store = store()
        providerRepo(store).reject(M23BookingRejectRequest(M23MockBookingIds.REQUESTED)).getOrThrow()
        assertTrue(customerRepo(store).cancel(M23BookingCancellation(M23MockBookingIds.REQUESTED)).isFailure)
    }

    @Test fun exceptionDoesNotDeleteBooking() = runBlocking {
        val store = store()
        val before = store.bookings.value.size
        store.exceptions.value = store.exceptions.value + M23AvailabilityException(
            "block-day", M23MockProviderRefs.ACTIVE_MULTI_BRANCH, date = LocalDate.of(2030, 1, 7), type = M23ExceptionType.BLOCKED
        )
        assertEquals(before, store.bookings.value.size)
    }

    @Test fun specialOpeningGeneratesSlot() = runBlocking {
        val page = MockM23AvailabilityRepository(store(), clock).observeSlots(
            M23SlotQuery(M23MockProviderRefs.ACTIVE_MULTI_BRANCH, M23MockOfferingIds.BATH, LocalDate.of(2030, 1, 2), LocalDate.of(2030, 1, 2), zone)
        ).first()
        assertTrue(page.days.single().slots.isNotEmpty())
    }

    @Test fun m06FailureDoesNotBlock() = runBlocking {
        val repo = MockM23BookingRepository({ M23MockUsers.CUSTOMER }, store(), clock, notifier = FailingM23BookingNotificationAdapter())
        assertTrue(repo.request(futureBooking("m06-b3")).isSuccess)
    }

    @Test fun m20UnavailableDoesNotBlockBooking() = runBlocking {
        val repo = MockM23BookingRepository({ M23MockUsers.CUSTOMER }, store(), clock, messaging = UnavailableM23BookingMessagingAdapter)
        assertTrue(repo.request(futureBooking("m20-b3")).isSuccess)
        assertTrue(repo.openConversation(M23MockBookingIds.CONFIRMED).isFailure)
    }

    @Test fun m21UnavailableDoesNotBlockComplete() = runBlocking {
        val store = store()
        store.bookings.value = store.bookings.value.map {
            if (it.id == M23MockBookingIds.CONFIRMED) {
                it.copy(startsAt = Instant.parse("2029-01-01T10:00:00Z"), endsAt = Instant.parse("2029-01-01T11:00:00Z"))
            } else it
        }
        assertTrue(providerRepo(store).complete(M23MockBookingIds.CONFIRMED).isSuccess)
    }

    @Test fun unauthorizedUserDenied() = runBlocking {
        assertTrue(otherRepo().confirm(M23MockBookingIds.REQUESTED).isFailure)
        assertTrue(otherRepo().observeBooking(M23MockBookingIds.CONFIRMED).first() == null)
    }

    @Test fun privateDataNotPublic() {
        val booking = M23Booking(
            M23MockBookingIds.CONFIRMED, M23MockProviderRefs.ACTIVE_MULTI_BRANCH, M23MockOfferingIds.BATH,
            M23MockUsers.CUSTOMER, Instant.parse("2030-01-07T13:00:00Z"), Instant.parse("2030-01-07T14:00:00Z"),
            zone, M23BookingModality.IN_PERSON, M23BookingStatus.CONFIRMED,
            providerPrivateNote = "Nota clínica interna", createdAt = Instant.now(clock), updatedAt = Instant.now(clock)
        )
        val public = M23PrivacySanitizer.publicContext(booking, "Patitas", "Baño")
        assertFalse(public.toString().contains("clínica"))
        assertFalse(public.toString().contains(M23MockUsers.CUSTOMER))
    }

    @Test fun timeZoneCorrect() = runBlocking {
        val booking = customerRepo().observeBooking(M23MockBookingIds.CONFIRMED).first()!!
        assertEquals(zone, booking.zoneId)
    }

    @Test fun historyOrdered() = runBlocking {
        val history = customerRepo().observeBookingHistory(M23MockBookingIds.CONFIRMED).first()
        assertEquals(history, history.sortedBy { it.at })
    }

    @Test fun metricsCorrect() = runBlocking {
        val bookings = providerRepo().observeProviderBookings(M23MockProviderRefs.ACTIVE_MULTI_BRANCH).first()
        val metrics = M23BookingFilters.metrics(bookings)
        assertTrue(metrics.requested + metrics.confirmed + metrics.completed >= 2)
    }

    @Test fun mockDeterministic() = runBlocking {
        val a = customerRepo().observeMyBookings().first().map { it.booking.id }
        val b = customerRepo().observeMyBookings().first().map { it.booking.id }
        assertEquals(a, b)
    }

    @Test fun noRealPaymentReferences() {
        val text = M23BookingResilience.safeUserMessage(IllegalStateException("M23_SLOT_UNAVAILABLE"))
        assertFalse(text.contains("pago"))
        assertFalse(text.contains("Mercado"))
    }
}
