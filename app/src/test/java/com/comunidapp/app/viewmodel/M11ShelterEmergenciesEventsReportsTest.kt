package com.comunidapp.app.viewmodel

import com.comunidapp.app.data.model.ShelterEmergencyCategory
import com.comunidapp.app.data.model.ShelterEmergencySeverity
import com.comunidapp.app.data.model.ShelterEmergencyStatus
import com.comunidapp.app.data.model.ShelterEmergencyVisibility
import com.comunidapp.app.data.model.ShelterEventRegistrationStatus
import com.comunidapp.app.data.model.ShelterEventStatus
import com.comunidapp.app.data.model.ShelterEventType
import com.comunidapp.app.data.model.ShelterEventVisibility
import com.comunidapp.app.data.model.ShelterStatus
import com.comunidapp.app.data.model.ShelterVolunteerRole
import com.comunidapp.app.data.remote.supabase.m11.M11ShelterErrorMapper
import com.comunidapp.app.data.repository.CreateShelterEmergencyInput
import com.comunidapp.app.data.repository.CreateShelterEventInput
import com.comunidapp.app.data.repository.CreateShelterProfileInput
import com.comunidapp.app.data.repository.FosterSecureRefValidator
import com.comunidapp.app.data.repository.M11ShelterAuditEvents
import com.comunidapp.app.data.repository.M11ShelterMemoryStore
import com.comunidapp.app.data.repository.M11ShelterNotificationHooks
import com.comunidapp.app.data.repository.MockShelterEmergencyRepository
import com.comunidapp.app.data.repository.MockShelterEventRepository
import com.comunidapp.app.data.repository.MockShelterProfileRepository
import com.comunidapp.app.data.repository.MockShelterReportRepository
import com.comunidapp.app.data.repository.MockShelterVolunteerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * LeoVer M11 — Bloque 3: urgencias operativas, eventos institucionales y reportes.
 * Solo fakes en memoria; sin Supabase real ni WorkManager.
 */
class M11ShelterEmergenciesEventsReportsTest {

    private lateinit var store: M11ShelterMemoryStore
    private var actorId: String = "manager-1"

    private lateinit var profiles: MockShelterProfileRepository
    private lateinit var emergencies: MockShelterEmergencyRepository
    private lateinit var events: MockShelterEventRepository
    private lateinit var reports: MockShelterReportRepository
    private lateinit var volunteers: MockShelterVolunteerRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        store = M11ShelterMemoryStore()
        actorId = "manager-1"
        store.organizationStatus.value = mapOf("org-1" to "ACTIVE")
        store.orgManagers.value = mapOf("org-1" to setOf("manager-1"))
        store.orgViewers.value = mapOf("org-1" to setOf("manager-1", "viewer-1", "contributor-1"))
        wire()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun wire() {
        profiles = MockShelterProfileRepository(actorUserId = { actorId }, store = store)
        emergencies = MockShelterEmergencyRepository(actorUserId = { actorId }, store = store)
        events = MockShelterEventRepository(actorUserId = { actorId }, store = store)
        reports = MockShelterReportRepository(actorUserId = { actorId }, store = store)
        volunteers = MockShelterVolunteerRepository(
            actorUserId = { actorId },
            store = store,
            knownUserIds = { setOf("vol-1", "manager-1", "contributor-1") }
        )
    }

    private suspend fun createActiveShelter() =
        profiles.createShelter(
            CreateShelterProfileInput(
                organizationId = "org-1",
                displayName = "Refugio Sol",
                description = "Desc",
                totalCapacity = 4,
                acceptedSpecies = setOf("DOG"),
                activate = true
            )
        ).getOrThrow()

    private fun nowPlusHours(hours: Long) = System.currentTimeMillis() + hours * 3_600_000L

    private suspend fun createDraftEmergency(
        shelterId: String,
        severity: ShelterEmergencySeverity = ShelterEmergencySeverity.MEDIUM,
        visibility: ShelterEmergencyVisibility = ShelterEmergencyVisibility.PUBLIC
    ) = emergencies.createEmergency(
        CreateShelterEmergencyInput(
            shelterProfileId = shelterId,
            title = "Urgencia médica",
            description = "Necesitamos insumos",
            category = ShelterEmergencyCategory.MEDICAL,
            severity = severity,
            visibility = visibility,
            startsAt = System.currentTimeMillis(),
            activate = false
        )
    ).getOrThrow()

    private suspend fun createDraftEvent(
        shelterId: String,
        capacity: Int? = null,
        visibility: ShelterEventVisibility = ShelterEventVisibility.PUBLIC
    ): com.comunidapp.app.data.model.ShelterEvent {
        val start = nowPlusHours(24)
        return events.createEvent(
            CreateShelterEventInput(
                shelterProfileId = shelterId,
                title = "Jornada adopción",
                description = "Evento comunitario",
                eventType = ShelterEventType.ADOPTION_DAY,
                visibility = visibility,
                startsAt = start,
                endsAt = start + 3_600_000L,
                capacity = capacity,
                publicLocationText = "Plaza central",
                privateLocationText = "Depósito interno calle 9",
                publish = false
            )
        ).getOrThrow()
    }

    private suspend fun publishEvent(eventId: String) =
        events.changeEventStatus(eventId, ShelterEventStatus.PUBLISHED).getOrThrow()

    private fun codeOf(r: Result<*>) = M11ShelterErrorMapper.codeOf(r.exceptionOrNull()!!)

    @Test
    fun createEmergencyDraft() = runTest {
        val shelter = createActiveShelter()
        val e = createDraftEmergency(shelter.id)
        assertEquals(ShelterEmergencyStatus.DRAFT, e.status)
        assertTrue(store.auditEvents.value.any { it.eventKey == M11ShelterAuditEvents.SHELTER_EMERGENCY_CREATED })
    }

    @Test
    fun activateValidEmergency() = runTest {
        val shelter = createActiveShelter()
        val draft = createDraftEmergency(shelter.id)
        val active = emergencies.changeEmergencyStatus(draft.id, ShelterEmergencyStatus.ACTIVE).getOrThrow()
        assertEquals(ShelterEmergencyStatus.ACTIVE, active.status)
        assertTrue(store.auditEvents.value.any { it.eventKey == M11ShelterAuditEvents.SHELTER_EMERGENCY_STATUS_CHANGED })
    }

    @Test
    fun criticalEmergencyPreparesM06Hook() = runTest {
        val shelter = createActiveShelter()
        store.m06Hooks.value = emptyList()
        emergencies.createEmergency(
            CreateShelterEmergencyInput(
                shelterProfileId = shelter.id,
                title = "Incendio cercano",
                description = "Evacuación parcial",
                category = ShelterEmergencyCategory.RESCUE,
                severity = ShelterEmergencySeverity.CRITICAL,
                visibility = ShelterEmergencyVisibility.PUBLIC,
                startsAt = System.currentTimeMillis(),
                activate = true
            )
        ).getOrThrow()
        assertTrue(store.m06Hooks.value.contains(M11ShelterNotificationHooks.EMERGENCY_CRITICAL_ACTIVATED))
    }

    @Test
    fun publicListHidesInternalEmergencies() = runTest {
        val shelter = createActiveShelter()
        val draftPublic = createDraftEmergency(shelter.id)
        emergencies.changeEmergencyStatus(draftPublic.id, ShelterEmergencyStatus.ACTIVE).getOrThrow()
        emergencies.createEmergency(
            CreateShelterEmergencyInput(
                shelterProfileId = shelter.id,
                title = "Operativa interna",
                description = "Solo staff",
                category = ShelterEmergencyCategory.OTHER,
                severity = ShelterEmergencySeverity.LOW,
                visibility = ShelterEmergencyVisibility.INTERNAL,
                startsAt = System.currentTimeMillis(),
                activate = true
            )
        ).getOrThrow()
        val pub = emergencies.observePublicEmergencies().first()
        assertEquals(1, pub.size)
        assertFalse(pub.javaClass.declaredFields.any { it.name == "resolutionNotes" })
        assertFalse(pub.javaClass.declaredFields.any { it.name == "evidenceRef" })
    }

    @Test
    fun rejectUnsafeEvidence() = runTest {
        val shelter = createActiveShelter()
        assertEquals(
            "SHELTER_EVIDENCE_REF_INVALID",
            codeOf(
                emergencies.createEmergency(
                    CreateShelterEmergencyInput(
                        shelterProfileId = shelter.id,
                        title = "Con evidencia",
                        description = "Desc",
                        category = ShelterEmergencyCategory.FOOD,
                        evidenceRef = "https://evil.example/leak.jpg",
                        startsAt = System.currentTimeMillis()
                    )
                )
            )
        )
        assertTrue(FosterSecureRefValidator.isUnsafePublicReference("https://x/object/public/leover/x"))
    }

    @Test
    fun resolveRequiresNotes() = runTest {
        val shelter = createActiveShelter()
        val draft = createDraftEmergency(shelter.id)
        val active = emergencies.changeEmergencyStatus(draft.id, ShelterEmergencyStatus.ACTIVE).getOrThrow()
        assertEquals(
            "SHELTER_EMERGENCY_RESOLUTION_REQUIRED",
            codeOf(emergencies.resolveEmergency(active.id, "   "))
        )
        assertEquals(
            "SHELTER_EMERGENCY_RESOLUTION_REQUIRED",
            codeOf(emergencies.changeEmergencyStatus(active.id, ShelterEmergencyStatus.RESOLVED))
        )
    }

    @Test
    fun resolveEmergency() = runTest {
        val shelter = createActiveShelter()
        val draft = createDraftEmergency(shelter.id)
        val active = emergencies.changeEmergencyStatus(draft.id, ShelterEmergencyStatus.ACTIVE).getOrThrow()
        val resolved = emergencies.resolveEmergency(active.id, "Se normalizó la situación").getOrThrow()
        assertEquals(ShelterEmergencyStatus.RESOLVED, resolved.status)
        assertNotNull(resolved.resolutionNotes)
        assertTrue(store.auditEvents.value.any { it.eventKey == M11ShelterAuditEvents.SHELTER_EMERGENCY_RESOLVED })
    }

    @Test
    fun expireActiveEmergency() = runTest {
        val shelter = createActiveShelter()
        val past = System.currentTimeMillis() - 86_400_000L
        val active = emergencies.createEmergency(
            CreateShelterEmergencyInput(
                shelterProfileId = shelter.id,
                title = "Vencida",
                description = "Expira sola",
                category = ShelterEmergencyCategory.CAPACITY,
                startsAt = past,
                expiresAt = past,
                activate = true
            )
        ).getOrThrow()
        val expired = emergencies.expireIfNeeded(active.id).getOrThrow()
        assertEquals(ShelterEmergencyStatus.EXPIRED, expired.status)
    }

    @Test
    fun cancelEmergency() = runTest {
        val shelter = createActiveShelter()
        val draft = createDraftEmergency(shelter.id)
        val cancelled = emergencies.changeEmergencyStatus(draft.id, ShelterEmergencyStatus.CANCELLED).getOrThrow()
        assertEquals(ShelterEmergencyStatus.CANCELLED, cancelled.status)
    }

    @Test
    fun createEventDraft() = runTest {
        val shelter = createActiveShelter()
        val event = createDraftEvent(shelter.id)
        assertEquals(ShelterEventStatus.DRAFT, event.status)
        assertTrue(store.auditEvents.value.any { it.eventKey == M11ShelterAuditEvents.SHELTER_EVENT_CREATED })
    }

    @Test
    fun eventDatesMustBeValid() = runTest {
        val shelter = createActiveShelter()
        val start = nowPlusHours(48)
        assertEquals(
            "SHELTER_EVENT_INVALID",
            codeOf(
                events.createEvent(
                    CreateShelterEventInput(
                        shelterProfileId = shelter.id,
                        title = "Mal fechado",
                        description = "Desc",
                        eventType = ShelterEventType.TRAINING,
                        startsAt = start,
                        endsAt = start - 1_000L
                    )
                )
            )
        )
        assertEquals(
            "SHELTER_EVENT_INVALID",
            codeOf(
                events.createEvent(
                    CreateShelterEventInput(
                        shelterProfileId = shelter.id,
                        title = "Cupo inválido",
                        description = "Desc",
                        eventType = ShelterEventType.TRAINING,
                        startsAt = start,
                        endsAt = start + 3_600_000L,
                        capacity = 0
                    )
                )
            )
        )
    }

    @Test
    fun publishEvent() = runTest {
        val shelter = createActiveShelter()
        store.m06Hooks.value = emptyList()
        val draft = createDraftEvent(shelter.id)
        val published = events.changeEventStatus(draft.id, ShelterEventStatus.PUBLISHED).getOrThrow()
        assertEquals(ShelterEventStatus.PUBLISHED, published.status)
        assertTrue(store.m06Hooks.value.contains(M11ShelterNotificationHooks.EVENT_PUBLISHED))
        assertEquals(1, events.observePublicEvents().first().size)
    }

    @Test
    fun registerForPublishedEvent() = runTest {
        val shelter = createActiveShelter()
        val draft = createDraftEvent(shelter.id, capacity = 10)
        val published = publishEvent(draft.id)
        actorId = "contributor-1"
        wire()
        val reg = events.register(published.id, "Quiero ayudar").getOrThrow()
        assertEquals(ShelterEventRegistrationStatus.REGISTERED, reg.status)
        assertTrue(store.auditEvents.value.any { it.eventKey == M11ShelterAuditEvents.SHELTER_EVENT_REGISTRATION })
    }

    @Test
    fun rejectDuplicateRegistration() = runTest {
        val shelter = createActiveShelter()
        val published = publishEvent(createDraftEvent(shelter.id, capacity = 5).id)
        actorId = "contributor-1"
        wire()
        events.register(published.id).getOrThrow()
        assertEquals(
            "SHELTER_EVENT_ALREADY_REGISTERED",
            codeOf(events.register(published.id))
        )
    }

    @Test
    fun fullCapacityGoesToWaitlist() = runTest {
        val shelter = createActiveShelter()
        val published = publishEvent(createDraftEvent(shelter.id, capacity = 1).id)
        actorId = "contributor-1"
        wire()
        events.register(published.id).getOrThrow()
        actorId = "viewer-1"
        wire()
        val waitlisted = events.register(published.id).getOrThrow()
        assertEquals(ShelterEventRegistrationStatus.WAITLISTED, waitlisted.status)
        assertTrue(store.m06Hooks.value.contains(M11ShelterNotificationHooks.EVENT_WAITLISTED))
        assertEquals(ShelterEventStatus.FULL, events.getEvent(published.id).getOrThrow().status)
    }

    @Test
    fun cancelRegistration() = runTest {
        val shelter = createActiveShelter()
        val published = publishEvent(createDraftEvent(shelter.id).id)
        actorId = "contributor-1"
        wire()
        val reg = events.register(published.id).getOrThrow()
        val cancelled = events.cancelRegistration(reg.id).getOrThrow()
        assertEquals(ShelterEventRegistrationStatus.CANCELLED, cancelled.status)
        assertNotNull(cancelled.cancelledAt)
        assertTrue(store.auditEvents.value.any { it.eventKey == M11ShelterAuditEvents.SHELTER_EVENT_REGISTRATION_CANCELLED })
    }

    @Test
    fun markAttendance() = runTest {
        val shelter = createActiveShelter()
        val published = publishEvent(createDraftEvent(shelter.id).id)
        actorId = "contributor-1"
        wire()
        val reg = events.register(published.id).getOrThrow()
        actorId = "manager-1"
        wire()
        val attended = events.markAttendance(reg.id, attended = true).getOrThrow()
        assertEquals(ShelterEventRegistrationStatus.ATTENDED, attended.status)
        assertTrue(store.auditEvents.value.any { it.eventKey == M11ShelterAuditEvents.SHELTER_EVENT_ATTENDANCE })
    }

    @Test
    fun cancelEventPreservesRegistrationHistory() = runTest {
        val shelter = createActiveShelter()
        store.m06Hooks.value = emptyList()
        val published = publishEvent(createDraftEvent(shelter.id).id)
        actorId = "contributor-1"
        wire()
        val reg = events.register(published.id).getOrThrow()
        actorId = "manager-1"
        wire()
        events.changeEventStatus(published.id, ShelterEventStatus.CANCELLED).getOrThrow()
        val stored = store.eventRegistrations.value.first { it.id == reg.id }
        assertEquals(ShelterEventRegistrationStatus.CANCELLED, stored.status)
        assertEquals(1, store.eventRegistrations.value.count { it.eventId == published.id })
        assertTrue(store.m06Hooks.value.contains(M11ShelterNotificationHooks.EVENT_CANCELLED))
    }

    @Test
    fun capacityMetrics() = runTest {
        val shelter = createActiveShelter()
        val from = 0L
        val to = System.currentTimeMillis() + 86_400_000L
        val metrics = reports.getCapacityMetrics(shelter.id, from, to).getOrThrow()
        assertEquals(shelter.id, metrics.shelterProfileId)
        assertEquals(4, metrics.totalCapacity)
        assertTrue(metrics.freeSlots >= 0)
    }

    @Test
    fun petMetrics() = runTest {
        val shelter = createActiveShelter()
        val from = 0L
        val to = System.currentTimeMillis() + 86_400_000L
        val metrics = reports.getPetMetrics(shelter.id, from, to).getOrThrow()
        assertEquals(shelter.id, metrics.shelterProfileId)
        assertTrue(metrics.activeCount >= 0)
    }

    @Test
    fun volunteerMetrics() = runTest {
        val shelter = createActiveShelter()
        val from = 0L
        val to = System.currentTimeMillis() + 86_400_000L
        val metrics = reports.getVolunteerMetrics(shelter.id, from, to).getOrThrow()
        assertEquals(shelter.id, metrics.shelterProfileId)
        assertTrue(metrics.activeCount >= 0)
    }

    @Test
    fun campaignAndSupplyMetrics() = runTest {
        val shelter = createActiveShelter()
        val from = 0L
        val to = System.currentTimeMillis() + 86_400_000L
        val campaigns = reports.getCampaignMetrics(shelter.id, from, to).getOrThrow()
        val supplies = reports.getSupplyMetrics(shelter.id, from, to).getOrThrow()
        assertEquals(shelter.id, campaigns.shelterProfileId)
        assertEquals(shelter.id, supplies.shelterProfileId)
    }

    @Test
    fun emergencyAndEventMetrics() = runTest {
        val shelter = createActiveShelter()
        val draft = createDraftEmergency(shelter.id, severity = ShelterEmergencySeverity.CRITICAL)
        emergencies.changeEmergencyStatus(draft.id, ShelterEmergencyStatus.ACTIVE).getOrThrow()
        publishEvent(createDraftEvent(shelter.id).id)
        val from = 0L
        val to = System.currentTimeMillis() + 86_400_000L * 30
        val em = reports.getEmergencyMetrics(shelter.id, from, to).getOrThrow()
        val ev = reports.getEventMetrics(shelter.id, from, to).getOrThrow()
        assertTrue(em.activeCount >= 1)
        assertTrue(em.criticalCount >= 1)
        assertTrue(ev.upcomingCount >= 1)
    }

    @Test
    fun invalidReportRange() = runTest {
        val shelter = createActiveShelter()
        val from = System.currentTimeMillis()
        val to = from - 1_000L
        assertEquals("SHELTER_REPORT_INVALID_RANGE", codeOf(reports.getOperationalSummary(shelter.id, from, to)))
    }

    @Test
    fun exportCsvWithoutPii() = runTest {
        val shelter = createActiveShelter()
        val draft = createDraftEmergency(shelter.id)
        val active = emergencies.changeEmergencyStatus(draft.id, ShelterEmergencyStatus.ACTIVE).getOrThrow()
        emergencies.resolveEmergency(active.id, "Resuelto con nota interna secreta").getOrThrow()
        val from = 0L
        val to = System.currentTimeMillis() + 86_400_000L
        val export = reports.exportOperationalCsv(shelter.id, from, to).getOrThrow()
        assertTrue(export.csvContent.contains("metric,value"))
        assertFalse(export.csvContent.contains("@"))
        assertFalse(export.csvContent.contains("secreta", ignoreCase = true))
        assertFalse(export.csvContent.contains("Depósito interno"))
        assertFalse(export.csvContent.contains("contributor-1"))
        assertTrue(store.auditEvents.value.any { it.eventKey == M11ShelterAuditEvents.SHELTER_REPORT_EXPORTED })
    }

    @Test
    fun userWithoutPermission() = runTest {
        val shelter = createActiveShelter()
        actorId = "stranger"
        wire()
        assertEquals(
            "SHELTER_EMERGENCY_FORBIDDEN",
            codeOf(
                emergencies.createEmergency(
                    CreateShelterEmergencyInput(
                        shelterProfileId = shelter.id,
                        title = "X",
                        description = "Y",
                        category = ShelterEmergencyCategory.OTHER,
                        startsAt = System.currentTimeMillis()
                    )
                )
            )
        )
        assertEquals(
            "SHELTER_REPORT_FORBIDDEN",
            codeOf(reports.getOperationalSummary(shelter.id, 0L, System.currentTimeMillis()))
        )
    }

    @Test
    fun volunteerWithoutAutomaticAuthority() = runTest {
        val shelter = createActiveShelter()
        val assignment = volunteers.inviteVolunteer(
            shelter.id,
            "vol-1",
            ShelterVolunteerRole.ANIMAL_CARE,
            null
        ).getOrThrow()
        actorId = "vol-1"
        wire()
        volunteers.acceptAssignment(assignment.id).getOrThrow()
        assertEquals(
            "SHELTER_EVENT_FORBIDDEN",
            codeOf(
                events.createEvent(
                    CreateShelterEventInput(
                        shelterProfileId = shelter.id,
                        title = "Vol intenta",
                        description = "Sin permiso M03",
                        eventType = ShelterEventType.VOLUNTEERING,
                        startsAt = nowPlusHours(1),
                        endsAt = nowPlusHours(2)
                    )
                )
            )
        )
    }

    @Test
    fun unknownEnumFromStringControlled() {
        assertEquals(ShelterEmergencyStatus.UNKNOWN, ShelterEmergencyStatus.fromString("WEIRD"))
        assertEquals(ShelterEmergencyCategory.UNKNOWN, ShelterEmergencyCategory.fromString("?"))
        assertEquals(ShelterEventStatus.UNKNOWN, ShelterEventStatus.fromString("x"))
        assertEquals(ShelterEventRegistrationStatus.UNKNOWN, ShelterEventRegistrationStatus.fromString("z"))
    }

    @Test
    fun emptyAndMissingIdControlled() = runTest {
        assertEquals("SHELTER_EMERGENCY_NOT_FOUND", codeOf(emergencies.getEmergency("")))
        assertEquals("SHELTER_EVENT_NOT_FOUND", codeOf(events.getEvent("  ")))
        assertEquals("SHELTER_EMERGENCY_NOT_FOUND", codeOf(emergencies.getEmergency("missing-emergency")))
        assertEquals("SHELTER_EVENT_NOT_FOUND", codeOf(events.getEvent("missing-event")))
    }

    @Test
    fun repositoryErrorMappedToUserMessage() {
        val msg = M11ShelterErrorMapper.userMessage("SHELTER_EMERGENCY_FORBIDDEN")
        assertFalse(msg.contains("postgres", ignoreCase = true))
        assertFalse(msg.contains("supabase", ignoreCase = true))
        assertTrue(msg.isNotBlank())
    }

    @Test
    fun doubleSubmitGuarded() = runTest {
        val shelter = createActiveShelter()
        val vm = ShelterEmergencyFormViewModel(shelter.id, emergencyId = null, repo = emergencies)
        val now = System.currentTimeMillis()
        vm.create(
            title = "Urgencia A",
            description = "Desc A",
            category = ShelterEmergencyCategory.FOOD,
            severity = ShelterEmergencySeverity.MEDIUM,
            visibility = ShelterEmergencyVisibility.PUBLIC,
            startsAt = now,
            expiresAt = null,
            petId = null,
            evidenceRef = null,
            activate = true
        )
        vm.create(
            title = "Urgencia B",
            description = "Desc B",
            category = ShelterEmergencyCategory.FOOD,
            severity = ShelterEmergencySeverity.MEDIUM,
            visibility = ShelterEmergencyVisibility.PUBLIC,
            startsAt = now,
            expiresAt = null,
            petId = null,
            evidenceRef = null,
            activate = true
        )
        assertTrue(emergencies.observeShelterEmergencies(shelter.id).first().isNotEmpty())
        val published = publishEvent(createDraftEvent(shelter.id, capacity = 5).id)
        actorId = "contributor-1"
        wire()
        events.register(published.id).getOrThrow()
        events.register(published.id)
        assertEquals(
            1,
            store.eventRegistrations.value.count {
                it.userId == "contributor-1" && it.eventId == published.id && it.status.isActive
            }
        )
    }

    @Test
    fun loadingInitialThenEmptyOrContent() = runTest {
        val vm = ShelterPublicEmergenciesViewModel(repo = emergencies)
        assertEquals(ShelterPublicEmergenciesUiState.Empty, vm.uiState.value)
        val blankDetail = ShelterEmergencyDetailViewModel("", repo = emergencies)
        assertTrue(blankDetail.uiState.value is ShelterEmergencyDetailUiState.Error)
        assertTrue(blankDetail.uiState.value !is ShelterEmergencyDetailUiState.Loading)
    }

    @Test
    fun m05IntegrationAcceptRefs() = runTest {
        val shelter = createActiveShelter()
        val e = emergencies.createEmergency(
            CreateShelterEmergencyInput(
                shelterProfileId = shelter.id,
                title = "Con M05",
                description = "Evidencia segura",
                category = ShelterEmergencyCategory.MEDICAL,
                evidenceRef = "m05://media/emergency-1",
                startsAt = System.currentTimeMillis(),
                activate = true
            )
        ).getOrThrow()
        assertEquals("m05://media/emergency-1", e.evidenceRef)
        val start = nowPlusHours(72)
        val ev = events.createEvent(
            CreateShelterEventInput(
                shelterProfileId = shelter.id,
                title = "Portada file_asset",
                description = "Evento",
                eventType = ShelterEventType.OPEN_HOUSE,
                startsAt = start,
                endsAt = start + 3_600_000L,
                coverAssetRef = "file_asset:cover-99",
                publish = true
            )
        ).getOrThrow()
        assertEquals("file_asset:cover-99", ev.coverAssetRef)
    }

    @Test
    fun preventEmergencyOnInactiveShelter() = runTest {
        val shelter = createActiveShelter()
        profiles.changeStatus(shelter.id, ShelterStatus.PAUSED).getOrThrow()
        assertEquals(
            "SHELTER_NOT_ACTIVE",
            codeOf(
                emergencies.createEmergency(
                    CreateShelterEmergencyInput(
                        shelterProfileId = shelter.id,
                        title = "X",
                        description = "Y",
                        category = ShelterEmergencyCategory.FOOD,
                        startsAt = System.currentTimeMillis(),
                        activate = true
                    )
                )
            )
        )
    }

    @Test
    fun publicEventListingHidesPrivateLocation() = runTest {
        val shelter = createActiveShelter()
        val published = publishEvent(createDraftEvent(shelter.id).id)
        val listing = events.observePublicEvents().first().first { it.id == published.id }
        assertNull(listing.javaClass.declaredFields.find { it.name == "privateLocationText" }?.get(listing))
        assertFalse(listing.javaClass.declaredFields.any { it.name == "privateLocationText" })
    }

    @Test
    fun noRealSupabaseInTests() {
        assertTrue(emergencies is MockShelterEmergencyRepository)
        assertTrue(events is MockShelterEventRepository)
        assertTrue(reports is MockShelterReportRepository)
        assertTrue(profiles is MockShelterProfileRepository)
        assertFalse(emergencies.javaClass.name.contains("Supabase"))
        assertFalse(events.javaClass.name.contains("Supabase"))
        assertFalse(reports.javaClass.name.contains("Supabase"))
    }
}
