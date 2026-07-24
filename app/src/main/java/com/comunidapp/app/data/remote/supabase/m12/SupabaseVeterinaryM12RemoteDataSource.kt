package com.comunidapp.app.data.remote.supabase.m12

import com.comunidapp.app.data.model.AnimalSpecies
import com.comunidapp.app.data.model.VeterinaryClinicProfile
import com.comunidapp.app.data.model.VeterinaryClinicStatus
import com.comunidapp.app.data.model.VeterinaryOpeningHours
import com.comunidapp.app.data.model.VeterinaryProfessional
import com.comunidapp.app.data.model.VeterinaryProfessionalStatus
import com.comunidapp.app.data.model.VeterinaryService
import com.comunidapp.app.data.model.VeterinaryServiceCategory
import com.comunidapp.app.data.model.VeterinarySpecialty
import com.comunidapp.app.data.model.VeterinaryVerificationStatus
import com.comunidapp.app.data.remote.supabase.supabase
import io.github.jan.supabase.postgrest.postgrest
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
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
}
