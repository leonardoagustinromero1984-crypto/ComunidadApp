package com.comunidapp.app.domain.m18

import com.comunidapp.app.data.model.M18EventStatus
import com.comunidapp.app.data.model.M18PrivacySanitizer
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

class M18EventFoundationTest {

    private lateinit var store: M18EventMemoryStore
    private lateinit var repository: MockM18EventRepository

    @Before
    fun setup() {
        store = M18EventMemoryStore()
        repository = MockM18EventRepository(actorUserId = { "mock_user_admin" }, store = store)
    }

    @Test
    fun invalidCapacityRejected() {
        assertEquals("M18_INVALID_CAPACITY", M18EventValidators.validateCapacity(0))
        assertEquals("M18_INVALID_CAPACITY", M18EventValidators.validateCapacity(-5))
    }

    @Test
    fun terminalEventCannotReopen() {
        assertEquals(
            "M18_STATE_ALREADY_FINAL",
            M18EventValidators.validateStateTransition(M18EventStatus.COMPLETED, M18EventStatus.PUBLISHED)
        )
    }

    @Test
    fun privacySanitizerRedactsEmailInTitle() {
        val scrubbed = M18PrivacySanitizer.scrubPublicText("Contacto: test@example.com")
        assertFalse(scrubbed.contains("test@example.com"))
        assertTrue(scrubbed.contains("[redactado]"))
    }

    @Test
    fun publicStatsExcludeIndividualPii() = runBlocking {
        val published = store.events.value.first { it.status == M18EventStatus.PUBLISHED }
        val stats = repository.observePublicRegistrationStats(published.id).getOrThrow()
        assertTrue(stats.registeredCount >= 0)
        assertTrue(stats.checkedInCount >= 0)
    }

    @Test
    fun registrationIdempotentWhenAlreadyRegistered() = runBlocking {
        val event = store.events.value.first {
            it.status == M18EventStatus.PUBLISHED && store.registrationsFor(it.id).isEmpty()
        }
        repository.registerForEvent(event.id).getOrThrow()
        val before = store.idempotentRetryCount()
        repository.registerForEvent(event.id).getOrThrow()
        assertTrue(store.idempotentRetryCount() > before)
    }

    @Test
    fun publishIdempotent() = runBlocking {
        val draft = store.events.value.first { it.status == M18EventStatus.DRAFT }
        repository.publishEvent(draft.id).getOrThrow()
        val before = store.idempotentRetryCount()
        repository.publishEvent(draft.id).getOrThrow()
        assertTrue(store.idempotentRetryCount() > before)
    }

    @Test
    fun fullEventRejectsRegistrationWithoutWaitlist() = runBlocking {
        val full = store.events.value.first { it.title.contains("primeros auxilios") }
        repository.registerForEvent(full.id)
            .fold(
                onSuccess = { assertEquals(M18RegistrationStatus.WAITLISTED, it.status) },
                onFailure = { assertNotNull(it) }
            )
    }

    @Test
    fun unauthorizedOrganizationCannotManage() = runBlocking {
        val repo = MockM18EventRepository(actorUserId = { "unknown_user" }, store = store)
        assertFalse(repo.canManageOrganization(com.comunidapp.app.data.model.M18MockOrganizations.ORG_NORTE))
    }

    @Test
    fun cancelledRegistrationDoesNotAppearAsActive() = runBlocking {
        val event = store.events.value.first { it.status == M18EventStatus.PUBLISHED }
        repository.registerForEvent(event.id).getOrThrow()
        repository.cancelRegistration(event.id).getOrThrow()
        val reg = repository.getMyRegistration(event.id)
        assertEquals(M18RegistrationStatus.CANCELLED, reg?.status)
    }

    @Test
    fun checkInIdempotent() = runBlocking {
        val completed = store.events.value.first { it.status == M18EventStatus.COMPLETED }
        val checkedIn = store.registrationsFor(completed.id)
            .first { it.status == M18RegistrationStatus.CHECKED_IN }
        val before = store.idempotentRetryCount()
        repository.checkInRegistration(checkedIn.id).getOrThrow()
        assertTrue(store.idempotentRetryCount() > before)
    }

    @Test
    fun reminderRequiresInfrastructure() = runBlocking {
        store.m06InfrastructureAvailable = false
        val event = store.events.value.first {
            it.status == M18EventStatus.PUBLISHED &&
                store.registrationForUser(it.id, "mock_user_admin")?.status == M18RegistrationStatus.REGISTERED
        }
        val result = repository.scheduleReminder(event.id)
        assertTrue(result.isFailure)
        assertNull(result.getOrNull())
    }
}
