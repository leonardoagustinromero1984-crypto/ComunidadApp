package com.comunidapp.app.data.remote.supabase.m08

import com.comunidapp.app.data.remote.supabase.PetReminderDto
import com.comunidapp.app.data.remote.supabase.VaccinationRecordDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * LeoVer M08 — PostgREST / RPC DTOs (snake_case SerialNames).
 * owner_id is nullable when principal is organization-only (never empty string).
 */

@Serializable
data class PetM08Row(
    val id: String,
    @SerialName("owner_id") val ownerId: String? = null,
    val name: String,
    @SerialName("photo_url") val photoUrl: String? = null,
    val species: String,
    val sex: String,
    @SerialName("age_years") val ageYears: Int = 0,
    @SerialName("age_months") val ageMonths: Int = 0,
    val size: String,
    val description: String = "",
    val vaccinations: List<VaccinationRecordDto> = emptyList(),
    @SerialName("last_deworming") val lastDeworming: String? = null,
    @SerialName("deworming_product") val dewormingProduct: String? = null,
    @SerialName("last_flea_treatment") val lastFleaTreatment: String? = null,
    @SerialName("flea_treatment_product") val fleaTreatmentProduct: String? = null,
    val sterilized: String? = null,
    @SerialName("microchip_id") val microchipId: String? = null,
    @SerialName("last_vet_visit") val lastVetVisit: String? = null,
    @SerialName("health_notes") val healthNotes: String? = null,
    @SerialName("weight_kg") val weightKg: Float? = null,
    val color: String? = null,
    val breed: String? = null,
    val personality: String? = null,
    @SerialName("location_text") val locationText: String? = null,
    val reminders: List<PetReminderDto> = emptyList(),
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val status: String = "ACTIVE",
    @SerialName("deceased_at") val deceasedAt: String? = null,
    @SerialName("archived_at") val archivedAt: String? = null,
    @SerialName("microchip_normalized") val microchipNormalized: String? = null,
    @SerialName("avatar_file_asset_id") val avatarFileAssetId: String? = null
)

@Serializable
data class AccessiblePetM08Row(
    val id: String,
    @SerialName("owner_id") val ownerId: String? = null,
    val name: String,
    @SerialName("photo_url") val photoUrl: String? = null,
    val species: String,
    val sex: String,
    @SerialName("age_years") val ageYears: Int = 0,
    @SerialName("age_months") val ageMonths: Int = 0,
    val size: String,
    val description: String = "",
    val vaccinations: List<VaccinationRecordDto> = emptyList(),
    @SerialName("last_deworming") val lastDeworming: String? = null,
    @SerialName("deworming_product") val dewormingProduct: String? = null,
    @SerialName("last_flea_treatment") val lastFleaTreatment: String? = null,
    @SerialName("flea_treatment_product") val fleaTreatmentProduct: String? = null,
    val sterilized: String? = null,
    @SerialName("microchip_id") val microchipId: String? = null,
    @SerialName("last_vet_visit") val lastVetVisit: String? = null,
    @SerialName("health_notes") val healthNotes: String? = null,
    @SerialName("weight_kg") val weightKg: Float? = null,
    val color: String? = null,
    val breed: String? = null,
    val personality: String? = null,
    @SerialName("location_text") val locationText: String? = null,
    val reminders: List<PetReminderDto> = emptyList(),
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val status: String = "ACTIVE",
    @SerialName("deceased_at") val deceasedAt: String? = null,
    @SerialName("archived_at") val archivedAt: String? = null,
    @SerialName("microchip_normalized") val microchipNormalized: String? = null,
    @SerialName("avatar_file_asset_id") val avatarFileAssetId: String? = null,
    @SerialName("relation_code") val relationCode: String = "NONE",
    @SerialName("principal_person_id") val principalPersonId: String? = null,
    @SerialName("principal_organization_id") val principalOrganizationId: String? = null,
    val capabilities: List<String> = emptyList(),
    @SerialName("can_update") val canUpdate: Boolean = false,
    @SerialName("can_manage_health") val canManageHealth: Boolean = false,
    @SerialName("can_manage_media") val canManageMedia: Boolean = false,
    @SerialName("can_archive") val canArchive: Boolean = false,
    @SerialName("can_mark_deceased") val canMarkDeceased: Boolean = false
)

@Serializable
data class PetAccessContextRow(
    @SerialName("pet_id") val petId: String,
    @SerialName("relation_code") val relationCode: String = "NONE",
    @SerialName("principal_person_id") val principalPersonId: String? = null,
    @SerialName("principal_organization_id") val principalOrganizationId: String? = null,
    val capabilities: List<String> = emptyList(),
    @SerialName("can_read") val canRead: Boolean = false,
    @SerialName("can_update") val canUpdate: Boolean = false,
    @SerialName("can_manage_health") val canManageHealth: Boolean = false,
    @SerialName("can_manage_media") val canManageMedia: Boolean = false,
    @SerialName("can_manage_responsibilities") val canManageResponsibilities: Boolean = false,
    @SerialName("can_manage_authorizations") val canManageAuthorizations: Boolean = false,
    @SerialName("can_initiate_transfer") val canInitiateTransfer: Boolean = false,
    @SerialName("can_accept_transfer") val canAcceptTransfer: Boolean = false,
    @SerialName("can_cancel_transfer") val canCancelTransfer: Boolean = false,
    @SerialName("can_archive") val canArchive: Boolean = false,
    @SerialName("can_restore") val canRestore: Boolean = false,
    @SerialName("can_mark_deceased") val canMarkDeceased: Boolean = false,
    @SerialName("can_view_history") val canViewHistory: Boolean = false
)

@Serializable
data class CreatePetWithPrincipalParams(
    @SerialName("p_name") val name: String,
    @SerialName("p_species") val species: String,
    @SerialName("p_sex") val sex: String,
    @SerialName("p_size") val size: String,
    @SerialName("p_description") val description: String,
    @SerialName("p_organization_id") val organizationId: String? = null,
    @SerialName("p_microchip_id") val microchipId: String? = null
)

@Serializable
data class UpdatePetProfileParams(
    @SerialName("p_pet_id") val petId: String,
    @SerialName("p_name") val name: String,
    @SerialName("p_species") val species: String,
    @SerialName("p_breed") val breed: String? = null,
    @SerialName("p_sex") val sex: String,
    @SerialName("p_size") val size: String,
    @SerialName("p_description") val description: String,
    @SerialName("p_age_years") val ageYears: Int,
    @SerialName("p_age_months") val ageMonths: Int,
    @SerialName("p_color") val color: String? = null,
    @SerialName("p_microchip_id") val microchipId: String? = null
)

@Serializable
data class UpdatePetHealthParams(
    @SerialName("p_pet_id") val petId: String,
    @SerialName("p_vaccinations") val vaccinations: List<VaccinationRecordDto> = emptyList(),
    @SerialName("p_reminders") val reminders: List<PetReminderDto> = emptyList(),
    @SerialName("p_last_deworming") val lastDeworming: String? = null,
    @SerialName("p_deworming_product") val dewormingProduct: String? = null,
    @SerialName("p_last_flea_treatment") val lastFleaTreatment: String? = null,
    @SerialName("p_flea_treatment_product") val fleaTreatmentProduct: String? = null,
    @SerialName("p_sterilized") val sterilized: String? = null,
    @SerialName("p_last_vet_visit") val lastVetVisit: String? = null,
    @SerialName("p_health_notes") val healthNotes: String? = null,
    @SerialName("p_weight_kg") val weightKg: Float? = null
)

@Serializable
data class ArchivePetParams(
    @SerialName("p_pet_id") val petId: String,
    @SerialName("p_reason") val reason: String? = null
)

@Serializable
data class RestorePetParams(
    @SerialName("p_pet_id") val petId: String
)

@Serializable
data class MarkPetDeceasedParams(
    @SerialName("p_pet_id") val petId: String,
    @SerialName("p_reason") val reason: String? = null
)

@Serializable
data class SetPetAvatarAssetParams(
    @SerialName("p_pet_id") val petId: String,
    @SerialName("p_asset_id") val assetId: String?
)

@Serializable
data class DetectPetDuplicateParams(
    @SerialName("p_microchip") val microchip: String? = null,
    @SerialName("p_name") val name: String? = null
)

@Serializable
data class PetDuplicateCandidateRow(
    @SerialName("pet_id") val petId: String,
    @SerialName("match_reason") val matchReason: String
)

@Serializable
data class AssignPetResponsibilityParams(
    @SerialName("p_pet_id") val petId: String,
    @SerialName("p_role_code") val roleCode: String,
    @SerialName("p_person_id") val personId: String? = null,
    @SerialName("p_organization_id") val organizationId: String? = null,
    @SerialName("p_ends_at") val endsAt: String? = null,
    @SerialName("p_reason") val reason: String? = null
)

@Serializable
data class RevokePetResponsibilityParams(
    @SerialName("p_responsibility_id") val responsibilityId: String
)

@Serializable
data class GrantPetAuthorizationParams(
    @SerialName("p_pet_id") val petId: String,
    @SerialName("p_person_id") val personId: String,
    @SerialName("p_capabilities") val capabilities: List<String>,
    @SerialName("p_valid_until") val validUntil: String? = null
)

@Serializable
data class RevokePetAuthorizationParams(
    @SerialName("p_authorization_id") val authorizationId: String
)

@Serializable
data class InitiatePetTransferParams(
    @SerialName("p_pet_id") val petId: String,
    @SerialName("p_to_person_id") val toPersonId: String? = null,
    @SerialName("p_to_organization_id") val toOrganizationId: String? = null,
    @SerialName("p_expires_at") val expiresAt: String? = null
)

@Serializable
data class AcceptPetTransferParams(
    @SerialName("p_transfer_id") val transferId: String
)

@Serializable
data class RejectPetTransferParams(
    @SerialName("p_transfer_id") val transferId: String
)

@Serializable
data class CancelPetTransferParams(
    @SerialName("p_transfer_id") val transferId: String,
    @SerialName("p_reason") val reason: String? = null
)

@Serializable
data class PetResponsibilityM08Row(
    val id: String,
    @SerialName("pet_id") val petId: String,
    @SerialName("role_code") val roleCode: String,
    @SerialName("person_id") val personId: String? = null,
    @SerialName("organization_id") val organizationId: String? = null,
    val status: String = "ACTIVE",
    @SerialName("starts_at") val startsAt: String? = null,
    @SerialName("ends_at") val endsAt: String? = null,
    @SerialName("revoked_at") val revokedAt: String? = null,
    @SerialName("revoked_by") val revokedBy: String? = null,
    @SerialName("created_by") val createdBy: String,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("accepted_at") val acceptedAt: String? = null,
    val reason: String? = null
)

@Serializable
data class PetAuthorizationM08Row(
    val id: String,
    @SerialName("pet_id") val petId: String,
    @SerialName("person_id") val personId: String,
    @SerialName("granted_by") val grantedBy: String,
    val capabilities: List<String> = emptyList(),
    val status: String = "ACTIVE",
    @SerialName("valid_from") val validFrom: String? = null,
    @SerialName("valid_until") val validUntil: String? = null,
    @SerialName("revoked_at") val revokedAt: String? = null,
    @SerialName("revoked_by") val revokedBy: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("accepted_at") val acceptedAt: String? = null
)

@Serializable
data class PetTransferM08Row(
    val id: String,
    @SerialName("pet_id") val petId: String,
    @SerialName("from_person_id") val fromPersonId: String? = null,
    @SerialName("from_organization_id") val fromOrganizationId: String? = null,
    @SerialName("to_person_id") val toPersonId: String? = null,
    @SerialName("to_organization_id") val toOrganizationId: String? = null,
    val status: String = "PENDING",
    @SerialName("requested_by") val requestedBy: String,
    @SerialName("requested_at") val requestedAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("responded_at") val respondedAt: String? = null,
    @SerialName("responded_by") val respondedBy: String? = null,
    @SerialName("cancelled_at") val cancelledAt: String? = null,
    @SerialName("cancellation_reason") val cancellationReason: String? = null,
    @SerialName("correlation_id") val correlationId: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class PetStatusHistoryM08Row(
    val id: String? = null,
    @SerialName("pet_id") val petId: String,
    @SerialName("previous_status") val previousStatus: String? = null,
    @SerialName("new_status") val newStatus: String,
    @SerialName("reason_code") val reasonCode: String? = null,
    @SerialName("actor_user_id") val actorUserId: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("correlation_id") val correlationId: String? = null
)
