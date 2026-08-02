package com.comunidapp.app.domain.m18

import com.comunidapp.app.data.model.M18EventStatus
import com.comunidapp.app.data.model.M18PrivacySanitizer
import com.comunidapp.app.data.model.M18RegistrationStatus
import com.comunidapp.app.data.remote.supabase.m18.M18EventErrorMapper
import com.comunidapp.app.data.remote.supabase.m18.toM18EventCapacitySummary
import com.comunidapp.app.data.remote.supabase.m18.toM18PublicEvent
import com.comunidapp.app.data.remote.supabase.m18.toM18PublicRegistrationStats
import com.comunidapp.app.data.repository.MockM18EventRepository
import com.comunidapp.app.data.repository.SupabaseM18EventRepository
import com.comunidapp.app.data.repository.M18EventValidators
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class M18EventRemoteMapperTest {

    @Test
    fun publicEventMapperOmitsInternalFields() {
        val json = buildJsonObject {
            put("id", "evt-1")
            put("title", "Feria de adopciones")
            put("description", "Descripción pública del evento")
            put("organization_display_name", "Refugio Norte")
            put("event_type", "ADOPTION_FAIR")
            put("status", "PUBLISHED")
            put("max_capacity", 50)
            put("registered_count", 10)
            put("waitlist_count", 2)
            put("available_spots", 40)
            put("is_full", false)
            put("is_waitlist_open", false)
            put("is_registration_open", true)
            put("starts_at", "2026-01-01T00:00:00Z")
            put("ends_at", "2026-01-01T03:00:00Z")
        }
        val public = json.toM18PublicEvent()
        assertEquals("evt-1", public.id)
        assertFalse(public.title.contains("@"))
        assertTrue(public.isRegistrationOpen)
    }

    @Test
    fun capacitySummaryMapperParsesCounts() {
        val json = buildJsonObject {
            put("max_capacity", 20)
            put("registered_count", 18)
            put("waitlist_count", 3)
            put("available_spots", 2)
            put("is_full", false)
            put("is_waitlist_open", false)
        }
        val summary = json.toM18EventCapacitySummary()
        assertEquals(18, summary.registeredCount)
        assertEquals(2, summary.availableSpots)
    }

    @Test
    fun publicRegistrationStatsMapperAggregatesOnly() {
        val json = buildJsonObject {
            put("registered_count", 15)
            put("waitlist_count", 4)
            put("checked_in_count", 8)
        }
        val stats = json.toM18PublicRegistrationStats()
        assertEquals(15, stats.registeredCount)
        assertEquals(8, stats.checkedInCount)
    }

    @Test
    fun draftNotPublicStatus() {
        assertFalse(M18EventStatus.DRAFT.isPublic)
    }

    @Test
    fun terminalCannotReopen() {
        assertEquals(
            "M18_STATE_ALREADY_FINAL",
            M18EventValidators.validateStateTransition(
                M18EventStatus.COMPLETED,
                M18EventStatus.PUBLISHED
            )
        )
    }

    @Test
    fun scrubPublicTextRedactsEmail() {
        val scrubbed = M18PrivacySanitizer.scrubPublicText("Contacto: user@test.com")
        assertTrue(scrubbed.contains("[redactado]"))
    }

    @Test
    fun remoteScheduleReminderUnavailable() {
        val repo = SupabaseM18EventRepository(actorUserId = { "u1" })
        val result = kotlinx.coroutines.runBlocking {
            repo.scheduleReminder("evt-x")
        }
        assertTrue(result.isFailure)
        val code = result.exceptionOrNull()?.let { M18EventErrorMapper.codeOf(it) }
        assertEquals("M18_NOTIFICATION_INFRASTRUCTURE_UNAVAILABLE", code)
    }

    @Test
    fun mockRepositoryStillOperative() {
        val repo = MockM18EventRepository(actorUserId = { "mock_user_admin" })
        val result = kotlinx.coroutines.runBlocking {
            repo.searchPublicEvents(com.comunidapp.app.data.model.M18EventSearchFilter())
        }
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull().orEmpty().isNotEmpty())
    }

    @Test
    fun registrationStatusOccupiesCapacity() {
        assertTrue(M18RegistrationStatus.REGISTERED.occupiesCapacity)
        assertFalse(M18RegistrationStatus.WAITLISTED.occupiesCapacity)
    }
}
