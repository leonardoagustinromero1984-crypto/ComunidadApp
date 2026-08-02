package com.comunidapp.app.domain.m23

import com.comunidapp.app.data.model.M23AvailabilityRuleStatus
import com.comunidapp.app.data.model.M23BookingModality
import com.comunidapp.app.data.model.M23BookingStatus
import com.comunidapp.app.data.model.M23ExceptionType
import com.comunidapp.app.data.remote.supabase.m23.toM23AvailabilityException
import com.comunidapp.app.data.remote.supabase.m23.toM23AvailabilityRule
import com.comunidapp.app.data.remote.supabase.m23.toM23Booking
import com.comunidapp.app.data.remote.supabase.m23.toM23SlotPage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class M23BookingRemoteMapperTest {
    private fun fixture(value: String) = Json.parseToJsonElement(value).jsonObject
    private val booking = """{"id":"b1","provider_id":"p1","offering_id":"o1","customer_user_id":"u1","starts_at":"2030-01-01T10:00:00Z","ends_at":"2030-01-01T10:30:00Z","zone_id":"America/Argentina/Buenos_Aires","modality":"REMOTE","status":"CONFIRMED","customer_note":"Llamar antes","created_at":"2030-01-01T09:00:00Z","updated_at":"2030-01-01T09:01:00Z","client_request_id":"request-1"}"""

    @Test fun mapsBookingId() = assertEquals("b1", fixture(booking).toM23Booking().id)
    @Test fun mapsBookingProvider() = assertEquals("p1", fixture(booking).toM23Booking().providerId)
    @Test fun mapsBookingOffering() = assertEquals("o1", fixture(booking).toM23Booking().offeringId)
    @Test fun mapsBookingCustomer() = assertEquals("u1", fixture(booking).toM23Booking().customerUserId)
    @Test fun mapsBookingTimes() = assertEquals("2030-01-01T10:00:00Z", fixture(booking).toM23Booking().startsAt.toString())
    @Test fun mapsBookingZone() = assertEquals("America/Argentina/Buenos_Aires", fixture(booking).toM23Booking().zoneId.id)
    @Test fun mapsBookingModality() = assertEquals(M23BookingModality.REMOTE, fixture(booking).toM23Booking().modality)
    @Test fun mapsBookingStatus() = assertEquals(M23BookingStatus.CONFIRMED, fixture(booking).toM23Booking().status)
    @Test fun mapsBookingNote() = assertEquals("Llamar antes", fixture(booking).toM23Booking().customerNote)
    @Test fun mapsBookingIdempotency() = assertEquals("request-1", fixture(booking).toM23Booking().idempotencyKey)
    @Test fun mapsMissingBookingNoteAsNull() = assertNull(fixture("""{"id":"b"}""").toM23Booking().customerNote)
    @Test fun defaultsUnknownBookingStatus() = assertEquals(M23BookingStatus.REQUESTED, fixture("""{"status":"OTHER"}""").toM23Booking().status)

    @Test fun mapsAvailabilityRule() {
        val rule = fixture("""{"id":"r1","provider_id":"p1","offering_id":"o1","day_of_week":3,"start_time":"09:00:00","end_time":"10:00:00","slot_duration_minutes":30,"zone_id":"UTC","status":"INACTIVE"}""").toM23AvailabilityRule()
        assertEquals("r1", rule.id); assertEquals(3, rule.dayOfWeek.value); assertEquals(M23AvailabilityRuleStatus.INACTIVE, rule.status)
    }

    @Test fun mapsAvailabilityException() {
        val exception = fixture("""{"id":"e1","provider_id":"p1","date":"2030-02-03","start_time":"09:00:00","end_time":"10:00:00","type":"SPECIAL_OPENING","note":"Extra"}""").toM23AvailabilityException()
        assertEquals(M23ExceptionType.SPECIAL_OPENING, exception.type); assertEquals("Extra", exception.note)
    }

    @Test fun mapsPublicSlotPage() {
        val page = fixture("""{"days":[{"date":"2030-01-01","slots":[{"starts_at":"2030-01-01T10:00:00Z","ends_at":"2030-01-01T10:30:00Z","modality":"IN_PERSON"}]}]}""")
            .toM23SlotPage("p1", "o1", java.time.ZoneId.of("UTC"))
        assertEquals(1, page.days.size); assertEquals("p1", page.days.single().slots.single().providerId)
    }
}
