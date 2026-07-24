package com.comunidapp.app.viewmodel

import com.comunidapp.app.data.model.VeterinaryAppointmentOperationalMetrics
import com.comunidapp.app.data.model.VeterinaryAppointmentStatus
import com.comunidapp.app.data.model.VeterinaryReminderDeliveryStatus
import com.comunidapp.app.data.model.VeterinaryScheduleSettings
import com.comunidapp.app.data.model.VeterinaryServiceCategory
import com.comunidapp.app.data.model.VeterinarySpecialty
import com.comunidapp.app.data.model.VeterinaryClinicStatus
import com.comunidapp.app.data.model.VeterinaryOpeningHours
import com.comunidapp.app.data.remote.supabase.m12.M12VeterinaryErrorMapper
import com.comunidapp.app.data.repository.CreateVeterinaryAvailabilityRuleInput
import com.comunidapp.app.data.repository.CreateVeterinaryClinicDraftInput
import com.comunidapp.app.data.repository.CreateVeterinaryProfessionalInput
import com.comunidapp.app.data.repository.CreateVeterinaryServiceInput
import com.comunidapp.app.data.repository.M12VeterinaryMemoryStore
import com.comunidapp.app.data.repository.MockVeterinaryAppointmentRepository
import com.comunidapp.app.data.repository.MockVeterinaryClinicLifecycle
import com.comunidapp.app.data.repository.MockVeterinaryClinicRepository
import com.comunidapp.app.data.repository.MockVeterinaryOpeningHoursRepository
import com.comunidapp.app.data.repository.MockVeterinaryProfessionalOpsRepository
import com.comunidapp.app.data.repository.MockVeterinaryScheduleRepository
import com.comunidapp.app.data.repository.MockVeterinaryServiceRepository
import com.comunidapp.app.data.repository.RequestVeterinaryAppointmentInput
import com.comunidapp.app.data.repository.VeterinaryRetryAction
import com.comunidapp.app.data.repository.appointments
import com.comunidapp.app.data.repository.clearBlock3
import com.comunidapp.app.data.repository.petAuthorizedActors
import com.comunidapp.app.data.repository.redactAppointmentForViewer
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * LeoVer M12 Bloque 4 — endurecimiento de agenda/turnos (solo fakes, sin Supabase real).
 *
 * Cubre expiración, concurrencia (confirmación simultánea), doble transición, ventana de
 * cancelación, servicio/profesional inactivo al confirmar, zona horaria/DST, timeline,
 * privacidad, `retrySafeTransition`, métricas agregadas sin PII y liberación de cupo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class M12VeterinaryAppointmentHardeningTest {

    private companion object {
        const val TZ = "America/Argentina/Buenos_Aires"
        const val SANTIAGO = "America/Santiago"
        const val MANAGER = "manager-vet-1"
        const val OWNER_A = "pet-owner-a"
        const val OWNER_B = "pet-owner-b"
        const val PET_A = "pet-a"
        const val PET_B = "pet-b"
        const val PET_C = "pet-c"
    }

    private lateinit var store: M12VeterinaryMemoryStore
    private var actorId = MANAGER
    private lateinit var clinics: MockVeterinaryClinicRepository
    private lateinit var lifecycle: MockVeterinaryClinicLifecycle
    private lateinit var pros: MockVeterinaryProfessionalOpsRepository
    private lateinit var services: MockVeterinaryServiceRepository
    private lateinit var hours: MockVeterinaryOpeningHoursRepository
    private lateinit var schedule: MockVeterinaryScheduleRepository
    private lateinit var appointments: MockVeterinaryAppointmentRepository

    private val zone: ZoneId get() = ZoneId.of(TZ)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        store = M12VeterinaryMemoryStore()
        store.organizationStatus.value = mapOf("org-1" to "ACTIVE")
        store.orgManagers.value = mapOf("org-1" to setOf(MANAGER))
        store.orgViewers.value = mapOf("org-1" to setOf(MANAGER))
        store.clearBlock3()
        clinics = MockVeterinaryClinicRepository({ actorId }, store)
        lifecycle = MockVeterinaryClinicLifecycle({ actorId }, store)
        pros = MockVeterinaryProfessionalOpsRepository({ actorId }, store)
        services = MockVeterinaryServiceRepository({ actorId }, store)
        hours = MockVeterinaryOpeningHoursRepository({ actorId }, store)
        schedule = MockVeterinaryScheduleRepository({ actorId }, store)
        appointments = MockVeterinaryAppointmentRepository({ actorId }, store)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun code(t: Throwable?) = M12VeterinaryErrorMapper.codeOf(t!!)

    private data class Fixture(
        val clinicId: String,
        val serviceId: String,
        val professionalId: String,
        val organizationId: String,
        val bookingDate: LocalDate
    )

    private suspend fun bootstrap(capacityPerSlot: Int = 1): Fixture {
        actorId = MANAGER
        val clinic = clinics.createLocalDraft(
            CreateVeterinaryClinicDraftInput(
                organizationId = "org-1",
                displayName = "Clínica Endurecimiento",
                publicZoneText = "Zona Test"
            )
        ).getOrThrow()
        services.createService(
            CreateVeterinaryServiceInput(
                clinicId = clinic.id,
                name = "Consulta",
                category = VeterinaryServiceCategory.CONSULTATION
            )
        ).getOrThrow()
        hours.replaceWeekly(
            clinic.id,
            listOf(
                VeterinaryOpeningHours(
                    clinicId = clinic.id,
                    dayOfWeek = java.time.DayOfWeek.MONDAY,
                    opensAt = LocalTime.of(9, 0),
                    closesAt = LocalTime.of(18, 0)
                )
            )
        ).getOrThrow()
        lifecycle.changeStatus(clinic.id, VeterinaryClinicStatus.ACTIVE).getOrThrow()

        val service = store.services.value.first { it.clinicId == clinic.id }
        val pro = pros.createProfessional(
            CreateVeterinaryProfessionalInput(
                displayName = "Dra. Vet",
                organizationId = "org-1",
                specialties = setOf(VeterinarySpecialty.GENERAL_MEDICINE)
            )
        ).getOrThrow()
        pros.linkProfessional(clinic.id, pro.id, "Titular").getOrThrow()
        schedule.saveSettings(VeterinaryScheduleSettings(clinicId = clinic.id)).getOrThrow()

        val bookingDate = LocalDate.now(zone).plusDays(2)
        schedule.createRule(
            CreateVeterinaryAvailabilityRuleInput(
                clinicId = clinic.id,
                dayOfWeek = bookingDate.dayOfWeek,
                startsAt = LocalTime.of(9, 0),
                endsAt = LocalTime.of(18, 0),
                slotDurationMinutes = 30,
                capacityPerSlot = capacityPerSlot,
                serviceId = service.id
            )
        ).getOrThrow()
        return Fixture(clinic.id, service.id, pro.id, clinic.organizationId, bookingDate)
    }

    private fun slotStart(f: Fixture): Instant =
        f.bookingDate.atTime(LocalTime.of(9, 0)).atZone(zone).toInstant()

    private suspend fun requestAppointment(
        f: Fixture,
        owner: String,
        pet: String,
        at: LocalTime = LocalTime.of(9, 0),
        professionalId: String? = null
    ) = run {
        store.petAuthorizedActors.value = store.petAuthorizedActors.value + (pet to setOf(owner))
        actorId = owner
        appointments.requestAppointment(
            RequestVeterinaryAppointmentInput(
                clinicId = f.clinicId,
                serviceId = f.serviceId,
                petId = pet,
                startsAt = f.bookingDate.atTime(at).atZone(zone).toInstant(),
                professionalId = professionalId
            )
        )
    }

    // --- Expiración ---

    @Test
    fun expire_requested_appointment() = runTest {
        val f = bootstrap()
        val a = requestAppointment(f, OWNER_A, PET_A).getOrThrow()
        assertEquals(VeterinaryAppointmentStatus.REQUESTED, a.status)
        actorId = MANAGER
        val expired = appointments.expireAppointment(a.id).getOrThrow()
        assertEquals(VeterinaryAppointmentStatus.EXPIRED, expired.status)
    }

    // --- Concurrencia: confirmación simultánea / doble transición ---

    @Test
    fun simultaneous_confirm_resolves_to_conflict() = runTest {
        val f = bootstrap()
        val a = requestAppointment(f, OWNER_A, PET_A).getOrThrow()
        actorId = MANAGER
        appointments.retrySafeTransition(
            a.id, VeterinaryAppointmentStatus.REQUESTED, VeterinaryRetryAction.CONFIRM
        ).getOrThrow()
        val again = appointments.retrySafeTransition(
            a.id, VeterinaryAppointmentStatus.REQUESTED, VeterinaryRetryAction.CONFIRM
        )
        assertEquals("VETERINARY_APPOINTMENT_RETRY_CONFLICT", code(again.exceptionOrNull()))
    }

    @Test
    fun double_confirm_blocked_as_invalid_transition() = runTest {
        val f = bootstrap()
        val a = requestAppointment(f, OWNER_A, PET_A).getOrThrow()
        actorId = MANAGER
        appointments.confirmAppointment(a.id).getOrThrow()
        val again = appointments.confirmAppointment(a.id)
        assertEquals("VETERINARY_APPOINTMENT_INVALID_TRANSITION", code(again.exceptionOrNull()))
    }

    @Test
    fun retry_safe_transition_conflicts_on_wrong_expected_from() = runTest {
        val f = bootstrap()
        val a = requestAppointment(f, OWNER_A, PET_A).getOrThrow()
        actorId = MANAGER
        // El turno está REQUESTED; pedir cancelación gestionada asumiendo CONFIRMED → conflicto.
        val conflict = appointments.retrySafeTransition(
            a.id, VeterinaryAppointmentStatus.CONFIRMED, VeterinaryRetryAction.CANCEL_MANAGED
        )
        assertEquals("VETERINARY_APPOINTMENT_RETRY_CONFLICT", code(conflict.exceptionOrNull()))
    }

    // --- Cancelación fuera de ventana ---

    @Test
    fun cancel_outside_window_rejected() = runTest {
        val f = bootstrap()
        val a = requestAppointment(f, OWNER_A, PET_A).getOrThrow()
        actorId = MANAGER
        appointments.confirmAppointment(a.id).getOrThrow()
        // Acercamos el turno a "ahora" (dentro de la ventana de cancelación de 120').
        val soon = Instant.now().plusSeconds(1_800)
        store.appointments.value = store.appointments.value.map {
            if (it.id == a.id) it.copy(startsAt = soon, endsAt = soon.plusSeconds(1_800)) else it
        }
        actorId = OWNER_A
        val cancel = appointments.cancelMyAppointment(a.id, null)
        assertEquals("VETERINARY_APPOINTMENT_CANCELLATION_WINDOW", code(cancel.exceptionOrNull()))
    }

    // --- Servicio / profesional inactivo al confirmar ---

    @Test
    fun service_inactive_on_confirm_rejected() = runTest {
        val f = bootstrap()
        val a = requestAppointment(f, OWNER_A, PET_A).getOrThrow()
        actorId = MANAGER
        services.changeServiceActive(f.serviceId, false).getOrThrow()
        val confirm = appointments.confirmAppointment(a.id)
        assertEquals("VETERINARY_APPOINTMENT_SERVICE_INACTIVE", code(confirm.exceptionOrNull()))
    }

    @Test
    fun professional_inactive_on_confirm_rejected() = runTest {
        val f = bootstrap()
        val a = requestAppointment(f, OWNER_A, PET_A, professionalId = f.professionalId).getOrThrow()
        assertEquals(f.professionalId, a.professionalId)
        actorId = MANAGER
        pros.unlinkProfessional(f.clinicId, f.professionalId).getOrThrow()
        val confirm = appointments.confirmAppointment(a.id)
        assertEquals("VETERINARY_APPOINTMENT_PROFESSIONAL_INACTIVE", code(confirm.exceptionOrNull()))
    }

    // --- Zona horaria / DST ---

    @Test
    fun dst_santiago_zone_used_for_slot_projection() = runTest {
        val f = bootstrap()
        actorId = MANAGER
        schedule.saveSettings(
            VeterinaryScheduleSettings(clinicId = f.clinicId, timezoneName = SANTIAGO)
        ).getOrThrow()
        val slots = schedule.observeAvailableSlots(f.clinicId, f.serviceId, f.bookingDate, null).first()
        assertEquals(18, slots.size)
        val santiago = ZoneId.of(SANTIAGO)
        assertEquals(LocalTime.of(9, 0), slots.first().startsAt.atZone(santiago).toLocalTime())
    }

    @Test
    fun timezone_change_keeps_open_appointment_instants_consistent() = runTest {
        val f = bootstrap()
        val a = requestAppointment(f, OWNER_A, PET_A).getOrThrow()
        val before = store.appointments.value.first { it.id == a.id }.startsAt
        actorId = MANAGER
        schedule.saveSettings(
            VeterinaryScheduleSettings(clinicId = f.clinicId, timezoneName = SANTIAGO)
        ).getOrThrow()
        val after = store.appointments.value.first { it.id == a.id }.startsAt
        assertEquals(before, after)
    }

    // --- Timeline y privacidad ---

    @Test
    fun timeline_built_with_spanish_labels() = runTest {
        val f = bootstrap(capacityPerSlot = 5)
        val a = requestAppointment(f, OWNER_A, PET_A).getOrThrow()
        actorId = MANAGER
        appointments.confirmAppointment(a.id).getOrThrow()
        val timeline = appointments.buildTimeline(a.id, isManager = true).getOrThrow()
        assertEquals(2, timeline.size)
        assertEquals(VeterinaryAppointmentStatus.REQUESTED, timeline.first().status)
        assertEquals("Turno solicitado", timeline.first().label)
        assertEquals(VeterinaryAppointmentStatus.CONFIRMED, timeline.last().status)
        assertTrue(timeline.all { it.label.isNotBlank() })
    }

    @Test
    fun timeline_redacts_clinic_reason_for_requester() = runTest {
        val f = bootstrap(capacityPerSlot = 5)
        val a = requestAppointment(f, OWNER_A, PET_A).getOrThrow()
        actorId = MANAGER
        appointments.rejectAppointment(a.id, "motivo interno de la clínica").getOrThrow()

        // Gestor ve el motivo.
        val managerTimeline = appointments.buildTimeline(a.id, isManager = true).getOrThrow()
        assertEquals("motivo interno de la clínica", managerTimeline.last().reason)

        // Solicitante NO ve el motivo operativo de rechazo de la clínica.
        actorId = OWNER_A
        val ownerTimeline = appointments.buildTimeline(a.id, isManager = false).getOrThrow()
        assertNull(ownerTimeline.last().reason)
    }

    @Test
    fun requester_cannot_see_clinic_operational_note() = runTest {
        val f = bootstrap(capacityPerSlot = 5)
        val a = requestAppointment(f, OWNER_A, PET_A).getOrThrow()
        store.appointments.value = store.appointments.value.map {
            if (it.id == a.id) it.copy(clinicOperationalNote = "nota operativa privada") else it
        }
        val stored = store.appointments.value.first { it.id == a.id }
        val forRequester = redactAppointmentForViewer(stored, OWNER_A, isManager = false)
        assertNull(forRequester.clinicOperationalNote)
        val forManager = redactAppointmentForViewer(stored, MANAGER, isManager = true)
        assertEquals("nota operativa privada", forManager.clinicOperationalNote)
    }

    // --- Métricas agregadas ---

    @Test
    fun operational_metrics_aggregate_counts() = runTest {
        val f = bootstrap(capacityPerSlot = 5)
        val a = requestAppointment(f, OWNER_A, PET_A).getOrThrow()
        val b = requestAppointment(f, OWNER_B, PET_B, at = LocalTime.of(9, 30)).getOrThrow()
        requestAppointment(f, "owner-c", PET_C, at = LocalTime.of(10, 0)).getOrThrow()
        actorId = MANAGER
        appointments.confirmAppointment(a.id).getOrThrow()
        appointments.rejectAppointment(b.id, "sin cupo").getOrThrow()

        val from = f.bookingDate.atStartOfDay(zone).toInstant()
        val to = f.bookingDate.plusDays(1).atStartOfDay(zone).toInstant()
        val metrics = appointments.getOperationalMetrics(f.clinicId, from, to).getOrThrow()
        assertEquals(1, metrics.requested)
        assertEquals(1, metrics.confirmed)
        assertEquals(1, metrics.rejected)
        assertTrue(metrics.occupancyRate in 0.0..1.0)
    }

    @Test
    fun operational_metrics_reject_invalid_range() = runTest {
        val f = bootstrap()
        actorId = MANAGER
        val now = Instant.now()
        val bad = appointments.getOperationalMetrics(f.clinicId, now, now)
        assertEquals("VETERINARY_APPOINTMENT_METRICS_INVALID_RANGE", code(bad.exceptionOrNull()))
    }

    @Test
    fun operational_metrics_expose_no_pii_fields() {
        val piiMarkers = listOf(
            "email", "phone", "note", "requester", "userid", "username",
            "displayname", "petid", "operational"
        )
        val fields = VeterinaryAppointmentOperationalMetrics::class.java.declaredFields.map {
            it.name.lowercase()
        }
        piiMarkers.forEach { marker ->
            assertFalse(
                "métricas no deben exponer PII ($marker)",
                fields.any { it.contains(marker) }
            )
        }
    }

    // --- Cancelación libera cupo y permite cancelar recordatorios ---

    @Test
    fun cancel_frees_capacity_and_reminders_can_be_cancelled() = runTest {
        val f = bootstrap(capacityPerSlot = 1)
        val a = requestAppointment(f, OWNER_A, PET_A).getOrThrow()
        actorId = MANAGER
        appointments.confirmAppointment(a.id).getOrThrow()

        // Confirmar preparó recordatorios (sin push).
        val prepared = appointments.observeReminderSchedule(a.id).first()
        assertNotNull(prepared)
        assertTrue(
            prepared!!.reminders.all { it.status == VeterinaryReminderDeliveryStatus.PREPARED }
        )
        assertTrue(prepared.reminders.none { it.pushClaimed })

        // Cancelar por el usuario libera el cupo.
        actorId = OWNER_A
        appointments.cancelMyAppointment(a.id, null).getOrThrow()
        val slot = schedule.observeAvailableSlots(f.clinicId, f.serviceId, f.bookingDate, null)
            .first().first { it.startsAt == slotStart(f) }
        assertEquals(1, slot.available)

        // Cancelar los recordatorios registra el hook y deja todo en CANCELLED.
        val cancelled = appointments.cancelReminders(a.id).getOrThrow()
        assertTrue(
            cancelled.reminders.all { it.status == VeterinaryReminderDeliveryStatus.CANCELLED }
        )
        assertTrue(
            store.m06Hooks.value.contains(
                com.comunidapp.app.data.model.VeterinaryAppointmentM06Hooks.REMINDER_CANCELLED
            )
        )
    }
}
