package com.comunidapp.app.data.remote.supabase.m12

import com.comunidapp.app.data.model.AnimalSpecies
import com.comunidapp.app.data.model.VeterinaryAppointment
import com.comunidapp.app.data.model.VeterinaryAppointmentSlot
import com.comunidapp.app.data.model.VeterinaryAppointmentStatus
import com.comunidapp.app.data.model.VeterinaryAppointmentStatusHistory
import com.comunidapp.app.data.model.VeterinaryAvailabilityException
import com.comunidapp.app.data.model.VeterinaryAvailabilityExceptionType
import com.comunidapp.app.data.model.VeterinaryAvailabilityRule
import com.comunidapp.app.data.model.VeterinaryClinicProfile
import com.comunidapp.app.data.model.VeterinaryClinicStatus
import com.comunidapp.app.data.model.VeterinaryOpeningHours
import com.comunidapp.app.data.model.VeterinaryProfessional
import com.comunidapp.app.data.model.VeterinaryProfessionalStatus
import com.comunidapp.app.data.model.VeterinaryScheduleSettings
import com.comunidapp.app.data.model.VeterinaryService
import com.comunidapp.app.data.model.VeterinaryServiceCategory
import com.comunidapp.app.data.model.VeterinarySpecialty
import com.comunidapp.app.data.model.VeterinaryVerificationStatus
import com.comunidapp.app.data.remote.supabase.supabase
import io.github.jan.supabase.postgrest.postgrest
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * LeoVer M12 — DTOs y remote datasource (RPC only, sin DML directo).
 */

@Serializable
data class VeterinaryClinicRow(
    val id: String,
    @SerialName("organization_id") val organizationId: String,
    @SerialName("branch_id") val branchId: String? = null,
    @SerialName("display_name") val displayName: String,
    val description: String? = null,
    val status: String = "DRAFT",
    @SerialName("verification_status") val verificationStatus: String = "UNVERIFIED",
    @SerialName("public_zone_text") val publicZoneText: String = "",
    @SerialName("public_address_text") val publicAddressText: String? = null,
    @SerialName("public_contact_enabled") val publicContactEnabled: Boolean = false,
    @SerialName("public_phone") val publicPhone: String? = null,
    @SerialName("public_email") val publicEmail: String? = null,
    @SerialName("website_url") val websiteUrl: String? = null,
    @SerialName("social_links") val socialLinks: Map<String, String> = emptyMap(),
    @SerialName("logo_asset_ref") val logoAssetRef: String? = null,
    @SerialName("cover_asset_ref") val coverAssetRef: String? = null,
    @SerialName("offers_emergency_care") val offersEmergencyCare: Boolean = false,
    @SerialName("is_open_24_hours") val isOpen24Hours: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("archived_at") val archivedAt: String? = null
)

@Serializable
data class VeterinaryProfessionalRow(
    val id: String,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("display_name") val displayName: String,
    @SerialName("license_number") val licenseNumber: String? = null,
    @SerialName("license_jurisdiction") val licenseJurisdiction: String? = null,
    @SerialName("verification_status") val verificationStatus: String = "UNVERIFIED",
    val biography: String? = null,
    @SerialName("public_contact_enabled") val publicContactEnabled: Boolean = false,
    @SerialName("avatar_asset_ref") val avatarAssetRef: String? = null,
    val status: String = "ACTIVE",
    @SerialName("public_phone") val publicPhone: String? = null,
    @SerialName("public_email") val publicEmail: String? = null,
    @SerialName("clinic_id") val clinicId: String? = null,
    val specialties: List<String> = emptyList()
)

@Serializable
data class VeterinaryServiceRow(
    val id: String,
    @SerialName("clinic_id") val clinicId: String,
    val name: String,
    val category: String,
    val description: String? = null,
    val species: List<String> = emptyList(),
    val active: Boolean = true,
    @SerialName("requires_appointment") val requiresAppointment: Boolean = true,
    @SerialName("emergency_available") val emergencyAvailable: Boolean = false
)

@Serializable
data class VeterinaryOpeningHoursRow(
    val id: String? = null,
    @SerialName("clinic_id") val clinicId: String,
    @SerialName("day_of_week") val dayOfWeek: Int,
    val closed: Boolean = false,
    @SerialName("opens_at") val opensAt: String? = null,
    @SerialName("closes_at") val closesAt: String? = null,
    @SerialName("emergency_only") val emergencyOnly: Boolean = false
)

private fun parseInstant(value: String?): Instant =
    value?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.EPOCH

private fun parseTime(value: String?): LocalTime? {
    if (value.isNullOrBlank()) return null
    val cleaned = value.take(8)
    return runCatching { LocalTime.parse(cleaned) }.getOrNull()
        ?: runCatching { LocalTime.parse(value.take(5)) }.getOrNull()
}

fun VeterinaryClinicRow.toDomain(): VeterinaryClinicProfile = VeterinaryClinicProfile(
    id = id,
    organizationId = organizationId,
    branchId = branchId,
    displayName = displayName,
    description = description,
    status = VeterinaryClinicStatus.fromString(status),
    verificationStatus = VeterinaryVerificationStatus.fromString(verificationStatus),
    publicZoneText = publicZoneText,
    publicAddressText = publicAddressText,
    publicContactEnabled = publicContactEnabled,
    publicPhone = publicPhone,
    publicEmail = publicEmail,
    websiteUrl = websiteUrl,
    socialLinks = socialLinks,
    logoAssetRef = logoAssetRef,
    coverAssetRef = coverAssetRef,
    offersEmergencyCare = offersEmergencyCare,
    isOpen24Hours = isOpen24Hours,
    createdAt = parseInstant(createdAt),
    updatedAt = parseInstant(updatedAt),
    archivedAt = archivedAt?.let { parseInstant(it) }
)

fun VeterinaryProfessionalRow.toDomain(): VeterinaryProfessional = VeterinaryProfessional(
    id = id,
    userId = userId,
    clinicId = clinicId,
    displayName = displayName,
    licenseNumber = licenseNumber,
    licenseJurisdiction = licenseJurisdiction,
    verificationStatus = VeterinaryVerificationStatus.fromString(verificationStatus),
    biography = biography,
    specialties = specialties.map { VeterinarySpecialty.fromString(it) }.toSet(),
    publicContactEnabled = publicContactEnabled,
    publicPhone = publicPhone,
    publicEmail = publicEmail,
    avatarAssetRef = avatarAssetRef,
    status = VeterinaryProfessionalStatus.fromString(status)
)

fun VeterinaryServiceRow.toDomain(): VeterinaryService = VeterinaryService(
    id = id,
    clinicId = clinicId,
    name = name,
    category = VeterinaryServiceCategory.fromString(category),
    description = description,
    species = species.map { AnimalSpecies.fromString(it) }.toSet(),
    active = active,
    requiresAppointment = requiresAppointment,
    emergencyAvailable = emergencyAvailable
)

fun VeterinaryOpeningHoursRow.toDomain(): VeterinaryOpeningHours = VeterinaryOpeningHours(
    clinicId = clinicId,
    dayOfWeek = DayOfWeek.of(dayOfWeek.coerceIn(1, 7)),
    closed = closed,
    opensAt = parseTime(opensAt),
    closesAt = parseTime(closesAt),
    emergencyOnly = emergencyOnly
)

// ---------------------------------------------------------------------------
// M12 Bloque 3 — agenda, disponibilidad y turnos (DTOs RPC + mappers).
// ---------------------------------------------------------------------------

private fun parseTimestamp(value: String?): Instant {
    if (value.isNullOrBlank()) return Instant.EPOCH
    return runCatching { Instant.parse(value) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(value).toInstant(ZoneOffset.UTC) }.getOrNull()
        ?: Instant.EPOCH
}

private fun parseLocalDate(value: String?): LocalDate? {
    if (value.isNullOrBlank()) return null
    return runCatching { LocalDate.parse(value.take(10)) }.getOrNull()
}

@Serializable
data class VeterinaryScheduleSettingsRow(
    @SerialName("clinic_id") val clinicId: String,
    @SerialName("timezone_name") val timezoneName: String = "America/Argentina/Buenos_Aires",
    @SerialName("booking_horizon_days") val bookingHorizonDays: Int = 30,
    @SerialName("minimum_notice_minutes") val minimumNoticeMinutes: Int = 60,
    @SerialName("cancellation_notice_minutes") val cancellationNoticeMinutes: Int = 120,
    @SerialName("default_slot_duration_minutes") val defaultSlotDurationMinutes: Int = 30,
    val active: Boolean = true
)

@Serializable
data class VeterinaryAvailabilityRuleRow(
    val id: String,
    @SerialName("clinic_id") val clinicId: String,
    @SerialName("professional_id") val professionalId: String? = null,
    @SerialName("service_id") val serviceId: String? = null,
    // ISO day_of_week (1=Monday ... 7=Sunday).
    @SerialName("day_of_week") val dayOfWeek: Int,
    @SerialName("starts_at") val startsAt: String,
    @SerialName("ends_at") val endsAt: String,
    @SerialName("slot_duration_minutes") val slotDurationMinutes: Int,
    @SerialName("capacity_per_slot") val capacityPerSlot: Int,
    @SerialName("valid_from") val validFrom: String? = null,
    @SerialName("valid_until") val validUntil: String? = null,
    val active: Boolean = true
)

@Serializable
data class VeterinaryAvailabilityExceptionRow(
    val id: String,
    @SerialName("clinic_id") val clinicId: String,
    @SerialName("rule_id") val ruleId: String? = null,
    @SerialName("exception_date") val exceptionDate: String,
    val type: String,
    @SerialName("starts_at") val startsAt: String? = null,
    @SerialName("ends_at") val endsAt: String? = null,
    @SerialName("capacity_per_slot") val capacityPerSlot: Int? = null,
    val reason: String? = null,
    val active: Boolean = true
)

@Serializable
data class VeterinaryAppointmentSlotRow(
    @SerialName("clinic_id") val clinicId: String,
    @SerialName("professional_id") val professionalId: String? = null,
    @SerialName("service_id") val serviceId: String,
    @SerialName("starts_at") val startsAt: String,
    @SerialName("ends_at") val endsAt: String,
    val capacity: Int,
    val reserved: Int,
    val available: Int
)

@Serializable
data class VeterinaryAppointmentRow(
    val id: String,
    @SerialName("clinic_id") val clinicId: String,
    @SerialName("professional_id") val professionalId: String? = null,
    @SerialName("service_id") val serviceId: String,
    @SerialName("pet_id") val petId: String,
    @SerialName("requester_user_id") val requesterUserId: String,
    @SerialName("starts_at") val startsAt: String,
    @SerialName("ends_at") val endsAt: String,
    val status: String = "REQUESTED",
    @SerialName("request_note") val requestNote: String? = null,
    @SerialName("clinic_operational_note") val clinicOperationalNote: String? = null,
    @SerialName("rejection_reason") val rejectionReason: String? = null,
    @SerialName("cancellation_reason") val cancellationReason: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class VeterinaryAppointmentStatusHistoryRow(
    val id: String,
    @SerialName("appointment_id") val appointmentId: String,
    @SerialName("from_status") val fromStatus: String? = null,
    @SerialName("to_status") val toStatus: String,
    @SerialName("changed_by") val changedBy: String,
    val reason: String? = null,
    @SerialName("changed_at") val changedAt: String? = null
)

@Serializable
data class ManagedVeterinaryAvailabilityRow(
    @SerialName("clinic_id") val clinicId: String? = null,
    val rules: List<VeterinaryAvailabilityRuleRow> = emptyList(),
    val exceptions: List<VeterinaryAvailabilityExceptionRow> = emptyList()
)

fun VeterinaryScheduleSettingsRow.toDomain(): VeterinaryScheduleSettings = VeterinaryScheduleSettings(
    clinicId = clinicId,
    timezoneName = timezoneName,
    bookingHorizonDays = bookingHorizonDays,
    minimumNoticeMinutes = minimumNoticeMinutes,
    cancellationNoticeMinutes = cancellationNoticeMinutes,
    defaultSlotDurationMinutes = defaultSlotDurationMinutes,
    active = active
)

fun VeterinaryAvailabilityRuleRow.toDomain(): VeterinaryAvailabilityRule = VeterinaryAvailabilityRule(
    id = id,
    clinicId = clinicId,
    professionalId = professionalId,
    serviceId = serviceId,
    dayOfWeek = DayOfWeek.of(dayOfWeek.coerceIn(1, 7)),
    startsAt = parseTime(startsAt) ?: LocalTime.MIN,
    endsAt = parseTime(endsAt) ?: LocalTime.MIN,
    slotDurationMinutes = slotDurationMinutes,
    capacityPerSlot = capacityPerSlot,
    validFrom = parseLocalDate(validFrom),
    validUntil = parseLocalDate(validUntil),
    active = active
)

fun VeterinaryAvailabilityExceptionRow.toDomain(): VeterinaryAvailabilityException =
    VeterinaryAvailabilityException(
        id = id,
        clinicId = clinicId,
        ruleId = ruleId,
        exceptionDate = parseLocalDate(exceptionDate) ?: LocalDate.now(),
        type = VeterinaryAvailabilityExceptionType.fromString(type),
        startsAt = parseTime(startsAt),
        endsAt = parseTime(endsAt),
        capacityPerSlot = capacityPerSlot,
        reason = reason,
        active = active
    )

fun VeterinaryAppointmentSlotRow.toDomain(): VeterinaryAppointmentSlot = VeterinaryAppointmentSlot(
    clinicId = clinicId,
    professionalId = professionalId,
    serviceId = serviceId,
    startsAt = parseTimestamp(startsAt),
    endsAt = parseTimestamp(endsAt),
    capacity = capacity,
    reserved = reserved,
    available = available
)

fun VeterinaryAppointmentRow.toDomain(): VeterinaryAppointment = VeterinaryAppointment(
    id = id,
    clinicId = clinicId,
    professionalId = professionalId,
    serviceId = serviceId,
    petId = petId,
    requesterUserId = requesterUserId,
    startsAt = parseTimestamp(startsAt),
    endsAt = parseTimestamp(endsAt),
    status = VeterinaryAppointmentStatus.fromString(status),
    requestNote = requestNote,
    clinicOperationalNote = clinicOperationalNote,
    rejectionReason = rejectionReason,
    cancellationReason = cancellationReason,
    createdAt = parseTimestamp(createdAt),
    updatedAt = parseTimestamp(updatedAt)
)

fun VeterinaryAppointmentStatusHistoryRow.toDomain(): VeterinaryAppointmentStatusHistory =
    VeterinaryAppointmentStatusHistory(
        id = id,
        appointmentId = appointmentId,
        fromStatus = fromStatus?.let { VeterinaryAppointmentStatus.fromString(it) },
        toStatus = VeterinaryAppointmentStatus.fromString(toStatus),
        changedBy = changedBy,
        reason = reason,
        changedAt = parseTimestamp(changedAt)
    )

class SupabaseVeterinaryM12RemoteDataSource {
    private suspend inline fun <reified T : Any> rpc(
        name: String,
        params: JsonObject = buildJsonObject { }
    ): T = supabase.postgrest.rpc(function = name, parameters = params).decodeSingle()

    private suspend inline fun <reified T : Any> rpcList(
        name: String,
        params: JsonObject = buildJsonObject { }
    ): List<T> = supabase.postgrest.rpc(function = name, parameters = params).decodeList()

    suspend fun createClinicDraft(params: JsonObject): VeterinaryClinicRow =
        rpc("m12_create_veterinary_clinic_draft", params)

    suspend fun updateClinicProfile(params: JsonObject): VeterinaryClinicRow =
        rpc("m12_update_veterinary_clinic_profile", params)

    suspend fun changeClinicStatus(clinicId: String, status: String): VeterinaryClinicRow =
        rpc(
            "m12_change_veterinary_clinic_status",
            buildJsonObject {
                put("p_clinic_id", clinicId)
                put("p_status", status)
            }
        )

    suspend fun requestClinicVerification(clinicId: String): VeterinaryClinicRow =
        rpc(
            "m12_request_veterinary_clinic_verification",
            buildJsonObject { put("p_clinic_id", clinicId) }
        )

    suspend fun reviewClinicVerification(clinicId: String, decision: String): VeterinaryClinicRow =
        rpc(
            "m12_review_veterinary_clinic_verification",
            buildJsonObject {
                put("p_clinic_id", clinicId)
                put("p_decision", decision)
            }
        )

    suspend fun getPublicClinic(clinicId: String): VeterinaryClinicRow =
        rpc(
            "m12_get_public_veterinary_clinic",
            buildJsonObject { put("p_clinic_id", clinicId) }
        )

    suspend fun getManagedClinic(clinicId: String): VeterinaryClinicRow =
        rpc(
            "m12_get_managed_veterinary_clinic",
            buildJsonObject { put("p_clinic_id", clinicId) }
        )

    suspend fun listPublicClinics(params: JsonObject): List<VeterinaryClinicRow> =
        rpcList("m12_list_public_veterinary_clinics", params)

    suspend fun listManagedClinics(): List<VeterinaryClinicRow> =
        rpcList("m12_list_managed_veterinary_clinics")

    suspend fun createProfessional(params: JsonObject): VeterinaryProfessionalRow =
        rpc("m12_create_veterinary_professional", params)

    suspend fun updateProfessional(params: JsonObject): VeterinaryProfessionalRow =
        rpc("m12_update_veterinary_professional", params)

    suspend fun linkProfessional(params: JsonObject): JsonObject =
        rpc("m12_link_veterinary_professional", params)

    suspend fun unlinkProfessional(params: JsonObject): JsonObject =
        rpc("m12_unlink_veterinary_professional", params)

    suspend fun replaceSpecialties(params: JsonObject): VeterinaryProfessionalRow =
        rpc("m12_replace_veterinary_professional_specialties", params)

    suspend fun listPublicProfessionals(clinicId: String): List<VeterinaryProfessionalRow> =
        rpcList(
            "m12_list_public_veterinary_professionals",
            buildJsonObject { put("p_clinic_id", clinicId) }
        )

    suspend fun listManagedProfessionals(clinicId: String): List<VeterinaryProfessionalRow> =
        rpcList(
            "m12_list_managed_veterinary_professionals",
            buildJsonObject { put("p_clinic_id", clinicId) }
        )

    suspend fun createService(params: JsonObject): VeterinaryServiceRow =
        rpc("m12_create_veterinary_service", params)

    suspend fun updateService(params: JsonObject): VeterinaryServiceRow =
        rpc("m12_update_veterinary_service", params)

    suspend fun changeServiceStatus(params: JsonObject): VeterinaryServiceRow =
        rpc("m12_change_veterinary_service_status", params)

    suspend fun listPublicServices(clinicId: String): List<VeterinaryServiceRow> =
        rpcList(
            "m12_list_public_veterinary_services",
            buildJsonObject { put("p_clinic_id", clinicId) }
        )

    suspend fun listManagedServices(clinicId: String): List<VeterinaryServiceRow> =
        rpcList(
            "m12_list_managed_veterinary_services",
            buildJsonObject { put("p_clinic_id", clinicId) }
        )

    suspend fun replaceOpeningHours(params: JsonObject): List<VeterinaryOpeningHoursRow> =
        rpcList("m12_replace_veterinary_opening_hours", params)

    suspend fun listPublicHours(clinicId: String): List<VeterinaryOpeningHoursRow> =
        rpcList(
            "m12_list_public_veterinary_opening_hours",
            buildJsonObject { put("p_clinic_id", clinicId) }
        )

    suspend fun listManagedHours(clinicId: String): List<VeterinaryOpeningHoursRow> =
        rpcList(
            "m12_list_managed_veterinary_opening_hours",
            buildJsonObject { put("p_clinic_id", clinicId) }
        )

    // --- M12 Bloque 3: configuración de agenda y disponibilidad ---

    suspend fun upsertScheduleSettings(params: JsonObject): VeterinaryScheduleSettingsRow =
        rpc("m12_upsert_veterinary_schedule_settings", params)

    suspend fun getScheduleSettings(clinicId: String): List<VeterinaryScheduleSettingsRow> =
        rpcList(
            "m12_get_veterinary_schedule_settings",
            buildJsonObject { put("p_clinic_id", clinicId) }
        )

    suspend fun createAvailabilityRule(params: JsonObject): VeterinaryAvailabilityRuleRow =
        rpc("m12_create_veterinary_availability_rule", params)

    suspend fun updateAvailabilityRule(params: JsonObject): VeterinaryAvailabilityRuleRow =
        rpc("m12_update_veterinary_availability_rule", params)

    suspend fun changeAvailabilityRuleStatus(
        ruleId: String,
        active: Boolean
    ): VeterinaryAvailabilityRuleRow =
        rpc(
            "m12_change_veterinary_availability_rule_status",
            buildJsonObject {
                put("p_rule_id", ruleId)
                put("p_active", active)
            }
        )

    suspend fun createAvailabilityException(params: JsonObject): VeterinaryAvailabilityExceptionRow =
        rpc("m12_create_veterinary_availability_exception", params)

    suspend fun updateAvailabilityException(params: JsonObject): VeterinaryAvailabilityExceptionRow =
        rpc("m12_update_veterinary_availability_exception", params)

    suspend fun changeAvailabilityExceptionStatus(
        exceptionId: String,
        active: Boolean
    ): VeterinaryAvailabilityExceptionRow =
        rpc(
            "m12_change_veterinary_availability_exception_status",
            buildJsonObject {
                put("p_exception_id", exceptionId)
                put("p_active", active)
            }
        )

    suspend fun listManagedAvailability(clinicId: String): ManagedVeterinaryAvailabilityRow =
        rpc(
            "m12_list_managed_veterinary_availability",
            buildJsonObject { put("p_clinic_id", clinicId) }
        )

    // Orden de parámetros según 047: p_clinic_id, p_service_id, p_date, p_professional_id default null.
    suspend fun listAvailableSlots(
        clinicId: String,
        serviceId: String,
        date: String,
        professionalId: String? = null
    ): List<VeterinaryAppointmentSlotRow> =
        rpcList(
            "m12_list_available_veterinary_appointment_slots",
            buildJsonObject {
                put("p_clinic_id", clinicId)
                put("p_service_id", serviceId)
                put("p_date", date)
                put("p_professional_id", professionalId)
            }
        )

    // --- M12 Bloque 3: solicitudes y ciclo de vida de turnos ---

    // Orden de parámetros según 047: p_clinic_id, p_service_id, p_pet_id, p_starts_at, p_ends_at,
    // p_professional_id default null, p_request_note default null.
    suspend fun requestAppointment(params: JsonObject): VeterinaryAppointmentRow =
        rpc("m12_request_veterinary_appointment", params)

    suspend fun getAppointment(appointmentId: String): VeterinaryAppointmentRow =
        rpc(
            "m12_get_veterinary_appointment",
            buildJsonObject { put("p_appointment_id", appointmentId) }
        )

    suspend fun listMyAppointments(): List<VeterinaryAppointmentRow> =
        rpcList("m12_list_my_veterinary_appointments")

    suspend fun listManagedAppointments(clinicId: String): List<VeterinaryAppointmentRow> =
        rpcList(
            "m12_list_managed_veterinary_appointments",
            buildJsonObject { put("p_clinic_id", clinicId) }
        )

    suspend fun confirmAppointment(appointmentId: String): VeterinaryAppointmentRow =
        rpc(
            "m12_confirm_veterinary_appointment",
            buildJsonObject { put("p_appointment_id", appointmentId) }
        )

    suspend fun rejectAppointment(appointmentId: String, reason: String?): VeterinaryAppointmentRow =
        rpc(
            "m12_reject_veterinary_appointment",
            buildJsonObject {
                put("p_appointment_id", appointmentId)
                put("p_reason", reason)
            }
        )

    suspend fun cancelMyAppointment(appointmentId: String, reason: String?): VeterinaryAppointmentRow =
        rpc(
            "m12_cancel_my_veterinary_appointment",
            buildJsonObject {
                put("p_appointment_id", appointmentId)
                put("p_reason", reason)
            }
        )

    suspend fun cancelManagedAppointment(
        appointmentId: String,
        reason: String?
    ): VeterinaryAppointmentRow =
        rpc(
            "m12_cancel_managed_veterinary_appointment",
            buildJsonObject {
                put("p_appointment_id", appointmentId)
                put("p_reason", reason)
            }
        )

    suspend fun completeAppointment(appointmentId: String): VeterinaryAppointmentRow =
        rpc(
            "m12_complete_veterinary_appointment",
            buildJsonObject { put("p_appointment_id", appointmentId) }
        )

    suspend fun markNoShow(appointmentId: String): VeterinaryAppointmentRow =
        rpc(
            "m12_mark_veterinary_appointment_no_show",
            buildJsonObject { put("p_appointment_id", appointmentId) }
        )

    suspend fun expireAppointment(appointmentId: String): VeterinaryAppointmentRow =
        rpc(
            "m12_expire_veterinary_appointment",
            buildJsonObject { put("p_appointment_id", appointmentId) }
        )

    suspend fun listAppointmentHistory(
        appointmentId: String
    ): List<VeterinaryAppointmentStatusHistoryRow> =
        rpcList(
            "m12_list_veterinary_appointment_history",
            buildJsonObject { put("p_appointment_id", appointmentId) }
        )
}
