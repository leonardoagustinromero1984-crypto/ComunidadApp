package com.comunidapp.app.data.repository

import com.comunidapp.app.data.mock.M16IntegrationPetIds
import com.comunidapp.app.data.model.CreateM18EventInput
import com.comunidapp.app.data.model.M18CapacityCalculator
import com.comunidapp.app.data.model.M18CommunityEvent
import com.comunidapp.app.data.model.M18EventCapacitySummary
import com.comunidapp.app.data.model.M18EventReference
import com.comunidapp.app.data.model.M18EventRegistration
import com.comunidapp.app.data.model.M18EventReminder
import com.comunidapp.app.data.model.M18EventSearchFilter
import com.comunidapp.app.data.model.M18EventStatus
import com.comunidapp.app.data.model.M18EventType
import com.comunidapp.app.data.model.M18MockOrganizations
import com.comunidapp.app.data.model.M18PrivacySanitizer
import com.comunidapp.app.data.model.M18PublicEvent
import com.comunidapp.app.data.model.M18PublicRegistrationStats
import com.comunidapp.app.data.model.M18RegistrationStatus
import com.comunidapp.app.data.model.M18ReminderStatus
import com.comunidapp.app.data.model.M18_ELIGIBLE_ORGANIZATION_TYPES
import com.comunidapp.app.data.model.UpdateM18EventCapacityInput
import com.comunidapp.app.data.model.UpdateM18EventDetailsInput
import com.comunidapp.app.data.remote.supabase.m18.M18EventErrorMapper
import com.comunidapp.app.data.remote.supabase.m18.M18Exception
import com.comunidapp.app.domain.organization.OrganizationType
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** LeoVer M18 — store + contratos + mock (Bloque 1, sin red). */

class M18EventMemoryStore {
    private val idSeq = AtomicLong(0)
    private val mutex = Mutex()
    private val _events = MutableStateFlow<List<M18CommunityEvent>>(emptyList())
    private val _registrations = MutableStateFlow<List<M18EventRegistration>>(emptyList())
    private val _reminders = MutableStateFlow<List<M18EventReminder>>(emptyList())
    private val idempotentRetries = AtomicInteger(0)

    val organizationTypes = MutableStateFlow<Map<String, OrganizationType>>(emptyMap())
    val organizationManagers = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val organizationDisplayNames = MutableStateFlow<Map<String, String>>(emptyMap())
    var m06InfrastructureAvailable: Boolean = false
    var seeded: Boolean = false

    val events: StateFlow<List<M18CommunityEvent>> = _events.asStateFlow()
    val registrations: StateFlow<List<M18EventRegistration>> = _registrations.asStateFlow()
    val reminders: StateFlow<List<M18EventReminder>> = _reminders.asStateFlow()

    fun nextId(prefix: String): String = "${prefix}_${idSeq.incrementAndGet()}"

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }

    fun upsertEvent(event: M18CommunityEvent) {
        _events.update { list ->
            (list.filterNot { it.id == event.id } + event).sortedByDescending { it.startsAt }
        }
    }

    fun upsertRegistration(registration: M18EventRegistration) {
        _registrations.update { list ->
            (list.filterNot { it.id == registration.id } + registration).sortedByDescending { it.registeredAt }
        }
    }

    fun upsertReminder(reminder: M18EventReminder) {
        _reminders.update { list ->
            (list.filterNot { it.id == reminder.id } + reminder)
        }
    }

    fun recordIdempotentRetry() {
        idempotentRetries.incrementAndGet()
    }

    fun idempotentRetryCount(): Int = idempotentRetries.get()

    fun registrationsFor(eventId: String): List<M18EventRegistration> =
        _registrations.value.filter { it.eventId == eventId }

    fun registrationForUser(eventId: String, userId: String): M18EventRegistration? =
        _registrations.value.firstOrNull { it.eventId == eventId && it.userId == userId }

    fun seedDefaults(actorUserId: String = "mock_user_admin") {
        if (seeded) return
        seeded = true
        val now = System.currentTimeMillis()
        organizationTypes.value = mapOf(
            M18MockOrganizations.ORG_NORTE to OrganizationType.SHELTER,
            M18MockOrganizations.ORG_SUR to OrganizationType.RESCUE_GROUP,
            M18MockOrganizations.ORG_OESTE to OrganizationType.NGO,
            "org_educador_demo" to OrganizationType.TRAINING_CENTER
        )
        organizationManagers.value = mapOf(
            M18MockOrganizations.ORG_NORTE to setOf(actorUserId),
            M18MockOrganizations.ORG_SUR to setOf(actorUserId),
            M18MockOrganizations.ORG_OESTE to setOf(actorUserId)
        )
        organizationDisplayNames.value = mapOf(
            M18MockOrganizations.ORG_NORTE to "Refugio Comunitario Norte",
            M18MockOrganizations.ORG_SUR to "Rescate Sur",
            M18MockOrganizations.ORG_OESTE to "Red Solidaria Oeste"
        )

        val eFair = event(
            id = nextId("m18_event"),
            org = M18MockOrganizations.ORG_NORTE,
            title = "Feria de adopciones — Parque Norte",
            type = M18EventType.ADOPTION_FAIR,
            status = M18EventStatus.PUBLISHED,
            capacity = 50,
            ref = M18EventReference(
                shelterProfileId = "m16_shelter_1",
                shelterPublicName = "Refugio Comunitario Norte",
                publicLocationText = "Parque Norte · CABA"
            ),
            venue = "Parque Norte",
            actor = actorUserId,
            now = now,
            startsOffsetDays = 7
        )
        val eVolunteer = event(
            id = nextId("m18_event"),
            org = M18MockOrganizations.ORG_SUR,
            title = "Jornada de voluntariado",
            type = M18EventType.VOLUNTEER_DAY,
            status = M18EventStatus.PUBLISHED,
            capacity = 20,
            actor = actorUserId,
            now = now,
            startsOffsetDays = 14
        )
        val eFull = event(
            id = nextId("m18_event"),
            org = M18MockOrganizations.ORG_NORTE,
            title = "Taller de primeros auxilios caninos",
            type = M18EventType.TRAINING_WORKSHOP,
            status = M18EventStatus.PUBLISHED,
            capacity = 3,
            actor = actorUserId,
            now = now,
            startsOffsetDays = 10
        )
        val ePaused = event(
            id = nextId("m18_event"),
            org = M18MockOrganizations.ORG_OESTE,
            title = "Encuentro comunitario (pausado)",
            type = M18EventType.COMMUNITY_GATHERING,
            status = M18EventStatus.PAUSED,
            capacity = 30,
            actor = actorUserId,
            now = now - 86_400_000L,
            startsOffsetDays = 21
        )
        val eCompleted = event(
            id = nextId("m18_event"),
            org = M18MockOrganizations.ORG_OESTE,
            title = "Caminata de concientización",
            type = M18EventType.AWARENESS_WALK,
            status = M18EventStatus.COMPLETED,
            capacity = 100,
            actor = actorUserId,
            now = now - 604_800_000L,
            startsOffsetDays = -7
        )
        val eCancelled = event(
            id = nextId("m18_event"),
            org = M18MockOrganizations.ORG_SUR,
            title = "Evento cancelado",
            type = M18EventType.FREE_FUNDRAISER,
            status = M18EventStatus.CANCELLED,
            capacity = 40,
            actor = actorUserId,
            now = now - 900_000_000L,
            startsOffsetDays = 30
        )
        val eDraft = event(
            id = nextId("m18_event"),
            org = M18MockOrganizations.ORG_NORTE,
            title = "Borrador — feria fin de mes",
            type = M18EventType.ADOPTION_FAIR,
            status = M18EventStatus.DRAFT,
            capacity = 25,
            actor = actorUserId,
            now = now,
            startsOffsetDays = 28
        )
        val eOpenSpots = event(
            id = nextId("m18_event"),
            org = M18MockOrganizations.ORG_NORTE,
            title = "Charla tenencia responsable",
            type = M18EventType.TRAINING_WORKSHOP,
            status = M18EventStatus.PUBLISHED,
            capacity = 15,
            ref = M18EventReference(
                petId = M16IntegrationPetIds.PET_HOUSED,
                petPublicName = "Bruno",
                publicLocationText = "Sede Norte · CABA"
            ),
            venue = "Sede Norte",
            actor = actorUserId,
            now = now,
            startsOffsetDays = 5
        )

        listOf(eFair, eVolunteer, eFull, ePaused, eCompleted, eCancelled, eDraft, eOpenSpots)
            .forEach { upsertEvent(it) }

        seedRegistrations(eFair.id, eVolunteer.id, eFull.id, eCompleted.id, actorUserId, now)
    }

    private fun event(
        id: String,
        org: String,
        title: String,
        type: M18EventType,
        status: M18EventStatus,
        capacity: Int,
        ref: M18EventReference = M18EventReference(),
        venue: String? = null,
        actor: String,
        now: Long,
        startsOffsetDays: Int
    ): M18CommunityEvent {
        val startsAt = now + startsOffsetDays * 86_400_000L + 14 * 3_600_000L
        val endsAt = startsAt + 3 * 3_600_000L
        return M18CommunityEvent(
            id = id,
            organizationId = org,
            organizationDisplayName = organizationDisplayNames.value[org] ?: org,
            title = title,
            description = "Evento comunitario mock M18. Sin venta de entradas ni pagos en Bloque 1.",
            eventType = type,
            status = status,
            venueName = venue,
            reference = ref,
            coverImageRef = "mock://m18/cover/$id",
            maxCapacity = capacity,
            waitlistEnabled = true,
            startsAt = startsAt,
            endsAt = endsAt,
            checkInOpensAt = startsAt - 3_600_000L,
            checkInClosesAt = endsAt,
            createdBy = actor,
            createdAt = now,
            updatedAt = now
        )
    }

    private fun seedRegistrations(
        fairId: String,
        volunteerId: String,
        fullId: String,
        completedId: String,
        actorUserId: String,
        now: Long
    ) {
        val samples = listOf(
            reg(fairId, "user_vol_1", M18RegistrationStatus.REGISTERED, "Vol. Ana", now),
            reg(fairId, "user_vol_2", M18RegistrationStatus.REGISTERED, "Vol. Carlos", now),
            reg(fairId, actorUserId, M18RegistrationStatus.REGISTERED, "Admin mock", now, reminder = true),
            reg(volunteerId, actorUserId, M18RegistrationStatus.WAITLISTED, "Admin mock", now),
            reg(fullId, "user_full_1", M18RegistrationStatus.REGISTERED, "U1", now),
            reg(fullId, "user_full_2", M18RegistrationStatus.REGISTERED, "U2", now),
            reg(fullId, "user_full_3", M18RegistrationStatus.REGISTERED, "U3", now),
            reg(fullId, "user_wait_1", M18RegistrationStatus.WAITLISTED, "Wait 1", now),
            reg(completedId, "user_done_1", M18RegistrationStatus.CHECKED_IN, "Asistente", now - 604_800_000L),
            reg(completedId, "user_done_2", M18RegistrationStatus.NO_SHOW, "Ausente", now - 604_800_000L)
        )
        samples.forEach { upsertRegistration(it) }
    }

    private fun reg(
        eventId: String,
        userId: String,
        status: M18RegistrationStatus,
        name: String,
        now: Long,
        reminder: Boolean = false
    ) = M18EventRegistration(
        id = nextId("m18_reg"),
        eventId = eventId,
        userId = userId,
        status = status,
        attendeeDisplayName = name,
        registeredAt = now,
        checkedInAt = if (status == M18RegistrationStatus.CHECKED_IN) now else null,
        reminderScheduled = reminder
    )
}

interface M18EventRepository {
    fun observeEventById(eventId: String): Flow<M18CommunityEvent?>
    fun observeEventsForOrganization(organizationId: String): Flow<List<M18CommunityEvent>>
    suspend fun searchPublicEvents(filter: M18EventSearchFilter): Result<List<M18PublicEvent>>
    suspend fun getPublicEventById(eventId: String): Result<M18PublicEvent>
    suspend fun createEvent(input: CreateM18EventInput): Result<M18CommunityEvent>
    suspend fun updateEventDetails(input: UpdateM18EventDetailsInput): Result<M18CommunityEvent>
    suspend fun updateEventCapacity(input: UpdateM18EventCapacityInput): Result<M18CommunityEvent>
    suspend fun publishEvent(eventId: String): Result<M18CommunityEvent>
    suspend fun pauseEvent(eventId: String): Result<M18CommunityEvent>
    suspend fun completeEvent(eventId: String): Result<M18CommunityEvent>
    suspend fun cancelEvent(eventId: String): Result<M18CommunityEvent>
    suspend fun observeCapacitySummary(eventId: String): Result<M18EventCapacitySummary>
    suspend fun observePublicRegistrationStats(eventId: String): Result<M18PublicRegistrationStats>
    suspend fun registerForEvent(eventId: String): Result<M18EventRegistration>
    suspend fun cancelRegistration(eventId: String): Result<M18EventRegistration>
    suspend fun checkInRegistration(registrationId: String): Result<M18EventRegistration>
    suspend fun scheduleReminder(eventId: String): Result<M18EventReminder>
    suspend fun refreshEvent(eventId: String): Result<M18CommunityEvent>
    suspend fun canManageOrganization(organizationId: String): Boolean
    suspend fun isOrganizationEligible(organizationId: String): Boolean
    suspend fun getMyRegistration(eventId: String): M18EventRegistration?
    suspend fun listRegistrationsForManage(eventId: String): Result<List<M18EventRegistration>>
}

interface M18EventAuthorityPolicy {
    fun canManageEvent(actorUserId: String, organizationId: String, store: M18EventMemoryStore): Boolean
    fun isOrganizationEligible(organizationId: String, store: M18EventMemoryStore): Boolean
}

class MockM18EventAuthorityPolicy : M18EventAuthorityPolicy {
    override fun canManageEvent(
        actorUserId: String,
        organizationId: String,
        store: M18EventMemoryStore
    ): Boolean = store.organizationManagers.value[organizationId]?.contains(actorUserId) == true

    override fun isOrganizationEligible(organizationId: String, store: M18EventMemoryStore): Boolean {
        val type = store.organizationTypes.value[organizationId] ?: return false
        return type in M18_ELIGIBLE_ORGANIZATION_TYPES
    }
}

private fun failM18(code: String): Nothing =
    throw M18Exception(code, M18EventErrorMapper.userMessage(code))

class MockM18EventRepository(
    private val actorUserId: () -> String?,
    private val store: M18EventMemoryStore = M18EventMemoryStore(),
    private val authority: M18EventAuthorityPolicy = MockM18EventAuthorityPolicy()
) : M18EventRepository {

    init {
        store.seedDefaults(actorUserId() ?: "mock_user_admin")
    }

    private fun requireActor(): String =
        actorUserId() ?: failM18("NOT_AUTHENTICATED")

    private fun requireManage(orgId: String, actor: String) {
        if (!authority.isOrganizationEligible(orgId, store)) failM18("M18_ORGANIZATION_NOT_ELIGIBLE")
        if (!authority.canManageEvent(actor, orgId, store)) failM18("M18_PERMISSION_DENIED")
    }

    private fun getEventOrFail(id: String): M18CommunityEvent =
        store.events.value.firstOrNull { it.id == id } ?: failM18("M18_EVENT_NOT_FOUND")

    private fun summaryFor(event: M18CommunityEvent): M18EventCapacitySummary =
        M18CapacityCalculator.summarize(
            event.maxCapacity,
            event.waitlistEnabled,
            store.registrationsFor(event.id)
        )

    override fun observeEventById(eventId: String): Flow<M18CommunityEvent?> =
        store.events.map { list -> list.firstOrNull { it.id == eventId } }

    override fun observeEventsForOrganization(organizationId: String): Flow<List<M18CommunityEvent>> =
        store.events.map { list -> list.filter { it.organizationId == organizationId } }

    override suspend fun searchPublicEvents(filter: M18EventSearchFilter): Result<List<M18PublicEvent>> =
        runCatching {
            val now = System.currentTimeMillis()
            store.events.value
                .filter { e ->
                    when {
                        filter.completedOnly -> e.status == M18EventStatus.COMPLETED
                        filter.activeOnly -> e.status == M18EventStatus.PUBLISHED
                        else -> e.status.isPublic
                    }
                }
                .filter { e ->
                    !filter.upcomingOnly || e.endsAt >= now || e.status == M18EventStatus.COMPLETED
                }
                .filter { e ->
                    filter.query.isBlank() ||
                        e.title.contains(filter.query, ignoreCase = true) ||
                        e.description.contains(filter.query, ignoreCase = true)
                }
                .filter { e -> filter.type == null || e.eventType == filter.type }
                .filter { e -> filter.organizationId == null || e.organizationId == filter.organizationId }
                .filter { e ->
                    !filter.withOpenSpotsOnly || summaryFor(e).availableSpots > 0
                }
                .map { e -> e.toPublicEvent(summaryFor(e)) }
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { M18EventErrorMapper.failure(it) }
        )

    override suspend fun getPublicEventById(eventId: String): Result<M18PublicEvent> =
        runCatching {
            val e = getEventOrFail(eventId)
            if (!e.status.isPublic && e.status != M18EventStatus.CANCELLED) {
                if (e.status == M18EventStatus.DRAFT) failM18("M18_EVENT_NOT_PUBLIC")
            }
            if (e.status == M18EventStatus.CANCELLED) failM18("M18_EVENT_TERMINAL")
            if (e.status != M18EventStatus.PUBLISHED &&
                e.status != M18EventStatus.PAUSED &&
                e.status != M18EventStatus.COMPLETED
            ) {
                failM18("M18_EVENT_NOT_PUBLIC")
            }
            e.toPublicEvent(summaryFor(e))
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { M18EventErrorMapper.failure(it) }
        )

    override suspend fun createEvent(input: CreateM18EventInput): Result<M18CommunityEvent> =
        store.withLock {
            runCatching {
                val actor = requireActor()
                requireManage(input.organizationId, actor)
                M18EventValidators.validateTitle(input.title)?.let { failM18(it) }
                M18EventValidators.validateDescription(input.description)?.let { failM18(it) }
                M18EventValidators.validateCapacity(input.maxCapacity)?.let { failM18(it) }
                M18EventValidators.validateDateRange(input.startsAt, input.endsAt)?.let { failM18(it) }
                M18EventValidators.validateCheckInWindow(
                    input.checkInOpensAt, input.checkInClosesAt, input.startsAt, input.endsAt
                )?.let { failM18(it) }
                val now = System.currentTimeMillis()
                val event = M18CommunityEvent(
                    id = store.nextId("m18_event"),
                    organizationId = input.organizationId,
                    organizationDisplayName = store.organizationDisplayNames.value[input.organizationId]
                        ?: input.organizationId,
                    title = input.title.trim(),
                    description = input.description.trim(),
                    eventType = input.eventType,
                    status = M18EventStatus.DRAFT,
                    venueName = input.venueName?.trim(),
                    reference = input.reference,
                    coverImageRef = input.coverImageRef,
                    maxCapacity = input.maxCapacity,
                    waitlistEnabled = input.waitlistEnabled,
                    startsAt = input.startsAt,
                    endsAt = input.endsAt,
                    checkInOpensAt = input.checkInOpensAt,
                    checkInClosesAt = input.checkInClosesAt,
                    createdBy = actor,
                    createdAt = now,
                    updatedAt = now
                )
                store.upsertEvent(event)
                event
            }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { M18EventErrorMapper.failure(it) }
            )
        }

    override suspend fun updateEventDetails(input: UpdateM18EventDetailsInput): Result<M18CommunityEvent> =
        mutate(input.eventId) { e, _ ->
            if (e.status != M18EventStatus.DRAFT && e.status != M18EventStatus.PUBLISHED &&
                e.status != M18EventStatus.PAUSED
            ) {
                failM18("M18_INVALID_STATE_TRANSITION")
            }
            M18EventValidators.validateTitle(input.title)?.let { failM18(it) }
            M18EventValidators.validateDescription(input.description)?.let { failM18(it) }
            M18EventValidators.validateDateRange(input.startsAt, input.endsAt)?.let { failM18(it) }
            e.copy(
                title = input.title.trim(),
                description = input.description.trim(),
                eventType = input.eventType,
                venueName = input.venueName?.trim(),
                reference = input.reference,
                startsAt = input.startsAt,
                endsAt = input.endsAt,
                updatedAt = System.currentTimeMillis()
            )
        }

    override suspend fun updateEventCapacity(input: UpdateM18EventCapacityInput): Result<M18CommunityEvent> =
        mutate(input.eventId) { e, _ ->
            M18EventValidators.validateCapacityReduction(
                e, input.maxCapacity, store.registrationsFor(e.id)
            )?.let { failM18(it) }
            e.copy(
                maxCapacity = input.maxCapacity,
                waitlistEnabled = input.waitlistEnabled,
                updatedAt = System.currentTimeMillis()
            )
        }

    override suspend fun publishEvent(eventId: String): Result<M18CommunityEvent> =
        transition(eventId, M18EventStatus.PUBLISHED)

    override suspend fun pauseEvent(eventId: String): Result<M18CommunityEvent> =
        transition(eventId, M18EventStatus.PAUSED)

    override suspend fun completeEvent(eventId: String): Result<M18CommunityEvent> =
        transition(eventId, M18EventStatus.COMPLETED)

    override suspend fun cancelEvent(eventId: String): Result<M18CommunityEvent> =
        transition(eventId, M18EventStatus.CANCELLED)

    override suspend fun observeCapacitySummary(eventId: String): Result<M18EventCapacitySummary> =
        runCatching { summaryFor(getEventOrFail(eventId)) }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { M18EventErrorMapper.failure(it) }
        )

    override suspend fun observePublicRegistrationStats(eventId: String): Result<M18PublicRegistrationStats> =
        runCatching {
            getEventOrFail(eventId)
            M18CapacityCalculator.registrationStats(store.registrationsFor(eventId))
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { M18EventErrorMapper.failure(it) }
        )

    override suspend fun registerForEvent(eventId: String): Result<M18EventRegistration> =
        store.withLock {
            runCatching {
                val actor = requireActor()
                val event = getEventOrFail(eventId)
                M18EventValidators.validateRegistration(event)?.let { failM18(it) }
                val existing = store.registrationForUser(eventId, actor)
                if (existing != null) {
                    when (existing.status) {
                        M18RegistrationStatus.REGISTERED,
                        M18RegistrationStatus.WAITLISTED,
                        M18RegistrationStatus.CHECKED_IN -> {
                            store.recordIdempotentRetry()
                            return@runCatching existing
                        }
                        M18RegistrationStatus.CANCELLED -> { /* allow re-register */ }
                        M18RegistrationStatus.NO_SHOW -> { /* allow re-register */ }
                    }
                }
                val summary = summaryFor(event)
                val status = when {
                    summary.availableSpots > 0 -> M18RegistrationStatus.REGISTERED
                    summary.isWaitlistOpen -> M18RegistrationStatus.WAITLISTED
                    else -> failM18("M18_EVENT_FULL")
                }
                val registration = M18EventRegistration(
                    id = existing?.id ?: store.nextId("m18_reg"),
                    eventId = eventId,
                    userId = actor,
                    status = status,
                    attendeeDisplayName = "Participante mock",
                    registeredAt = System.currentTimeMillis()
                )
                store.upsertRegistration(registration)
                registration
            }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { M18EventErrorMapper.failure(it) }
            )
        }

    override suspend fun cancelRegistration(eventId: String): Result<M18EventRegistration> =
        store.withLock {
            runCatching {
                val actor = requireActor()
                val registration = store.registrationForUser(eventId, actor)
                    ?: failM18("M18_REGISTRATION_NOT_FOUND")
                if (registration.status.isTerminal) {
                    store.recordIdempotentRetry()
                    return@runCatching registration
                }
                val cancelled = registration.copy(status = M18RegistrationStatus.CANCELLED)
                store.upsertRegistration(cancelled)
                promoteWaitlist(eventId)
                cancelled
            }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { M18EventErrorMapper.failure(it) }
            )
        }

    private fun promoteWaitlist(eventId: String) {
        val event = store.events.value.firstOrNull { it.id == eventId } ?: return
        val summary = summaryFor(event)
        if (summary.availableSpots <= 0) return
        val nextWait = store.registrationsFor(eventId)
            .filter { it.status == M18RegistrationStatus.WAITLISTED }
            .minByOrNull { it.registeredAt } ?: return
        store.upsertRegistration(nextWait.copy(status = M18RegistrationStatus.REGISTERED))
    }

    override suspend fun checkInRegistration(registrationId: String): Result<M18EventRegistration> =
        store.withLock {
            runCatching {
                val actor = requireActor()
                val registration = store.registrations.value.firstOrNull { it.id == registrationId }
                    ?: failM18("M18_REGISTRATION_NOT_FOUND")
                val event = getEventOrFail(registration.eventId)
                requireManage(event.organizationId, actor)
                if (registration.status == M18RegistrationStatus.CHECKED_IN) {
                    store.recordIdempotentRetry()
                    return@runCatching registration
                }
                M18EventValidators.validateCheckIn(event, registration)?.let { failM18(it) }
                val checkedIn = registration.copy(
                    status = M18RegistrationStatus.CHECKED_IN,
                    checkedInAt = System.currentTimeMillis()
                )
                store.upsertRegistration(checkedIn)
                checkedIn
            }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { M18EventErrorMapper.failure(it) }
            )
        }

    override suspend fun scheduleReminder(eventId: String): Result<M18EventReminder> =
        store.withLock {
            runCatching {
                val actor = requireActor()
                val event = getEventOrFail(eventId)
                M18EventValidators.validateRegistration(event)?.let { failM18(it) }
                val registration = store.registrationForUser(eventId, actor)
                    ?: failM18("M18_REGISTRATION_NOT_FOUND")
                if (registration.reminderScheduled) {
                    store.recordIdempotentRetry()
                    val existing = store.reminders.value.firstOrNull {
                        it.eventId == eventId && it.userId == actor
                    } ?: failM18("M18_REMINDER_ALREADY_SCHEDULED")
                    return@runCatching existing
                }
                if (!store.m06InfrastructureAvailable) failM18("M18_NOTIFICATION_INFRASTRUCTURE_UNAVAILABLE")
                val reminder = M18EventReminder(
                    id = store.nextId("m18_reminder"),
                    eventId = eventId,
                    userId = actor,
                    scheduledFor = event.startsAt - 86_400_000L,
                    status = M18ReminderStatus.SCHEDULED
                )
                store.upsertReminder(reminder)
                store.upsertRegistration(registration.copy(reminderScheduled = true))
                reminder
            }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { M18EventErrorMapper.failure(it) }
            )
        }

    override suspend fun refreshEvent(eventId: String): Result<M18CommunityEvent> =
        runCatching { getEventOrFail(eventId) }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { M18EventErrorMapper.failure(it) }
        )

    override suspend fun canManageOrganization(organizationId: String): Boolean {
        val actor = actorUserId() ?: return false
        return authority.canManageEvent(actor, organizationId, store)
    }

    override suspend fun isOrganizationEligible(organizationId: String): Boolean =
        authority.isOrganizationEligible(organizationId, store)

    override suspend fun getMyRegistration(eventId: String): M18EventRegistration? {
        val actor = actorUserId() ?: return null
        return store.registrationForUser(eventId, actor)
    }

    override suspend fun listRegistrationsForManage(eventId: String): Result<List<M18EventRegistration>> =
        runCatching {
            val actor = requireActor()
            val event = getEventOrFail(eventId)
            requireManage(event.organizationId, actor)
            store.registrationsFor(eventId)
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { M18EventErrorMapper.failure(it) }
        )

    private suspend fun transition(
        eventId: String,
        target: M18EventStatus
    ): Result<M18CommunityEvent> = mutate(eventId) { e, _ ->
        M18EventValidators.validateStateTransition(e.status, target)?.let { failM18(it) }
        if (e.status == target) {
            store.recordIdempotentRetry()
            return@mutate e
        }
        e.copy(status = target, updatedAt = System.currentTimeMillis())
    }

    private suspend fun mutate(
        eventId: String,
        block: (M18CommunityEvent, String) -> M18CommunityEvent
    ): Result<M18CommunityEvent> = store.withLock {
        runCatching {
            val actor = requireActor()
            val current = getEventOrFail(eventId)
            requireManage(current.organizationId, actor)
            if (current.status.isTerminal) failM18("M18_STATE_ALREADY_FINAL")
            val updated = block(current, actor)
            store.upsertEvent(updated)
            updated
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { M18EventErrorMapper.failure(it) }
        )
    }
}
