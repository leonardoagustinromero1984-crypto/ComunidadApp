package com.comunidapp.app.domain.m23

import com.comunidapp.app.data.model.*
import com.comunidapp.app.data.repository.M23BookingValidators
import com.comunidapp.app.data.repository.M23SchedulingMemoryStore
import com.comunidapp.app.data.repository.MockM23AvailabilityRepository
import com.comunidapp.app.data.repository.MockM23BookingRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class M23SchedulingFoundationTest {
    private val zone = ZoneId.of("America/Argentina/Buenos_Aires")
    private val clock = Clock.fixed(Instant.parse("2029-06-01T12:00:00Z"), zone)

    private fun availabilityStore() = M23SchedulingMemoryStore().also { it.seedDefaults(clock) }
    private fun bookingRepo(actor: String = M23MockUsers.CUSTOMER, store: M23SchedulingMemoryStore = availabilityStore()) =
        MockM23BookingRepository({ actor }, store, clock)

    @Test fun validRuleAccepted() {
        val rule = M23AvailabilityRule("r1", M23MockProviderRefs.ACTIVE_MULTI_BRANCH, M23MockOfferingIds.BATH, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0), 30, zone)
        assertNull(M23BookingValidators.validateRule(rule))
    }

    @Test fun invalidRuleRejected() {
        val rule = M23AvailabilityRule("r1", M23MockProviderRefs.ACTIVE_MULTI_BRANCH, M23MockOfferingIds.BATH, DayOfWeek.MONDAY, LocalTime.of(12, 0), LocalTime.of(9, 0), 30, zone)
        assertEquals("M23_INVALID_AVAILABILITY_RULE", M23BookingValidators.validateRule(rule))
    }

    @Test fun ruleGeneratesSlots() = runBlocking {
        val page = MockM23AvailabilityRepository(availabilityStore(), clock).observeSlots(
            M23SlotQuery(M23MockProviderRefs.ACTIVE_MULTI_BRANCH, M23MockOfferingIds.BATH, LocalDate.of(2030, 1, 1), LocalDate.of(2030, 1, 14), zone)
        ).first()
        assertTrue(page.days.any { it.slots.isNotEmpty() })
    }

    @Test fun blockedExceptionRemovesSlots() = runBlocking {
        val store = availabilityStore()
        store.exceptions.value = store.exceptions.value + M23AvailabilityException("block", M23MockProviderRefs.ACTIVE_MULTI_BRANCH, M23MockOfferingIds.BATH, LocalDate.of(2030, 1, 6), type = M23ExceptionType.BLOCKED)
        val page = MockM23AvailabilityRepository(store, clock).observeSlots(
            M23SlotQuery(M23MockProviderRefs.ACTIVE_MULTI_BRANCH, M23MockOfferingIds.BATH, LocalDate.of(2030, 1, 7), LocalDate.of(2030, 1, 7), zone)
        ).first()
        assertTrue(page.days.single().slots.isEmpty())
    }

    @Test fun specialOpeningAddsSlots() = runBlocking {
        val page = MockM23AvailabilityRepository(availabilityStore(), clock).observeSlots(
            M23SlotQuery(M23MockProviderRefs.ACTIVE_MULTI_BRANCH, M23MockOfferingIds.BATH, LocalDate.of(2030, 1, 2), LocalDate.of(2030, 1, 2), zone)
        ).first()
        assertTrue(page.days.single().slots.isNotEmpty())
    }

    @Test fun noPastSlotsGenerated() {
        val page = M23SlotGenerator.generate(
            M23SlotQuery(M23MockProviderRefs.ACTIVE_MULTI_BRANCH, M23MockOfferingIds.BATH, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 7), zone),
            emptyList(), emptyList(), emptyList()
        )
        assertTrue(page.days.all { it.slots.isEmpty() })
    }

    @Test fun respectsTimeZone() = runBlocking {
        val page = MockM23AvailabilityRepository(availabilityStore(), clock).observeSlots(
            M23SlotQuery(M23MockProviderRefs.ACTIVE_MULTI_BRANCH, M23MockOfferingIds.BATH, LocalDate.of(2030, 1, 1), LocalDate.of(2030, 1, 14), zone)
        ).first()
        val slot = page.days.first { it.slots.isNotEmpty() }.slots.first()
        assertEquals(zone, slot.startsAt.atZone(zone).zone)
    }

    @Test fun overlapDetectionWorks() {
        assertTrue(M23SlotGenerator.overlaps(100, 200, 150, 250))
        assertFalse(M23SlotGenerator.overlaps(100, 200, 200, 300))
    }

    @Test fun sanitizerRedactsEmail() {
        val scrubbed = M23PrivacySanitizer.scrubPublicText("Contacto test@example.com")
        assertFalse(scrubbed.contains("test@example.com"))
    }

    @Test fun publicBookingContextHasNoInternalIds() {
        val booking = M23Booking("id", M23MockProviderRefs.ACTIVE_MULTI_BRANCH, M23MockOfferingIds.BATH, M23MockUsers.CUSTOMER,
            Instant.parse("2030-02-01T15:00:00Z"), Instant.parse("2030-02-01T16:00:00Z"), zone, M23BookingModality.IN_PERSON,
            M23BookingStatus.CONFIRMED, createdAt = Instant.now(clock), updatedAt = Instant.now(clock))
        val public = M23PrivacySanitizer.publicContext(booking, "Patitas", "Baño")
        assertFalse(public.toString().contains("mock_user"))
        assertFalse(public.toString().contains("m23_booking"))
    }

    @Test fun mockSeedsDeterministic() = runBlocking {
        val a = bookingRepo().observeMyBookings().first().map { it.booking.id }
        val b = bookingRepo().observeMyBookings().first().map { it.booking.id }
        assertEquals(a, b)
    }

    @Test fun draftProviderHasLimitedRules() = runBlocking {
        val rules = MockM23AvailabilityRepository(availabilityStore(), clock).observeRules(M23MockProviderRefs.DRAFT).first()
        assertTrue(rules.isNotEmpty())
    }

    @Test fun slotPageBounded() {
        val page = M23SlotGenerator.generate(
            M23SlotQuery(M23MockProviderRefs.ACTIVE_MULTI_BRANCH, M23MockOfferingIds.BATH, LocalDate.of(2030, 1, 1), LocalDate.of(2030, 12, 31), zone),
            availabilityStore().rules.value, availabilityStore().exceptions.value, availabilityStore().bookings.value, maxDays = 14
        )
        assertEquals(14, page.days.size)
        assertNotNull(page.nextDate)
    }
}
