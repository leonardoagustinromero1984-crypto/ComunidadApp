package com.comunidapp.app.viewmodel

import com.comunidapp.app.data.model.VeterinaryAppointmentStatus
import com.comunidapp.app.data.model.VeterinaryAvailabilityExceptionType
import com.comunidapp.app.data.remote.supabase.m12.M12VeterinaryErrorMapper
import com.comunidapp.app.data.remote.supabase.m12.ManagedVeterinaryAvailabilityRow
import com.comunidapp.app.data.remote.supabase.m12.VeterinaryAppointmentRow
import com.comunidapp.app.data.remote.supabase.m12.VeterinaryAppointmentSlotRow
import com.comunidapp.app.data.remote.supabase.m12.VeterinaryAppointmentStatusHistoryRow
import com.comunidapp.app.data.remote.supabase.m12.VeterinaryAvailabilityExceptionRow
import com.comunidapp.app.data.remote.supabase.m12.VeterinaryAvailabilityRuleRow
import com.comunidapp.app.data.remote.supabase.m12.VeterinaryScheduleSettingsRow
import com.comunidapp.app.data.remote.supabase.m12.toDomain
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.serialization.descriptors.SerialDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LeoVer M12 Bloque 3 — mapeo snake_case / dominio de DTOs de agenda y turnos (sin Supabase real).
 */
class M12VeterinaryAppointmentMappingTest {

    private fun serialNames(descriptor: SerialDescriptor): List<String> =
        (0 until descriptor.elementsCount).map { descriptor.getElementName(it) }

    @Test
    fun b3_dtos_use_snake_case_serial_names() {
        val settings = serialNames(VeterinaryScheduleSettingsRow.serializer().descriptor)
        listOf(
            "clinic_id", "timezone_name", "booking_horizon_days", "minimum_notice_minutes",
            "cancellation_notice_minutes", "default_slot_duration_minutes"
        ).forEach { assertTrue("settings falta $it", settings.contains(it)) }

        val rule = serialNames(VeterinaryAvailabilityRuleRow.serializer().descriptor)
        listOf(
            "clinic_id", "professional_id", "service_id", "day_of_week", "starts_at", "ends_at",
            "slot_duration_minutes", "capacity_per_slot", "valid_from", "valid_until"
        ).forEach { assertTrue("rule falta $it", rule.contains(it)) }

        val exception = serialNames(VeterinaryAvailabilityExceptionRow.serializer().descriptor)
        listOf("clinic_id", "rule_id", "exception_date", "starts_at", "ends_at", "capacity_per_slot")
            .forEach { assertTrue("exception falta $it", exception.contains(it)) }

        val appointment = serialNames(VeterinaryAppointmentRow.serializer().descriptor)
        listOf(
            "clinic_id", "professional_id", "service_id", "pet_id", "requester_user_id",
            "starts_at", "ends_at", "request_note", "clinic_operational_note",
            "rejection_reason", "cancellation_reason", "created_at", "updated_at"
        ).forEach { assertTrue("appointment falta $it", appointment.contains(it)) }

        val slot = serialNames(VeterinaryAppointmentSlotRow.serializer().descriptor)
        listOf("clinic_id", "professional_id", "service_id", "starts_at", "ends_at")
            .forEach { assertTrue("slot falta $it", slot.contains(it)) }

        val history = serialNames(VeterinaryAppointmentStatusHistoryRow.serializer().descriptor)
        listOf("appointment_id", "from_status", "to_status", "changed_by", "changed_at")
            .forEach { assertTrue("history falta $it", history.contains(it)) }

        // No camelCase filtrado en los nombres serializados.
        (settings + rule + exception + appointment + slot + history).forEach {
            assertFalse("nombre no debe ser camelCase: $it", it.any { ch -> ch.isUpperCase() })
        }
    }

    @Test
    fun unknown_enum_values_are_tolerated() {
        assertEquals(VeterinaryAppointmentStatus.UNKNOWN, VeterinaryAppointmentStatus.fromString("WEIRD"))
        assertEquals(VeterinaryAppointmentStatus.UNKNOWN, VeterinaryAppointmentStatus.fromString(null))
        assertEquals(VeterinaryAppointmentStatus.REQUESTED, VeterinaryAppointmentStatus.fromString("requested"))
        assertEquals(
            VeterinaryAvailabilityExceptionType.UNKNOWN,
            VeterinaryAvailabilityExceptionType.fromString("WEIRD")
        )
        assertEquals(
            VeterinaryAvailabilityExceptionType.CLOSED,
            VeterinaryAvailabilityExceptionType.fromString("closed")
        )
    }

    @Test
    fun typed_error_codes_from_rpc_messages() {
        assertEquals(
            "VETERINARY_SLOT_CAPACITY_EXHAUSTED",
            M12VeterinaryErrorMapper.codeOf(Exception("ERROR: ...VETERINARY_SLOT_CAPACITY_EXHAUSTED..."))
        )
        assertEquals(
            "VETERINARY_SLOT_NOT_AVAILABLE",
            M12VeterinaryErrorMapper.codeOf(Exception("VETERINARY_SLOT_NOT_AVAILABLE at slot"))
        )
        assertEquals(
            "VETERINARY_APPOINTMENT_PET_FORBIDDEN",
            M12VeterinaryErrorMapper.codeOf(Exception("raise VETERINARY_APPOINTMENT_PET_FORBIDDEN"))
        )
        assertEquals(
            "VETERINARY_APPOINTMENT_INVALID_TRANSITION",
            M12VeterinaryErrorMapper.codeOf(Exception("VETERINARY_APPOINTMENT_INVALID_TRANSITION"))
        )
        assertEquals(
            "VETERINARY_TIMEZONE_INVALID",
            M12VeterinaryErrorMapper.codeOf(Exception("VETERINARY_TIMEZONE_INVALID"))
        )
    }

    @Test
    fun user_messages_for_new_codes_are_specific() {
        val generic = M12VeterinaryErrorMapper.userMessage("SOMETHING_UNMAPPED")
        listOf(
            "VETERINARY_SCHEDULE_SETTINGS_INVALID",
            "VETERINARY_TIMEZONE_INVALID",
            "VETERINARY_AVAILABILITY_RULE_INVALID",
            "VETERINARY_AVAILABILITY_RULE_OVERLAP",
            "VETERINARY_AVAILABILITY_EXCEPTION_INVALID",
            "VETERINARY_SLOT_NOT_AVAILABLE",
            "VETERINARY_SLOT_CAPACITY_EXHAUSTED",
            "VETERINARY_APPOINTMENT_NOT_FOUND",
            "VETERINARY_APPOINTMENT_FORBIDDEN",
            "VETERINARY_APPOINTMENT_PET_FORBIDDEN",
            "VETERINARY_APPOINTMENT_INVALID_TRANSITION",
            "VETERINARY_APPOINTMENT_PAST_SLOT",
            "VETERINARY_APPOINTMENT_CANCELLATION_WINDOW",
            "VETERINARY_APPOINTMENT_ALREADY_FINAL"
        ).forEach {
            val msg = M12VeterinaryErrorMapper.userMessage(it)
            assertNotEquals("mensaje genérico para $it", generic, msg)
            assertFalse(msg.contains("postgrest", ignoreCase = true))
        }
    }

    @Test
    fun to_domain_smoke_without_network() {
        val settings = VeterinaryScheduleSettingsRow(
            clinicId = "c1",
            timezoneName = "America/Argentina/Buenos_Aires",
            bookingHorizonDays = 15,
            minimumNoticeMinutes = 45,
            cancellationNoticeMinutes = 90,
            defaultSlotDurationMinutes = 20
        ).toDomain()
        assertEquals("c1", settings.clinicId)
        assertEquals(15, settings.bookingHorizonDays)
        assertEquals(20, settings.defaultSlotDurationMinutes)

        val rule = VeterinaryAvailabilityRuleRow(
            id = "r1",
            clinicId = "c1",
            professionalId = "p1",
            serviceId = "s1",
            dayOfWeek = 1,
            startsAt = "09:00:00",
            endsAt = "18:00",
            slotDurationMinutes = 30,
            capacityPerSlot = 3,
            validFrom = "2026-01-01",
            validUntil = "2026-12-31"
        ).toDomain()
        assertEquals(DayOfWeek.MONDAY, rule.dayOfWeek)
        assertEquals(LocalTime.of(9, 0), rule.startsAt)
        assertEquals(LocalTime.of(18, 0), rule.endsAt)
        assertEquals(3, rule.capacityPerSlot)
        assertEquals(LocalDate.of(2026, 1, 1), rule.validFrom)

        val exceptionClosed = VeterinaryAvailabilityExceptionRow(
            id = "e1",
            clinicId = "c1",
            exceptionDate = "2026-05-01",
            type = "CLOSED"
        ).toDomain()
        assertEquals(VeterinaryAvailabilityExceptionType.CLOSED, exceptionClosed.type)
        assertEquals(LocalDate.of(2026, 5, 1), exceptionClosed.exceptionDate)

        val exceptionUnknown = VeterinaryAvailabilityExceptionRow(
            id = "e2",
            clinicId = "c1",
            exceptionDate = "2026-05-02",
            type = "WEIRD"
        ).toDomain()
        assertEquals(VeterinaryAvailabilityExceptionType.UNKNOWN, exceptionUnknown.type)

        val appointment = VeterinaryAppointmentRow(
            id = "a1",
            clinicId = "c1",
            professionalId = null,
            serviceId = "s1",
            petId = "pet1",
            requesterUserId = "u1",
            startsAt = "2026-06-01T12:00:00Z",
            endsAt = "2026-06-01T12:30:00Z",
            status = "CONFIRMED",
            requestNote = "nota privada"
        ).toDomain()
        assertEquals(VeterinaryAppointmentStatus.CONFIRMED, appointment.status)
        assertEquals("nota privada", appointment.requestNote)

        val weird = VeterinaryAppointmentRow(
            id = "a2",
            clinicId = "c1",
            serviceId = "s1",
            petId = "pet1",
            requesterUserId = "u1",
            startsAt = "2026-06-01T12:00:00Z",
            endsAt = "2026-06-01T12:30:00Z",
            status = "WEIRD"
        ).toDomain()
        assertEquals(VeterinaryAppointmentStatus.UNKNOWN, weird.status)

        val slot = VeterinaryAppointmentSlotRow(
            clinicId = "c1",
            professionalId = null,
            serviceId = "s1",
            startsAt = "2026-06-01T12:00:00Z",
            endsAt = "2026-06-01T12:30:00Z",
            capacity = 2,
            reserved = 1,
            available = 1
        ).toDomain()
        assertEquals(2, slot.capacity)
        assertEquals(1, slot.available)

        val history = VeterinaryAppointmentStatusHistoryRow(
            id = "h1",
            appointmentId = "a1",
            fromStatus = "REQUESTED",
            toStatus = "CONFIRMED",
            changedBy = "u1",
            changedAt = "2026-06-01T11:00:00Z"
        ).toDomain()
        assertEquals(VeterinaryAppointmentStatus.REQUESTED, history.fromStatus)
        assertEquals(VeterinaryAppointmentStatus.CONFIRMED, history.toStatus)

        val managed = ManagedVeterinaryAvailabilityRow(
            clinicId = "c1",
            rules = listOf(
                VeterinaryAvailabilityRuleRow(
                    id = "r1",
                    clinicId = "c1",
                    dayOfWeek = 2,
                    startsAt = "10:00",
                    endsAt = "12:00",
                    slotDurationMinutes = 30,
                    capacityPerSlot = 1
                )
            )
        )
        assertEquals(1, managed.rules.size)
        assertEquals(DayOfWeek.TUESDAY, managed.rules.first().toDomain().dayOfWeek)
    }
}
