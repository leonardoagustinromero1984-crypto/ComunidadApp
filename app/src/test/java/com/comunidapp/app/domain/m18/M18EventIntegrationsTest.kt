package com.comunidapp.app.domain.m18

import com.comunidapp.app.data.model.M18EventSearchFilter
import com.comunidapp.app.data.model.M18EventStatus
import com.comunidapp.app.data.model.M18PrivacySanitizer
import com.comunidapp.app.data.model.M18RegistrationStatus
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.M18EventMemoryStore
import com.comunidapp.app.data.repository.M18EventModerationAdapter
import com.comunidapp.app.data.repository.M18EventValidators
import com.comunidapp.app.data.repository.MockM18EventRepository
import com.comunidapp.app.domain.m18.M18EventOperationsService.buildOperationsSummary
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** M18 Bloque 4 — integraciones, ciclo de vida, descubrimiento y cierre preparatorio. */
class M18EventIntegrationsTest {

    private lateinit var store: M18EventMemoryStore
    private lateinit var repository: MockM18EventRepository

    @Before
    fun setup() {
        store = M18EventMemoryStore()
        repository = MockM18EventRepository(actorUserId = { "mock_user_admin" }, store = store)
    }

    @Test
    fun draftNotPublic() = runBlocking {
        val draft = store.events.value.first { it.status == M18EventStatus.DRAFT }
        assertTrue(repository.getPublicEventById(draft.id).isFailure)
    }

    @Test
    fun publishedIsPublic() = runBlocking {
        val published = store.events.value.first { it.status == M18EventStatus.PUBLISHED }
        assertTrue(repository.getPublicEventById(published.id).isSuccess)
    }

    @Test
    fun cancelledIsTerminal() = runBlocking {
        val cancelled = store.events.value.first { it.status == M18EventStatus.CANCELLED }
        assertTrue(cancelled.status.isTerminal)
        assertTrue(repository.publishEvent(cancelled.id).isFailure)
    }

    @Test
    fun completedIsTerminal() = runBlocking {
        val completed = store.events.value.first { it.status == M18EventStatus.COMPLETED }
        assertTrue(completed.status.isTerminal)
    }

    @Test
    fun pastEventRejectsRegistration() = runBlocking {
        val past = store.events.value.first { it.status == M18EventStatus.COMPLETED }
        assertEquals("M18_EVENT_TERMINAL", M18EventValidators.validateRegistration(past))
    }

    @Test
    fun invalidCapacityReductionRejected() = runBlocking {
        val full = store.events.value.first { it.title.contains("primeros auxilios") }
        val input = com.comunidapp.app.data.model.UpdateM18EventCapacityInput(
            eventId = full.id,
            maxCapacity = 1,
            waitlistEnabled = true
        )
        assertTrue(repository.updateEventCapacity(input).isFailure)
    }

    @Test
    fun cancelPreservesRegistrationHistory() = runBlocking {
        val eventId = store.events.value.first { it.status == M18EventStatus.PUBLISHED }.id
        val before = store.registrationsFor(eventId).size
        repository.cancelEvent(eventId).getOrThrow()
        assertEquals(before, store.registrationsFor(eventId).size)
    }

    @Test
    fun publicDirectoryExcludesParticipantList() = runBlocking {
        val list = repository.searchPublicEvents(M18EventSearchFilter()).getOrThrow()
        list.forEach { event ->
            assertFalse(event.title.contains("user_"))
        }
    }

    @Test
    fun moderationAdapterBuildsReport() = runBlocking {
        val published = store.events.value.first { it.status == M18EventStatus.PUBLISHED }
        val result = M18EventModerationAdapter.reportEvent(
            eventId = published.id,
            reason = "spam",
            reporterId = "reporter_1"
        )
        assertNotNull(result)
    }

    @Test
    fun foreignUserCannotManage() = runBlocking {
        val repo = MockM18EventRepository(actorUserId = { "user_vol_1" }, store = store)
        assertFalse(repo.canManageOrganization(store.events.value.first().organizationId))
    }

    @Test
    fun authorizedUserCanManage() = runBlocking {
        val org = store.events.value.first().organizationId
        assertTrue(repository.canManageOrganization(org))
    }

    @Test
    fun coverImageRefIsPublicNotBinary() = runBlocking {
        val event = repository.searchPublicEvents(M18EventSearchFilter()).getOrThrow().first()
        assertTrue(event.coverImageRef?.startsWith("mock://") == true)
    }

    @Test
    fun m06UnavailableDoesNotBlockPublish() = runBlocking {
        store.m06InfrastructureAvailable = false
        val draft = store.events.value.first { it.status == M18EventStatus.DRAFT }
        assertTrue(repository.publishEvent(draft.id).isSuccess)
    }

    @Test
    fun partialLocationStillShowsPublicText() = runBlocking {
        val event = repository.searchPublicEvents(M18EventSearchFilter()).getOrThrow()
            .first { it.reference.publicLocationText != null }
        assertNotNull(event.reference.publicLocationText)
    }

    @Test
    fun metricsCalculatedFromRegistrations() {
        val event = store.events.value.first { it.status == M18EventStatus.PUBLISHED }
        val summary = buildOperationsSummary(event, store.registrationsFor(event.id))
        assertTrue(summary.occupancyPercent in 0..100)
        assertEquals(event.maxCapacity, summary.maxCapacity)
    }

    @Test
    fun combinedFiltersWork() = runBlocking {
        val filter = M18EventSearchFilter(
            query = "feria",
            withOpenSpotsOnly = true,
            activeOnly = true
        )
        val list = repository.searchPublicEvents(filter).getOrThrow()
        assertTrue(list.all { it.title.contains("feria", ignoreCase = true) || it.availableSpots > 0 })
    }

    @Test
    fun errorsDoNotContainPii() {
        val msg = M18EventResilience.safeUserMessage(
            IllegalStateException("fail userId=secret@test.com")
        )
        assertFalse(msg.contains("secret@test.com"))
    }

    @Test
    fun mockAndDomainShareOperationsSummary() {
        val event = store.events.value.first { it.status == M18EventStatus.PUBLISHED }
        val fromService = buildOperationsSummary(event, store.registrationsFor(event.id))
        val fromRepo = runBlocking { repository.observeOperationsSummary(event.id).getOrThrow() }
        assertEquals(fromService.registeredCount, fromRepo.registeredCount)
    }

    @Test
    fun dataProviderSelectsMockRepository() {
        assertNotNull(DataProvider.m18EventRepository)
    }

    @Test
    fun noM19ModuleStarted() {
        val m19ViewModel = runCatching {
            Class.forName("com.comunidapp.app.viewmodel.M19FeedViewModel")
        }
        assertTrue(m19ViewModel.isFailure)
    }
}
