package com.comunidapp.app.viewmodel

import com.comunidapp.app.data.model.VeterinaryAppointment
import com.comunidapp.app.data.model.VeterinaryAppointmentAuditEvents
import com.comunidapp.app.data.model.VeterinaryAppointmentM06Hooks
import com.comunidapp.app.data.model.VeterinaryAppointmentStatus
import com.comunidapp.app.data.model.VeterinaryAvailabilityExceptionType
import com.comunidapp.app.data.model.VeterinaryClinicStatus
import com.comunidapp.app.data.model.VeterinaryOpeningHours
import com.comunidapp.app.data.model.VeterinaryScheduleSettings
import com.comunidapp.app.data.model.VeterinaryServiceCategory
import com.comunidapp.app.data.model.VeterinarySpecialty
import com.comunidapp.app.data.remote.supabase.m12.M12VeterinaryErrorMapper
import com.comunidapp.app.data.repository.CreateVeterinaryAvailabilityExceptionInput
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
import com.comunidapp.app.data.repository.appointmentHistory
import com.comunidapp.app.data.repository.appointments
import com.comunidapp.app.data.repository.clearBlock3
import com.comunidapp.app.data.repository.petAuthorizedActors
import com.comunidapp.app.data.repository.petOrgCustody
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * LeoVer M12 Bloque 3 — agenda, disponibilidad y turnos (solo fakes, sin Supabase).
 *
 * Cubre configuración de agenda, reglas/excepciones, cálculo de slots, cupo,
 * autoridad sobre la mascota, ciclo de vida del turno, privacidad, historial,
 * hooks M06 y auditoría M07.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class M12VeterinaryAppointmentsTest {

    private companion object {
        const val TZ = "America/Argentina/Buenos_Aires"
        const val MANAGER = "manager-vet-1"
        const val OWNER_A = "pet-owner-a"
        const val OWNER_B = "pet-owner-b"
        const val PET_A = "pet-a"
        const val PET_B = "pet-b"
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
        wire()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun wire() {
        clinics = MockVeterinaryClinicRepository({ actorId }, store)
        lifecycle = MockVeterinaryClinicLifecycle({ actorId }, store)
        pros = MockVeterinaryProfessionalOpsRepository({ actorId }, store)
        services = MockVeterinaryServiceRepository({ actorId }, store)
        hours = MockVeterinaryOpeningHoursRepository({ actorId }, store)
        schedule = MockVeterinaryScheduleRepository({ actorId }, store)
        appointments = MockVeterinaryAppointmentRepository({ actorId }, store)
    }

    private fun code(t: Throwable?) = M12VeterinaryErrorMapper.codeOf(t!!)

    private data class Fixture(
        val clinicId: String,
        val serviceId: String,
        val professionalId: String,
        val organizationId: String,
        val bookingDate: LocalDate
    )

    /** Crea una clínica ACTIVA con servicio, horarios, profesional vinculado, settings y una regla. */
    private suspend fun bootstrap(capacityPerSlot: Int = 1): Fixture {
        actorId = MANAGER
        val clinic = clinics.createLocalDraft(
            CreateVeterinaryClinicDraftInput(
                organizationId = "org-1",
                displayName = "Clínica Agenda",
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
        professionalId: String? = null
    ) = run {
        store.petAuthorizedActors.value =
            store.petAuthorizedActors.value + (pet to setOf(owner))
        actorId = owner
        appointments.requestAppointment(
            RequestVeterinaryAppointmentInput(
                clinicId = f.clinicId,
                serviceId = f.serviceId,
                petId = pet,
                startsAt = slotStart(f),
                professionalId = professionalId
            )
        )
    }

    /** Fuerza un turno al pasado para poder completar / marcar no-show. */
    private fun forceToPast(appointmentId: String) {
        val past = Instant.now().minusSeconds(7_200)
        store.appointments.value = store.appointments.value.map {
            if (it.id == appointmentId) {
                it.copy(startsAt = past, endsAt = past.plusSeconds(1_800))
            } else {
                it
            }
        }
    }

    // --- Configuración de agenda (1, 2, 3) ---

    @Test
    fun settings_valid_timezone_invalid_horizon_invalid() = runTest {
        val f = bootstrap()
        // 1: settings válida
        val ok = schedule.saveSettings(
            VeterinaryScheduleSettings(clinicId = f.clinicId, timezoneName = TZ, bookingHorizonDays = 30)
        )
        assertTrue(ok.isSuccess)
        // 2: timezone inválida
        val badTz = schedule.saveSettings(
            VeterinaryScheduleSettings(clinicId = f.clinicId, timezoneName = "Not/AZone")
        )
        assertEquals("VETERINARY_TIMEZONE_INVALID", code(badTz.exceptionOrNull()))
        // 3: horizonte inválido
        val badHorizon = schedule.saveSettings(
            VeterinaryScheduleSettings(clinicId = f.clinicId, bookingHorizonDays = 0)
        )
        assertEquals("VETERINARY_SCHEDULE_SETTINGS_INVALID", code(badHorizon.exceptionOrNull()))
    }

    // --- Reglas y excepciones (4, 5, 6, 7) ---

    @Test
    fun rule_valid_and_overlap_rejected() = runTest {
        val f = bootstrap()
        // 4: regla válida (otro día, para no chocar con la de bootstrap)
        val otherDay = f.bookingDate.dayOfWeek.plus(1)
        val valid = schedule.createRule(
            CreateVeterinaryAvailabilityRuleInput(
                clinicId = f.clinicId,
                dayOfWeek = otherDay,
                startsAt = LocalTime.of(9, 0),
                endsAt = LocalTime.of(12, 0),
                slotDurationMinutes = 30,
                capacityPerSlot = 2,
                serviceId = f.serviceId
            )
        )
        assertTrue(valid.isSuccess)
        // 5: solapamiento con la regla de bootstrap (mismo día/servicio/ventana)
        val overlap = schedule.createRule(
            CreateVeterinaryAvailabilityRuleInput(
                clinicId = f.clinicId,
                dayOfWeek = f.bookingDate.dayOfWeek,
                startsAt = LocalTime.of(10, 0),
                endsAt = LocalTime.of(12, 0),
                slotDurationMinutes = 30,
                capacityPerSlot = 1,
                serviceId = f.serviceId
            )
        )
        assertEquals("VETERINARY_AVAILABILITY_RULE_OVERLAP", code(overlap.exceptionOrNull()))
    }

    @Test
    fun exception_closed_and_custom_hours_shapes() = runTest {
        val f = bootstrap()
        // 6: CLOSED no admite horas
        val closed = schedule.createException(
            CreateVeterinaryAvailabilityExceptionInput(
                clinicId = f.clinicId,
                exceptionDate = f.bookingDate,
                type = VeterinaryAvailabilityExceptionType.CLOSED
            )
        )
        assertTrue(closed.isSuccess)
        val closedBad = schedule.createException(
            CreateVeterinaryAvailabilityExceptionInput(
                clinicId = f.clinicId,
                exceptionDate = f.bookingDate,
                type = VeterinaryAvailabilityExceptionType.CLOSED,
                startsAt = LocalTime.of(9, 0)
            )
        )
        assertEquals(
            "VETERINARY_AVAILABILITY_EXCEPTION_INVALID",
            code(closedBad.exceptionOrNull())
        )
        // 7: CUSTOM_HOURS requiere ambas horas coherentes
        val custom = schedule.createException(
            CreateVeterinaryAvailabilityExceptionInput(
                clinicId = f.clinicId,
                exceptionDate = f.bookingDate.plusDays(7),
                type = VeterinaryAvailabilityExceptionType.CUSTOM_HOURS,
                startsAt = LocalTime.of(10, 0),
                endsAt = LocalTime.of(12, 0)
            )
        )
        assertTrue(custom.isSuccess)
    }

    // --- Cálculo de slots (8, 9) ---

    @Test
    fun slot_calculation_and_closed_removes_slots() = runTest {
        val f = bootstrap()
        // 8: 09:00–18:00 con slots de 30' => 18 slots
        val slots = schedule.observeAvailableSlots(f.clinicId, f.serviceId, f.bookingDate, null).first()
        assertEquals(18, slots.size)
        assertEquals(slotStart(f), slots.first().startsAt)
        assertEquals(1, slots.first().capacity)
        assertEquals(0, slots.first().reserved)

        // 9: excepción CLOSED elimina los slots del día
        schedule.createException(
            CreateVeterinaryAvailabilityExceptionInput(
                clinicId = f.clinicId,
                exceptionDate = f.bookingDate,
                type = VeterinaryAvailabilityExceptionType.CLOSED
            )
        ).getOrThrow()
        val after = schedule.observeAvailableSlots(f.clinicId, f.serviceId, f.bookingDate, null).first()
        assertTrue(after.isEmpty())
    }

    @Test
    fun custom_hours_exception_narrows_slots() = runTest {
        val f = bootstrap()
        schedule.createException(
            CreateVeterinaryAvailabilityExceptionInput(
                clinicId = f.clinicId,
                exceptionDate = f.bookingDate,
                type = VeterinaryAvailabilityExceptionType.CUSTOM_HOURS,
                startsAt = LocalTime.of(10, 0),
                endsAt = LocalTime.of(12, 0)
            )
        ).getOrThrow()
        val slots = schedule.observeAvailableSlots(f.clinicId, f.serviceId, f.bookingDate, null).first()
        assertEquals(4, slots.size)
    }

    // --- Cupo (10, 11, 12, 13, 14) ---

    @Test
    fun capacity_one_consumed_by_requested_then_confirmed_then_freed() = runTest {
        val f = bootstrap(capacityPerSlot = 1)
        // 10 + 12: capacidad 1, REQUESTED consume cupo
        val appt = requestAppointment(f, OWNER_A, PET_A).getOrThrow()
        assertEquals(VeterinaryAppointmentStatus.REQUESTED, appt.status)
        var slot = schedule.observeAvailableSlots(f.clinicId, f.serviceId, f.bookingDate, null).first()
            .first { it.startsAt == slotStart(f) }
        assertEquals(0, slot.available)

        // 13: CONFIRMED sigue consumiendo cupo
        actorId = MANAGER
        appointments.confirmAppointment(appt.id).getOrThrow()
        slot = schedule.observeAvailableSlots(f.clinicId, f.serviceId, f.bookingDate, null).first()
            .first { it.startsAt == slotStart(f) }
        assertEquals(0, slot.available)

        // 14: cancelar libera cupo
        actorId = OWNER_A
        appointments.cancelMyAppointment(appt.id, "cambio de planes").getOrThrow()
        slot = schedule.observeAvailableSlots(f.clinicId, f.serviceId, f.bookingDate, null).first()
            .first { it.startsAt == slotStart(f) }
        assertEquals(1, slot.available)
    }

    @Test
    fun capacity_multiple_allows_two_requesters() = runTest {
        // 11: capacidad múltiple admite varias reservas
        val f = bootstrap(capacityPerSlot = 2)
        requestAppointment(f, OWNER_A, PET_A).getOrThrow()
        val second = requestAppointment(f, OWNER_B, PET_B).getOrThrow()
        assertEquals(VeterinaryAppointmentStatus.REQUESTED, second.status)
        val slot = schedule.observeAvailableSlots(f.clinicId, f.serviceId, f.bookingDate, null).first()
            .first { it.startsAt == slotStart(f) }
        assertEquals(0, slot.available)
    }

    // --- Concurrencia / overbooking (15) ---

    @Test
    fun concurrency_overbook_prevented() = runTest {
        val f = bootstrap(capacityPerSlot = 1)
        requestAppointment(f, OWNER_A, PET_A).getOrThrow()
        val overbook = requestAppointment(f, OWNER_B, PET_B)
        val c = code(overbook.exceptionOrNull())
        assertTrue(
            "esperado cupo agotado o slot no disponible, fue $c",
            c == "VETERINARY_SLOT_CAPACITY_EXHAUSTED" || c == "VETERINARY_SLOT_NOT_AVAILABLE"
        )
    }

    // --- Autoridad sobre la mascota (16, 17, 18) ---

    @Test
    fun own_pet_allowed_foreign_pet_rejected_org_custody_allowed() = runTest {
        val f = bootstrap()
        // 16: mascota propia permitida
        val own = requestAppointment(f, OWNER_A, PET_A)
        assertTrue(own.isSuccess)

        // 17: mascota ajena rechazada (sin autoridad sembrada)
        actorId = OWNER_B
        val foreign = appointments.requestAppointment(
            RequestVeterinaryAppointmentInput(
                clinicId = f.clinicId,
                serviceId = f.serviceId,
                petId = "pet-foreign",
                startsAt = f.bookingDate.atTime(LocalTime.of(9, 30)).atZone(zone).toInstant()
            )
        )
        assertEquals("VETERINARY_APPOINTMENT_PET_FORBIDDEN", code(foreign.exceptionOrNull()))

        // 18: custodia organizacional válida (org de la clínica + actor gestiona la org)
        store.petOrgCustody.value = mapOf("pet-org" to f.organizationId)
        actorId = MANAGER
        val custody = appointments.requestAppointment(
            RequestVeterinaryAppointmentInput(
                clinicId = f.clinicId,
                serviceId = f.serviceId,
                petId = "pet-org",
                startsAt = f.bookingDate.atTime(LocalTime.of(10, 0)).atZone(zone).toInstant()
            )
        )
        assertTrue(custody.isSuccess)
    }

    // --- Validaciones de la solicitud (19, 20, 21, 22, 23) ---

    @Test
    fun inactive_service_rejected() = runTest {
        val f = bootstrap()
        val service = store.services.value.first { it.id == f.serviceId }
        services.changeServiceActive(service.id, false).getOrThrow()
        val r = requestAppointment(f, OWNER_A, PET_A)
        assertEquals("VETERINARY_SERVICE_NOT_FOUND", code(r.exceptionOrNull()))
    }

    @Test
    fun unlinked_professional_rejected() = runTest {
        val f = bootstrap()
        val r = requestAppointment(f, OWNER_A, PET_A, professionalId = "pro-not-linked")
        assertEquals("VETERINARY_PROFESSIONAL_NOT_LINKED", code(r.exceptionOrNull()))
    }

    @Test
    fun past_slot_rejected() = runTest {
        val f = bootstrap()
        store.petAuthorizedActors.value = mapOf(PET_A to setOf(OWNER_A))
        actorId = OWNER_A
        val r = appointments.requestAppointment(
            RequestVeterinaryAppointmentInput(
                clinicId = f.clinicId,
                serviceId = f.serviceId,
                petId = PET_A,
                startsAt = Instant.now().minusSeconds(3_600)
            )
        )
        assertEquals("VETERINARY_APPOINTMENT_PAST_SLOT", code(r.exceptionOrNull()))
    }

    @Test
    fun successful_request_is_requested_and_double_submit_rejected() = runTest {
        // 22: solicitud exitosa => REQUESTED
        val f = bootstrap(capacityPerSlot = 2)
        val appt = requestAppointment(f, OWNER_A, PET_A).getOrThrow()
        assertEquals(VeterinaryAppointmentStatus.REQUESTED, appt.status)
        assertNotNull(appt.id)

        // 23: doble envío del mismo requester al mismo slot => rechazado
        val dup = requestAppointment(f, OWNER_A, PET_A)
        assertEquals("VETERINARY_SLOT_NOT_AVAILABLE", code(dup.exceptionOrNull()))
    }

    // --- Listados y privacidad (24, 25, 26) ---

    @Test
    fun list_my_appointments_and_privacy_and_managed() = runTest {
        val f = bootstrap(capacityPerSlot = 5)
        val apptA = requestAppointment(f, OWNER_A, PET_A).getOrThrow()
        requestAppointment(f, OWNER_B, PET_B).getOrThrow()

        // 24: mis turnos
        actorId = OWNER_A
        val mine = appointments.observeMyAppointments().first()
        assertEquals(1, mine.size)
        assertEquals(apptA.id, mine.first().id)

        // 25: privacidad entre usuarios (B no puede leer el turno de A)
        actorId = OWNER_B
        val forbidden = appointments.getAppointment(apptA.id)
        assertEquals("VETERINARY_APPOINTMENT_FORBIDDEN", code(forbidden.exceptionOrNull()))

        // 26: gestor autorizado ve los turnos gestionados
        actorId = MANAGER
        val managed = appointments.observeManagedAppointments(f.clinicId).first()
        assertEquals(2, managed.size)
    }

    // --- Ciclo de vida (27, 28, 29, 30, 31, 32, 33) ---

    @Test
    fun confirm_and_reject() = runTest {
        val f = bootstrap(capacityPerSlot = 5)
        val a = requestAppointment(f, OWNER_A, PET_A).getOrThrow()
        val b = requestAppointment(f, OWNER_B, PET_B).getOrThrow()
        actorId = MANAGER
        // 27: confirmar
        val confirmed = appointments.confirmAppointment(a.id).getOrThrow()
        assertEquals(VeterinaryAppointmentStatus.CONFIRMED, confirmed.status)
        // 28: rechazar
        val rejected = appointments.rejectAppointment(b.id, "sin disponibilidad").getOrThrow()
        assertEquals(VeterinaryAppointmentStatus.REJECTED, rejected.status)
    }

    @Test
    fun cancel_by_user_and_by_clinic() = runTest {
        val f = bootstrap(capacityPerSlot = 5)
        val a = requestAppointment(f, OWNER_A, PET_A).getOrThrow()
        val b = requestAppointment(f, OWNER_B, PET_B).getOrThrow()
        // 29: cancela el usuario
        actorId = OWNER_A
        val cancelledUser = appointments.cancelMyAppointment(a.id, null).getOrThrow()
        assertEquals(VeterinaryAppointmentStatus.CANCELLED_BY_USER, cancelledUser.status)
        // 30: cancela la clínica
        actorId = MANAGER
        val cancelledClinic = appointments.cancelManagedAppointment(b.id, "cierre imprevisto").getOrThrow()
        assertEquals(VeterinaryAppointmentStatus.CANCELLED_BY_CLINIC, cancelledClinic.status)
    }

    @Test
    fun complete_and_no_show_require_past_slot() = runTest {
        val f = bootstrap(capacityPerSlot = 5)
        val a = requestAppointment(f, OWNER_A, PET_A).getOrThrow()
        val b = requestAppointment(f, OWNER_B, PET_B).getOrThrow()
        actorId = MANAGER
        appointments.confirmAppointment(a.id).getOrThrow()
        appointments.confirmAppointment(b.id).getOrThrow()

        // 31: completar requiere turno no futuro
        forceToPast(a.id)
        val completed = appointments.completeAppointment(a.id).getOrThrow()
        assertEquals(VeterinaryAppointmentStatus.COMPLETED, completed.status)

        // 32: no-show requiere turno no futuro
        forceToPast(b.id)
        val noShow = appointments.markNoShow(b.id).getOrThrow()
        assertEquals(VeterinaryAppointmentStatus.NO_SHOW, noShow.status)
    }

    @Test
    fun invalid_transition_rejected() = runTest {
        // 33: transición inválida (confirmar dos veces)
        val f = bootstrap(capacityPerSlot = 5)
        val a = requestAppointment(f, OWNER_A, PET_A).getOrThrow()
        actorId = MANAGER
        appointments.confirmAppointment(a.id).getOrThrow()
        val again = appointments.confirmAppointment(a.id)
        assertEquals("VETERINARY_APPOINTMENT_INVALID_TRANSITION", code(again.exceptionOrNull()))
    }

    // --- Historial y notas privadas (34, 35) ---

    @Test
    fun history_present_after_transitions() = runTest {
        val f = bootstrap(capacityPerSlot = 5)
        val a = requestAppointment(f, OWNER_A, PET_A).getOrThrow()
        actorId = MANAGER
        appointments.confirmAppointment(a.id).getOrThrow()
        // 34: historial presente (REQUESTED + CONFIRMED)
        val history = appointments.observeAppointmentHistory(a.id).first()
        assertEquals(2, history.size)
        assertEquals(VeterinaryAppointmentStatus.REQUESTED, history.first().toStatus)
        assertEquals(VeterinaryAppointmentStatus.CONFIRMED, history.last().toStatus)
        assertTrue(store.appointmentHistory.value.any { it.appointmentId == a.id })
    }

    @Test
    fun private_note_not_exposed_to_other_users() = runTest {
        // 35: no hay listado público de turnos; la nota privada solo la ve
        // el solicitante o un gestor. Un tercero no puede leer el turno.
        val f = bootstrap(capacityPerSlot = 5)
        store.petAuthorizedActors.value = mapOf(PET_A to setOf(OWNER_A))
        actorId = OWNER_A
        val a = appointments.requestAppointment(
            RequestVeterinaryAppointmentInput(
                clinicId = f.clinicId,
                serviceId = f.serviceId,
                petId = PET_A,
                startsAt = slotStart(f),
                requestNote = "nota privada de contacto"
            )
        ).getOrThrow()
        assertEquals("nota privada de contacto", a.requestNote)

        actorId = OWNER_B
        val forbidden = appointments.getAppointment(a.id)
        assertEquals("VETERINARY_APPOINTMENT_FORBIDDEN", code(forbidden.exceptionOrNull()))
    }

    // --- Hooks M06 / auditoría M07 / fallo técnico (36, 37, 38) ---

    @Test
    fun m06_hook_and_m07_audit_recorded_after_request() = runTest {
        val f = bootstrap(capacityPerSlot = 5)
        requestAppointment(f, OWNER_A, PET_A).getOrThrow()
        // 36: hook M06 registrado
        assertTrue(store.m06Hooks.value.contains(VeterinaryAppointmentM06Hooks.REQUESTED))
        // 37: auditoría M07 registrada
        assertTrue(
            store.auditEvents.value.any { it.eventKey == VeterinaryAppointmentAuditEvents.REQUESTED }
        )
    }

    @Test
    fun force_failure_maps_to_repository_failure() = runTest {
        val f = bootstrap()
        store.forceFailure = true
        val r = appointments.getAppointment("whatever")
        assertEquals("VETERINARY_REPOSITORY_FAILURE", code(r.exceptionOrNull()))
        store.forceFailure = false
        assertFalse(this::class.java.name.contains("supabase", ignoreCase = true))
        assertNotNull(f.clinicId)
    }
}
