package com.comunidapp.app.domain.m18

import com.comunidapp.app.data.model.M18EventSearchFilter
import com.comunidapp.app.data.model.M18EventStatus
import com.comunidapp.app.data.model.M18EventType
import com.comunidapp.app.data.model.M18MockOrganizations
import com.comunidapp.app.data.model.M18RegistrationStatus
import com.comunidapp.app.data.model.M18ReminderStatus
import com.comunidapp.app.data.model.M18EventReminder
import com.comunidapp.app.data.remote.supabase.m18.M18EventErrorMapper
import com.comunidapp.app.data.repository.M18EventMemoryStore
import com.comunidapp.app.data.repository.MockM18EventRepository
import com.comunidapp.app.domain.RolePermissions
import com.comunidapp.app.domain.capability.PersonCapabilityCode
import com.comunidapp.app.domain.context.OperationalContext
import com.comunidapp.app.ui.UiRegressionGateTest
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LeoVer 17B.9 — descubrir un evento, inscribirse con el estado real
 * y ver solo la actividad propia. El recordatorio no se ofrece: no hay envío.
 */
class Events17B9Test {

    @Test
    fun openingDetailDoesNotRegisterOrWaitlistAndSessionDoesNotLeak() = runBlocking {
        var actor: String? = PERSON
        val store = M18EventMemoryStore()
        val repo = MockM18EventRepository(actorUserId = { actor }, store = store)
        val before = store.registrations.value.size
        val published = repo.searchPublicEvents(M18EventSearchFilter()).getOrThrow()
        assertTrue(published.isNotEmpty())
        assertTrue(published.all { it.status == M18EventStatus.PUBLISHED })
        assertTrue(published.none { it.title.contains("Borrador") || it.title.contains("cancelado") })
        val opened = published.first()
        repo.getPublicEventById(opened.id).getOrThrow()
        assertNull(repo.getMyRegistration(opened.id))
        assertEquals(before, store.registrations.value.size)
        assertTrue(repo.listMyRegistrations().getOrThrow().isEmpty())
        assertFalse(store.registrations.value.any { it.userId == PERSON })
        assertFalse(store.organizationManagers.value.values.any { PERSON in it })

        val open = store.events.value.first { it.title.contains("sin inscriptos") }
        val saved = repo.registerForEvent(open.id).getOrThrow()
        assertEquals(M18RegistrationStatus.REGISTERED, saved.status)
        actor = OTHER
        assertTrue(repo.listMyRegistrations().getOrThrow().isEmpty())
        assertNull(repo.getMyRegistration(open.id))
        store.clearSessionResidue()
        assertFalse(store.registrations.value.any { it.userId == PERSON })
        assertFalse(store.registrations.value.any { it.userId == OTHER })
        actor = PERSON
        assertTrue(repo.listMyRegistrations().getOrThrow().isEmpty())
    }

    @Test
    fun capacityWaitlistAndCancelFollowTheRealRules() = runBlocking {
        val store = M18EventMemoryStore()
        val repo = MockM18EventRepository(actorUserId = { PERSON }, store = store)

        val open = store.events.value.first { it.title.contains("sin inscriptos") }
        val confirmed = repo.registerForEvent(open.id).getOrThrow()
        assertEquals(M18RegistrationStatus.REGISTERED, confirmed.status)
        val again = repo.registerForEvent(open.id).getOrThrow()
        assertEquals(confirmed.id, again.id)
        assertEquals(
            1,
            store.registrations.value.count {
                it.userId == PERSON && it.eventId == open.id && it.status == M18RegistrationStatus.REGISTERED
            }
        )

        store.upsertRegistration(confirmed.copy(reminderScheduled = true))
        store.upsertReminder(
            M18EventReminder(
                id = "rem-person",
                eventId = open.id,
                userId = PERSON,
                scheduledFor = open.startsAt - 86_400_000L,
                status = M18ReminderStatus.SCHEDULED
            )
        )
        val cancelled = repo.cancelRegistration(open.id).getOrThrow()
        assertEquals(M18RegistrationStatus.CANCELLED, cancelled.status)
        assertFalse(cancelled.reminderScheduled)
        assertEquals(M18RegistrationStatus.CANCELLED, repo.getMyRegistration(open.id)?.status)
        assertEquals(
            M18ReminderStatus.SKIPPED,
            store.reminders.value.first { it.userId == PERSON && it.eventId == open.id }.status
        )

        val full = store.events.value.first { it.title.contains("primeros auxilios") }
        val waitlisted = repo.registerForEvent(full.id).getOrThrow()
        assertEquals(M18RegistrationStatus.WAITLISTED, waitlisted.status)
        repo.cancelRegistration(full.id).getOrThrow()

        store.upsertEvent(full.copy(waitlistEnabled = false))
        val rejected = repo.registerForEvent(full.id)
        assertTrue(rejected.isFailure)
        assertEquals("M18_EVENT_FULL", M18EventErrorMapper.codeOf(rejected.exceptionOrNull()!!))
        assertFalse(
            store.registrations.value.any {
                it.userId == PERSON && it.eventId == full.id &&
                    it.status == M18RegistrationStatus.WAITLISTED
            }
        )

        val cancelledEvent = store.events.value.first { it.status == M18EventStatus.CANCELLED }
        val past = store.events.value.first { it.status == M18EventStatus.COMPLETED }
        val paused = store.events.value.first { it.status == M18EventStatus.PAUSED }
        assertEquals("M18_EVENT_TERMINAL", codeOf(repo.registerForEvent(cancelledEvent.id)))
        assertEquals("M18_EVENT_TERMINAL", codeOf(repo.registerForEvent(past.id)))
        assertEquals("M18_EVENT_NOT_OPEN", codeOf(repo.registerForEvent(paused.id)))
        val ended = open.copy(endsAt = System.currentTimeMillis() - 1_000L)
        store.upsertEvent(ended)
        assertEquals("M18_EVENT_TERMINAL", codeOf(repo.registerForEvent(open.id)))
    }

    @Test
    fun cancellingASpotPromotesTheEarliestWaitlistedPerson() = runBlocking {
        val store = M18EventMemoryStore()
        val repo = MockM18EventRepository(actorUserId = { "user_full_1" }, store = store)
        val full = store.events.value.first { it.title.contains("primeros auxilios") }
        assertEquals(
            M18RegistrationStatus.WAITLISTED,
            store.registrations.value.first { it.userId == "user_wait_1" }.status
        )
        val cancelled = repo.cancelRegistration(full.id).getOrThrow()
        assertEquals(M18RegistrationStatus.CANCELLED, cancelled.status)
        assertEquals(
            M18RegistrationStatus.REGISTERED,
            store.registrations.value.first { it.userId == "user_wait_1" }.status
        )
    }

    @Test
    fun presentationIsHumanAndReminderActionIsAbsent() {
        val start = Instant.parse("2026-10-10T18:00:00Z").toEpochMilli()
        val end = Instant.parse("2026-10-10T21:00:00Z").toEpochMilli()
        val line = EventPresentation.whenLine(start, end)
        assertEquals("sábado 10 de octubre · 15:00 a 18:00", line)
        assertFalse(line.contains("America/"))
        assertFalse(line.contains("2026-10-10"))
        assertFalse(line.contains("T18"))
        val nextDay = EventPresentation.whenLine(start, Instant.parse("2026-10-11T21:00:00Z").toEpochMilli())
        assertEquals("sábado 10 de octubre · 15:00", nextDay)

        assertEquals("Quedan 4 lugares", EventPresentation.availability(10, 4, false))
        assertEquals("Queda 1 lugar", EventPresentation.availability(10, 1, false))
        assertEquals("Completo. Hay lista de espera.", EventPresentation.availability(3, 0, true))
        assertEquals("Completo", EventPresentation.availability(3, 0, false))
        assertNull(EventPresentation.availability(0, 0, true))
        assertNull(EventPresentation.placeLine(null, "N/A"))
        assertNull(EventPresentation.placeLine("-", "null"))
        assertEquals("Parque Norte · CABA", EventPresentation.placeLine("Parque Norte", "CABA"))

        M18EventStatus.entries.forEach { status ->
            val label = EventPresentation.eventStatus(status)
            assertFalse(label == status.name)
            assertFalse(label.contains("N/A"))
        }
        M18RegistrationStatus.entries.forEach { status ->
            val label = EventPresentation.registrationStatus(status)
            val own = EventPresentation.ownRegistration(status)
            assertFalse(label == status.name)
            assertFalse(own.contains(status.name))
            assertFalse(own.contains("N/A"))
        }
        assertEquals("Estás inscripto.", EventPresentation.ownRegistration(M18RegistrationStatus.REGISTERED))
        assertEquals("Estás en lista de espera.", EventPresentation.ownRegistration(M18RegistrationStatus.WAITLISTED))
        assertEquals("Asististe.", EventPresentation.ownRegistration(M18RegistrationStatus.ATTENDED))
        assertEquals("No registró asistencia.", EventPresentation.ownRegistration(M18RegistrationStatus.NO_SHOW))
        assertEquals("Feria de adopciones", EventPresentation.eventType(M18EventType.ADOPTION_FAIR))
        assertEquals("Finalizado", EventPresentation.eventStatus(M18EventStatus.COMPLETED))

        val now = start
        val upcoming = sample(M18EventStatus.PUBLISHED, M18RegistrationStatus.REGISTERED, now + 86_400_000L)
        val waiting = sample(M18EventStatus.PUBLISHED, M18RegistrationStatus.WAITLISTED, now + 86_400_000L)
        val history = sample(M18EventStatus.COMPLETED, M18RegistrationStatus.ATTENDED, now - 86_400_000L)
        assertEquals(EventActivityBucket.UPCOMING, EventPresentation.bucket(upcoming, now))
        assertEquals(EventActivityBucket.WAITLIST, EventPresentation.bucket(waiting, now))
        assertEquals(EventActivityBucket.PAST, EventPresentation.bucket(history, now))

        val screens = source("app/src/main/java/com/comunidapp/app/ui/screens/m18/M18EventScreens.kt")
        val detail = functionBody(screens, "fun M18EventDetailScreen(", "fun M18EventOperationsScreen(")
        val card = functionBody(screens, "private fun M18EventCard(", "fun M18EventDetailScreen(")
        val viewModel = source("app/src/main/java/com/comunidapp/app/viewmodel/M18EventViewModels.kt")
        assertTrue(detail.contains("EventPresentation.WAITLIST_BEFORE"))
        assertTrue(detail.contains("EventPresentation.REGISTERED_NOW"))
        assertTrue(card.contains("EventPresentation.whenLine"))
        assertTrue(card.contains("EventPresentation.availability"))
        assertFalse(detail.contains("Programar recordatorio"))
        assertFalse(detail.contains("Las notificaciones no están disponibles para eventos"))
        assertFalse(detail.contains("scheduleReminder"))
        assertFalse(detail.contains("formatEventDateRange"))
        assertFalse(detail.contains("America/"))
        assertFalse(detail.contains("puesto"))
        assertFalse(card.contains(".name"))
        assertFalse(card.contains("America/"))
        assertFalse(screens.contains("Programar recordatorio"))
        assertFalse(viewModel.contains("fun scheduleReminder"))
        assertFalse(viewModel.contains("Las notificaciones no están disponibles para eventos"))
        assertTrue(screens.contains("canAdminister"))
        assertTrue(screens.contains("LeoFilterChip("))
    }

    @Test
    fun personDoesNotManageAndProfileOpensPersonalActivity() = runBlocking {
        assertFalse(RolePermissions.canPublishEvent(OperationalContext.Personal))
        val store = M18EventMemoryStore()
        store.seedDefaults("org_manager")
        val manager = MockM18EventRepository(actorUserId = { "org_manager" }, store = store)
        assertTrue(manager.canManageOrganization(M18MockOrganizations.ORG_NORTE))
        val person = MockM18EventRepository(actorUserId = { PERSON }, store = store)
        val owned = store.events.value.first { it.organizationId == M18MockOrganizations.ORG_NORTE }
        store.upsertEvent(owned.copy(createdBy = PERSON))
        assertFalse(person.canManageOrganization(M18MockOrganizations.ORG_NORTE))
        val open = store.events.value.first { it.title.contains("sin inscriptos") }
        person.registerForEvent(open.id).getOrThrow()
        assertFalse(person.canManageOrganization(open.organizationId))
        assertFalse(store.organizationManagers.value.values.any { PERSON in it })
        assertFalse(PersonCapabilityCode.entries.any { it.storageValue == "VOLUNTEER" })

        val profile = source("app/src/main/java/com/comunidapp/app/ui/screens/profile/ProfileScreen.kt")
        val graph = source("app/src/main/java/com/comunidapp/app/navigation/ComunidappNavGraph.kt")
        val routes = source("app/src/main/java/com/comunidapp/app/navigation/M18NavGraph.kt")
        assertTrue(profile.contains("Mis eventos"))
        assertTrue(graph.contains("onNavigateToMyEvents = { navController.navigate(NavRoutes.M18_MY_EVENTS) }"))
        assertFalse(graph.contains("onNavigateToMyEvents = { navController.navigate(NavRoutes.M18_EVENTS) }"))
        assertTrue(graph.contains("onM18Events = { navController.navigate(NavRoutes.M18_EVENTS) }"))
        assertTrue(routes.contains("NavRoutes.M18_MY_EVENTS"))
        assertTrue(routes.contains("canAdminister = RolePermissions.canPublishEvent(context)"))

        val sql = source("supabase/migrations/086_m18_my_event_activity.sql")
        assertTrue(sql.contains("m18_list_my_event_activity"))
        assertTrue(sql.contains("o.display_name"))
        assertTrue(sql.contains("_m18_require_authenticated()"))
        assertTrue(sql.contains("event_id"))
        assertFalse(sql.contains("create table"))
        assertFalse(sql.contains("m18_event_reminders"))
    }

    private fun sample(
        eventStatus: M18EventStatus,
        registrationStatus: M18RegistrationStatus,
        endsAt: Long
    ) = MyEventRegistration(
        eventId = "evt",
        title = "Charla",
        organizationName = "Refugio",
        startsAt = endsAt - 3_600_000L,
        endsAt = endsAt,
        eventStatus = eventStatus,
        registrationStatus = registrationStatus
    )

    private fun codeOf(result: Result<*>): String =
        M18EventErrorMapper.codeOf(result.exceptionOrNull()!!)

    private fun functionBody(source: String, signature: String, nextSignature: String): String {
        val start = source.indexOf(signature)
        val end = source.indexOf(nextSignature, start + signature.length)
        require(start >= 0 && end > start) { "Missing $signature" }
        return source.substring(start, end)
    }

    private fun source(relative: String): String =
        UiRegressionGateTest.sourceFile(relative).readText()

    private companion object {
        const val PERSON = "person_events_17b9"
        const val OTHER = "other_events_17b9"
    }
}
