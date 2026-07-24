package com.comunidapp.app.viewmodel

import com.comunidapp.app.data.model.VeterinaryAppointmentM06Hooks
import com.comunidapp.app.data.model.VeterinaryClinicStatus
import com.comunidapp.app.data.model.VeterinaryOpeningHours
import com.comunidapp.app.data.model.VeterinaryReminderDeliveryStatus
import com.comunidapp.app.data.model.VeterinaryReminderType
import com.comunidapp.app.data.model.VeterinaryScheduleSettings
import com.comunidapp.app.data.model.VeterinaryServiceCategory
import com.comunidapp.app.data.model.VeterinarySpecialty
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
import com.comunidapp.app.data.repository.appointments
import com.comunidapp.app.data.repository.clearBlock3
import com.comunidapp.app.data.repository.m06InfrastructureAvailable
import com.comunidapp.app.data.repository.petAuthorizedActors
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * LeoVer M12 Bloque 4 — recordatorios idempotentes sin push real (solo fakes).
 *
 * Verifica elegibilidad (solo CONFIRMED), idempotencia, zona horaria en `dueAt`, payload/hooks
 * sin PII, disponibilidad de infraestructura M06, disparo `FIRED_PREPARED` sin reclamo de push y
 * hook de cancelación. Nunca se afirma entrega de push.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class M12VeterinaryReminderTest {

    private companion object {
        const val TZ = "America/Argentina/Buenos_Aires"
        const val SANTIAGO = "America/Santiago"
        const val MANAGER = "manager-vet-1"
        const val OWNER_A = "pet-owner-a"
        const val PET_A = "pet-a"
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
        val bookingDate: LocalDate
    )

    private suspend fun bootstrap(): Fixture {
        actorId = MANAGER
        val clinic = clinics.createLocalDraft(
            CreateVeterinaryClinicDraftInput(
                organizationId = "org-1",
                displayName = "Clínica Recordatorios",
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
                capacityPerSlot = 3,
                serviceId = service.id
            )
        ).getOrThrow()
        return Fixture(clinic.id, service.id, bookingDate)
    }

    private suspend fun requestAppointment(f: Fixture, note: String? = null) = run {
        store.petAuthorizedActors.value = store.petAuthorizedActors.value + (PET_A to setOf(OWNER_A))
        actorId = OWNER_A
        appointments.requestAppointment(
            RequestVeterinaryAppointmentInput(
                clinicId = f.clinicId,
                serviceId = f.serviceId,
                petId = PET_A,
                startsAt = f.bookingDate.atTime(LocalTime.of(9, 0)).atZone(zone).toInstant(),
                requestNote = note
            )
        ).getOrThrow()
    }

    private suspend fun requestAndConfirm(f: Fixture, note: String? = null): String {
        val a = requestAppointment(f, note)
        actorId = MANAGER
        appointments.confirmAppointment(a.id).getOrThrow()
        return a.id
    }

    // --- Elegibilidad ---

    @Test
    fun reminders_24h_and_2h_eligible_on_confirmed() = runTest {
        val f = bootstrap()
        val id = requestAndConfirm(f)
        val sched = appointments.observeReminderSchedule(id).first()
        assertNotNull(sched)
        val types = sched!!.reminders.map { it.type }.toSet()
        assertTrue(types.contains(VeterinaryReminderType.REMINDER_24H))
        assertTrue(types.contains(VeterinaryReminderType.REMINDER_2H))
        assertTrue(sched.reminders.all { it.status == VeterinaryReminderDeliveryStatus.PREPARED })
    }

    @Test
    fun reminders_not_eligible_on_requested() = runTest {
        val f = bootstrap()
        val a = requestAppointment(f)
        actorId = MANAGER
        val result = appointments.prepareRemindersForConfirmed(a.id)
        assertEquals("VETERINARY_REMINDER_NOT_ELIGIBLE", code(result.exceptionOrNull()))
    }

    @Test
    fun reminders_not_eligible_on_cancelled() = runTest {
        val f = bootstrap()
        val a = requestAppointment(f)
        actorId = OWNER_A
        appointments.cancelMyAppointment(a.id, null).getOrThrow()
        actorId = MANAGER
        val result = appointments.prepareRemindersForConfirmed(a.id)
        assertEquals("VETERINARY_REMINDER_NOT_ELIGIBLE", code(result.exceptionOrNull()))
    }

    // --- Idempotencia ---

    @Test
    fun second_prepare_is_idempotent_no_duplicate_hooks() = runTest {
        val f = bootstrap()
        val id = requestAndConfirm(f) // confirmar ya preparó los recordatorios
        actorId = MANAGER
        appointments.prepareRemindersForConfirmed(id).getOrThrow()
        val sched = appointments.observeReminderSchedule(id).first()
        assertNotNull(sched)
        assertEquals(2, sched!!.reminders.size)
        val hook24 = store.m06Hooks.value.count { it == VeterinaryAppointmentM06Hooks.REMINDER_24H_DUE }
        assertEquals(1, hook24)
    }

    // --- Zona horaria ---

    @Test
    fun reminder_due_at_uses_clinic_timezone() = runTest {
        val f = bootstrap()
        actorId = MANAGER
        schedule.saveSettings(
            VeterinaryScheduleSettings(clinicId = f.clinicId, timezoneName = SANTIAGO)
        ).getOrThrow()
        // La solicitud usa la zona de la clínica (Santiago) para ubicar el slot 09:00.
        val santiago = ZoneId.of(SANTIAGO)
        val startsAt = f.bookingDate.atTime(LocalTime.of(9, 0)).atZone(santiago).toInstant()
        store.petAuthorizedActors.value = store.petAuthorizedActors.value + (PET_A to setOf(OWNER_A))
        actorId = OWNER_A
        val a = appointments.requestAppointment(
            RequestVeterinaryAppointmentInput(
                clinicId = f.clinicId,
                serviceId = f.serviceId,
                petId = PET_A,
                startsAt = startsAt
            )
        ).getOrThrow()
        actorId = MANAGER
        appointments.confirmAppointment(a.id).getOrThrow()
        val sched = appointments.observeReminderSchedule(a.id).first()!!
        val r24 = sched.reminders.first { it.type == VeterinaryReminderType.REMINDER_24H }
        assertEquals(SANTIAGO, r24.timezoneName)
        assertEquals(a.startsAt.minusSeconds(24L * 3600L), r24.dueAt)
    }

    // --- Payload sin PII ---

    @Test
    fun hooks_are_event_keys_without_request_note() = runTest {
        val f = bootstrap()
        requestAndConfirm(f, note = "telefono secreto 11-2222-3333")
        val hooks = store.m06Hooks.value
        assertTrue(hooks.contains(VeterinaryAppointmentM06Hooks.REMINDER_24H_DUE))
        assertTrue(hooks.contains(VeterinaryAppointmentM06Hooks.REMINDER_2H_DUE))
        // Los hooks son claves de evento (MAYÚSCULAS/_): nunca transportan texto libre ni la nota.
        assertTrue(hooks.none { it.contains("telefono secreto") })
        assertTrue(hooks.all { it.matches(Regex("[A-Z0-9_]+")) })
    }

    // --- Infraestructura M06 ---

    @Test
    fun infrastructure_available_flag_true_by_default() = runTest {
        val f = bootstrap()
        val id = requestAndConfirm(f)
        val sched = appointments.observeReminderSchedule(id).first()!!
        assertTrue(sched.infrastructureAvailable)
    }

    @Test
    fun infrastructure_unavailable_still_prepares_without_push() = runTest {
        val f = bootstrap()
        store.m06InfrastructureAvailable.value = false
        val id = requestAndConfirm(f)
        val sched = appointments.observeReminderSchedule(id).first()!!
        assertFalse(sched.infrastructureAvailable)
        // Aun sin infraestructura, quedan PREPARED y nunca se reclama push.
        assertTrue(sched.reminders.all { it.status == VeterinaryReminderDeliveryStatus.PREPARED })
        assertTrue(sched.reminders.none { it.pushClaimed })
    }

    // --- Disparo del recordatorio ---

    @Test
    fun fire_due_reminder_marks_fired_prepared_without_push() = runTest {
        val f = bootstrap()
        val id = requestAndConfirm(f)
        val appt = store.appointments.value.first { it.id == id }
        actorId = MANAGER
        val fired = appointments.fireDueReminder(
            id, VeterinaryReminderType.REMINDER_24H, now = appt.startsAt
        ).getOrThrow()
        assertEquals(VeterinaryReminderDeliveryStatus.FIRED_PREPARED, fired.status)
        assertFalse(fired.pushClaimed)
    }

    @Test
    fun fire_due_reminder_twice_is_already_scheduled() = runTest {
        val f = bootstrap()
        val id = requestAndConfirm(f)
        val appt = store.appointments.value.first { it.id == id }
        actorId = MANAGER
        appointments.fireDueReminder(id, VeterinaryReminderType.REMINDER_24H, now = appt.startsAt)
            .getOrThrow()
        val again = appointments.fireDueReminder(
            id, VeterinaryReminderType.REMINDER_24H, now = appt.startsAt
        )
        assertEquals("VETERINARY_REMINDER_ALREADY_SCHEDULED", code(again.exceptionOrNull()))
    }

    // --- Cancelación ---

    @Test
    fun cancel_reminders_fires_reminder_cancelled_hook() = runTest {
        val f = bootstrap()
        val id = requestAndConfirm(f)
        actorId = MANAGER
        val cancelled = appointments.cancelReminders(id).getOrThrow()
        assertTrue(
            cancelled.reminders.all { it.status == VeterinaryReminderDeliveryStatus.CANCELLED }
        )
        assertTrue(store.m06Hooks.value.contains(VeterinaryAppointmentM06Hooks.REMINDER_CANCELLED))
    }

    // --- Nunca se afirma push entregado ---

    @Test
    fun never_claims_push_delivered() = runTest {
        val f = bootstrap()
        val id = requestAndConfirm(f)
        val appt = store.appointments.value.first { it.id == id }
        actorId = MANAGER
        appointments.fireDueReminder(id, VeterinaryReminderType.REMINDER_24H, now = appt.startsAt)
            .getOrThrow()
        val sched = appointments.observeReminderSchedule(id).first()!!
        // Ningún recordatorio reclama push en ningún estado (PREPARED o FIRED_PREPARED).
        assertTrue(sched.reminders.none { it.pushClaimed })
    }
}
